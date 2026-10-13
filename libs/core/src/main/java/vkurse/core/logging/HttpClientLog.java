/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.logging;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Logs every call a client makes, in one line written when the answer came:
 *
 * <pre>{@code
 * yookassa POST https://api.yookassa.ru/v3/payments 200 312ms request={…} response={…}
 * }</pre>
 *
 * A 5xx answer, and a call that got no answer, is a WARN. Headers are never logged: they hold the credentials.
 * It reads the response, so the client's request factory must buffer it (see {@link HttpClients}).
 */
class HttpClientLog implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(HttpClientLog.class);
    private static final Logger bodies = LoggerFactory.getLogger(HttpLogFilter.BODY_LOGGER);

    private final String name;
    private final LogMasker masker;

    HttpClientLog(String name, LogMasker masker) {
        this.name = name;
        this.masker = masker;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        var call = new StringBuilder()
                .append(name)
                .append(' ')
                .append(request.getMethod())
                .append(' ')
                .append(masker.url(request.getURI().toString()));
        var sent = bodies.isInfoEnabled()
                ? masker.body(request.getHeaders().getContentType(), body, body.length, true)
                : "";
        var startedAt = System.nanoTime();
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException | RuntimeException e) {
            // The exception's own message may name the URL with a secret in it, so only the cause's is logged.
            log.warn(
                    "{} failed after {}ms, {}{}",
                    call,
                    millisSince(startedAt),
                    masker.url(String.valueOf(rootCause(e))),
                    labeled("request=", sent));
            throw e;
        }
        var status = response.getStatusCode().value();
        var line = call.append(' ')
                .append(status)
                .append(' ')
                .append(millisSince(startedAt))
                .append("ms");
        line.append(labeled("request=", sent));
        if (bodies.isInfoEnabled()) {
            line.append(labeled("response=", received(response)));
        }
        // A 4xx is often an answer the caller expects (no such payment, the bucket exists): the caller says if it
        // isn't.
        if (status >= 500) {
            log.warn(line.toString());
        } else {
            log.info(line.toString());
        }
        return response;
    }

    private String received(ClientHttpResponse response) {
        try {
            var content = response.getBody().readAllBytes();
            return masker.body(response.getHeaders().getContentType(), content, content.length, false);
        } catch (IOException e) {
            // An answer without a body, for some statuses.
            return "";
        }
    }

    /** An indented body starts on a line of its own. */
    private static String labeled(String label, String body) {
        if (body.isEmpty()) {
            return "";
        }
        return (body.indexOf('\n') < 0 ? " " : "\n") + label + body;
    }

    private static long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static Throwable rootCause(Throwable e) {
        var cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
