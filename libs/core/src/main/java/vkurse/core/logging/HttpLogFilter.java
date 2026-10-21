/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import vkurse.core.telemetry.Telemetry;

/**
 * Logs every request this service answers, in one line written when the answer is ready:
 *
 * <pre>{@code
 * POST /auth/otp 200 41ms - 127.0.0.1 @anonymous request={"email":"a@b.ru"} response={"token":"***","expiresAt":"…"}
 * }</pre>
 *
 * The level follows the status: INFO, WARN for a 4xx, ERROR for a 5xx. The bodies are masked and cut by
 * {@link LogMasker}, and left out when the logger {@value #BODY_LOGGER} is below INFO.
 *
 * <p>It also names the request: {@code requestId} is in the MDC for every line logged while the request is handled,
 * and in the {@value #REQUEST_ID_HEADER} response header. A caller may send its own in that header.
 *
 * <p>Each request is also counted by {@link Telemetry}, under its route.
 *
 * <p>It runs before the security filters, so a rejected request is logged too. The response is copied as it is
 * written, never held back, so a stream (the chat's server-sent events) is not delayed; its line is written when the
 * stream opens.
 */
public class HttpLogFilter extends OncePerRequestFilter implements Ordered {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String BODY_LOGGER = "vkurse.http.body";
    public static final String UNMATCHED = "UNMATCHED";

    static final String REQUEST_ID = "requestId";
    static final String USER_ID = "userId";

    /** The request attribute {@link UserLogFilter} leaves the caller's id in. */
    static final String USER_ID_ATTRIBUTE = HttpLogFilter.class.getName() + ".userId";

    private static final Logger log = LoggerFactory.getLogger(HttpLogFilter.class);
    private static final Logger bodies = LoggerFactory.getLogger(BODY_LOGGER);

    private static final Pattern SANE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String SILENT_PREFIX = "/actuator";

    private final LogMasker masker;
    private final Telemetry telemetry;

    public HttpLogFilter(LogMasker masker, Telemetry telemetry) {
        this.masker = masker;
        this.telemetry = telemetry;
    }

    @Override
    public int getOrder() {
        // Before Spring Security's filter chain (-100).
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || request.getRequestURI().startsWith(SILENT_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var requestId = requestId(request);
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        var cachedRequest = new ContentCachingRequestWrapper(request, masker.maxBodyBytes());
        var copiedResponse = new CopiedResponse(response, masker.maxBodyBytes());
        var startedAt = System.nanoTime();
        var failed = false;
        try {
            chain.doFilter(cachedRequest, copiedResponse);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            try {
                write(cachedRequest, copiedResponse, failed, (System.nanoTime() - startedAt) / 1_000_000);
            } finally {
                MDC.remove(USER_ID);
                MDC.remove(REQUEST_ID);
            }
        }
    }

    private void write(ContentCachingRequestWrapper request, CopiedResponse response, boolean failed, long millis) {
        // An exception that got this far is answered by the container, after this line is written.
        var status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
        var userId = request.getAttribute(USER_ID_ATTRIBUTE);
        if (userId != null) {
            MDC.put(USER_ID, userId.toString());
        }
        var query = request.getQueryString();
        var line = new StringBuilder()
                .append(request.getMethod())
                .append(' ')
                .append(masker.url(query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query))
                .append(' ')
                .append(status)
                .append(' ')
                .append(millis)
                .append("ms - ")
                .append(request.getRemoteAddr())
                .append(" @")
                .append(userId == null ? "anonymous" : userId);
        if (request.isAsyncStarted()) {
            line.append(" (stream)");
        }
        if (bodies.isInfoEnabled()) {
            append(
                    line,
                    "request=",
                    masker.body(
                            mediaType(request.getContentType()),
                            request.getContentAsByteArray(),
                            request.getContentLengthLong(),
                            true));
            append(
                    line,
                    "response=",
                    masker.body(mediaType(response.getContentType()), response.copied(), response.size(), false));
        }
        telemetry.request(
                MDC.get(REQUEST_ID),
                request.getMethod(),
                route(request),
                request.getRequestURI(),
                status,
                millis,
                userId == null ? null : userId.toString());
        if (status >= 500) {
            log.error(line.toString());
        } else if (status >= 400) {
            log.warn(line.toString());
        } else {
            log.info(line.toString());
        }
    }

    /**
     * The controller's path template, so there are as many routes as endpoints. A request no controller answered (the
     * resource handler's {@code /**}, or a rejection before any handler) is {@value #UNMATCHED}, so a scan can't make
     * routes up.
     */
    private static String route(HttpServletRequest request) {
        var pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null || pattern.toString().equals("/**") ? UNMATCHED : pattern.toString();
    }

    private static void append(StringBuilder line, String label, String body) {
        if (!body.isEmpty()) {
            // An indented body starts on a line of its own.
            line.append(body.indexOf('\n') < 0 ? ' ' : '\n').append(label).append(body);
        }
    }

    private static String requestId(HttpServletRequest request) {
        var sent = request.getHeader(REQUEST_ID_HEADER);
        if (sent != null && SANE_REQUEST_ID.matcher(sent).matches()) {
            return sent;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static @Nullable MediaType mediaType(@Nullable String contentType) {
        try {
            return contentType == null ? null : MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return null;
        }
    }

    /** A response that keeps a copy of the beginning of what is written to it. Nothing is held back. */
    private static final class CopiedResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream copy = new ByteArrayOutputStream();
        private final int limit;
        private long size;
        private @Nullable ServletOutputStream stream;
        private @Nullable PrintWriter writer;

        CopiedResponse(HttpServletResponse response, int limit) {
            super(response);
            this.limit = limit;
        }

        byte[] copied() {
            return copy.toByteArray();
        }

        long size() {
            return size;
        }

        private void keep(byte[] bytes, int offset, int length) {
            size += length;
            var room = limit - copy.size();
            if (room > 0) {
                copy.write(bytes, offset, Math.min(room, length));
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (stream == null) {
                var target = super.getOutputStream();
                stream = new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        target.write(b);
                        keep(new byte[] {(byte) b}, 0, 1);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        target.write(bytes, offset, length);
                        keep(bytes, offset, length);
                    }

                    @Override
                    public void flush() throws IOException {
                        target.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        target.close();
                    }

                    @Override
                    public boolean isReady() {
                        return target.isReady();
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                        target.setWriteListener(listener);
                    }
                };
            }
            return stream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                var target = super.getWriter();
                writer = new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] chars, int offset, int length) {
                        target.write(chars, offset, length);
                        // The copy is read back as UTF-8 unless the content type names another charset.
                        var bytes = new String(chars, offset, length).getBytes(StandardCharsets.UTF_8);
                        keep(bytes, 0, bytes.length);
                    }

                    @Override
                    public void flush() {
                        target.flush();
                    }

                    @Override
                    public void close() {
                        target.close();
                    }
                });
            }
            return writer;
        }
    }
}
