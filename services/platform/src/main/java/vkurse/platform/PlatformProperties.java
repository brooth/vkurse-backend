/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the platform service that must be present at startup. The ones every service has are in
 * {@code CoreProperties}.
 *
 * @param publicUrl public base URL of this service: the issuer of our tokens and the base of OAuth redirect URIs
 * @param webUrl base URL of the web app: links in our emails lead there
 */
@Validated
@ConfigurationProperties("vkurse")
public record PlatformProperties(
        @NotNull URI publicUrl, @NotNull URI webUrl) {}
