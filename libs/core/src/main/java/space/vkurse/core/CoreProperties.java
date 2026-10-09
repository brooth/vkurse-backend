/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings every service must have at startup. A missing or blank value stops the service with a binding error that
 * names the property.
 *
 * @param nodeId node identifier, used in logs and alerts to tell instances apart
 * @param database database settings
 */
@Validated
@ConfigurationProperties("vkurse")
public record CoreProperties(
        @NotBlank String nodeId, @Valid @NotNull Database database) {

    /** @param schema Postgres schema owned by the service (Flyway, Hibernate, ShedLock, Modulith events) */
    public record Database(@NotBlank String schema) {}
}
