/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.monitoring;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the monitoring service that must be present at startup. The ones every service has are in
 * {@code CoreProperties}.
 *
 * @param platformUrl public base URL of platform: the issuer of the access tokens, and where its keys are published
 *     ({@code /.well-known/jwks.json})
 */
@Validated
@ConfigurationProperties("vkurse")
public record MonitoringProperties(@NotNull URI platformUrl) {}
