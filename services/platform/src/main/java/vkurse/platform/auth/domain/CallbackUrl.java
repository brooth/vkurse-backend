/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.domain;

import jakarta.persistence.AttributeConverter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.BadRequestException;

/**
 * The frontend URL an OAuth sign-in returns to. It exists only if it is an absolute http(s) URL on an allowed web
 * origin, so the sign-in can't be used to redirect a browser anywhere else.
 */
public record CallbackUrl(String value) {

    /** Checks a URL the client asked for. */
    public static CallbackUrl of(@Nullable String callback, Collection<URI> allowedOrigins) {
        if (callback == null || callback.isBlank()) {
            throw new BadRequestException("The `callback` query parameter is required");
        }
        try {
            var uri = new URI(callback);
            var allowed = uri.isAbsolute()
                    && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && allowedOrigins.stream().map(CallbackUrl::origin).anyMatch(origin(uri)::equals);
            if (allowed) {
                return new CallbackUrl(callback);
            }
        } catch (URISyntaxException e) {
            // rejected below
        }
        throw new BadRequestException("The `callback` must be a URL on an allowed web origin");
    }

    /** {@code scheme://host[:port]} of a URI, the form browsers use in the {@code Origin} header. */
    public static String origin(URI uri) {
        var port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + port;
    }

    @Override
    public String toString() {
        return value;
    }

    /** Stores a {@link CallbackUrl} as its text. A stored one was checked when the sign-in started. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<CallbackUrl, String> {
        @Override
        public @Nullable String convertToDatabaseColumn(@Nullable CallbackUrl callback) {
            return callback == null ? null : callback.value();
        }

        @Override
        public @Nullable CallbackUrl convertToEntityAttribute(@Nullable String value) {
            return value == null ? null : new CallbackUrl(value);
        }
    }
}
