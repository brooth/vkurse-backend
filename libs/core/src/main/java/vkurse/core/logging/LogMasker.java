/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.logging;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes a payload safe to log: the values of secret fields become {@code ***}, and a long text is cut. It works on the
 * text, not on parsed JSON, so a body that is cut short or isn't valid JSON is masked all the same.
 */
public class LogMasker {
    private static final String MASK = "***";
    private static final Pattern BOT_TOKEN = Pattern.compile("/bot[^/\\s]+/");
    private static final Pattern LINE_BREAK = Pattern.compile("\\s*\\R\\s*");

    // Numbers are written back as they came: 10.00 stays 10.00.
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final int maxLength;
    private final boolean pretty;
    private final Fields always;
    private final Fields inRequests;

    public LogMasker(LogProperties properties) {
        this.maxLength = properties.maxBodyLength();
        this.pretty = properties.prettyBodies();
        this.always = new Fields(properties.maskedFields());
        var sent = new ArrayList<>(properties.maskedFields());
        sent.addAll(properties.maskedRequestFields());
        this.inRequests = new Fields(sent);
    }

    /** How many bytes of a body are worth keeping for {@link #request} and {@link #response}. */
    public int maxBodyBytes() {
        // A character of Russian text takes two bytes.
        return maxLength * 4;
    }

    /** A request body or a query string, ours or one we send. */
    public String request(String text) {
        return shorten(inRequests.mask(text));
    }

    public String response(String text) {
        return shorten(always.mask(text));
    }

    /** A URL with its query. Telegram's has the bot token in the path. */
    public String url(String url) {
        return inRequests.mask(BOT_TOKEN.matcher(url).replaceAll("/bot" + MASK + "/"));
    }

    /**
     * A body as bytes: text is masked, anything else is only named ({@code <image/png, 48211 bytes>}).
     *
     * @param content the body, or its beginning
     * @param length the size of the whole body, when it is known
     */
    public String body(@Nullable MediaType type, byte[] content, long length, boolean request) {
        if (content.length == 0) {
            return "";
        }
        if (type != null && !isText(type)) {
            return "<" + type.getType() + "/" + type.getSubtype() + ", " + Math.max(length, content.length) + " bytes>";
        }
        Charset charset = type == null || type.getCharset() == null ? StandardCharsets.UTF_8 : type.getCharset();
        var text = new String(content, charset);
        return request ? request(text) : response(text);
    }

    private static boolean isText(MediaType type) {
        var subtype = type.getSubtype();
        return "text".equals(type.getType())
                || subtype.equals("json")
                || subtype.endsWith("+json")
                || subtype.equals("x-www-form-urlencoded")
                || subtype.equals("xml");
    }

    private String shorten(String text) {
        var line = indented(LINE_BREAK.matcher(text.strip()).replaceAll(" "));
        return line.length() <= maxLength
                ? line
                : line.substring(0, maxLength) + "…(+" + (line.length() - maxLength) + " chars)";
    }

    /** JSON on several lines, when that is asked for and the text is whole JSON; otherwise the text as it is. */
    private String indented(String line) {
        if (!pretty || !(line.startsWith("{") || line.startsWith("["))) {
            return line;
        }
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(line));
        } catch (JacksonException e) {
            // Cut short, or not JSON after all.
            return line;
        }
    }

    /** The patterns that find a field by name in JSON ({@code "name": value}) and in a query or form ({@code name=value}). */
    private static final class Fields {
        private final @Nullable Pattern json;
        private final @Nullable Pattern form;

        Fields(List<String> names) {
            if (names.isEmpty()) {
                this.json = null;
                this.form = null;
                return;
            }
            var any = names.stream().map(Pattern::quote).collect(Collectors.joining("|"));
            // The value: a string, closed or cut short, or anything up to the next comma or bracket.
            this.json = Pattern.compile(
                    "(\"(?:" + any + ")\"\\s*:\\s*)(?:\"(?:[^\"\\\\]|\\\\.)*\"?|[^\\s,}\\]]+)",
                    Pattern.CASE_INSENSITIVE);
            this.form = Pattern.compile("((?:^|[?&])(?:" + any + ")=)[^&\\s\"]*", Pattern.CASE_INSENSITIVE);
        }

        String mask(String text) {
            if (json == null || form == null) {
                return text;
            }
            var masked = json.matcher(text).replaceAll("$1\"" + MASK + "\"");
            return form.matcher(masked).replaceAll("$1" + MASK);
        }
    }
}
