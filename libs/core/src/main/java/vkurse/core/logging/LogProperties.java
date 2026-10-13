/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.logging;

import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Logging settings ({@code vkurse.logging.*}). Every one has a default, so a service sets none of them.
 *
 * @param maxBodyLength how many characters of a request or response body one log line holds; the rest is cut
 * @param maskedFields names of JSON fields, form fields and query parameters whose value is logged as {@code ***}
 * @param maskedRequestFields names masked only in what is sent (a request body, a query string): {@code code} is a
 *     secret in a sign-in request and an error code in a response
 * @param prettyBodies whether a JSON body is logged indented, on lines of its own. For reading by eye in
 *     development; production keeps one line per event
 */
@Validated
@ConfigurationProperties("vkurse.logging")
public record LogProperties(
        @DefaultValue("2000") @Min(100) int maxBodyLength,

        @DefaultValue({
            "password",
            "secret",
            "secretKey",
            "secret_key",
            "token",
            "accessToken",
            "access_token",
            "refreshToken",
            "refresh_token",
            "idToken",
            "id_token",
            "clientSecret",
            "client_secret",
            "authorization",
            "apikey"
        })
        List<String> maskedFields,

        @DefaultValue("code") List<String> maskedRequestFields,

        @DefaultValue("false") boolean prettyBodies) {}
