/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of {@link Telemetry}. With {@code enabled} off, nothing else is needed and nothing is sent.
 *
 * @param enabled whether this node sends telemetry to Kafka; off by default, so a developer needs no Kafka
 * @param kafka where to send it; required when {@code enabled}
 * @param slowMillis a request that took this long is kept whole, as a sample
 * @param sampleRate the share (0 to 1) of the other requests kept whole, so every route has examples
 * @param queueSize events waiting to be sent; when it is full, new ones are dropped, never waited for
 * @param nodeInterval how often the node reports its CPU, memory and pool
 */
@Validated
@ConfigurationProperties("vkurse.telemetry")
public record TelemetryProperties(
        @DefaultValue("false") boolean enabled,
        @Valid @Nullable Kafka kafka,
        @DefaultValue("500") @Min(1) long slowMillis,

        @DefaultValue("0.01") @DecimalMin("0") @DecimalMax("1")
        double sampleRate,

        @DefaultValue("10000") @Min(100) int queueSize,
        @DefaultValue("15s") @NotNull Duration nodeInterval) {

    /** @param bootstrapServers Kafka's {@code host:port} list; MSK's in production */
    public record Kafka(@Nullable String bootstrapServers) {}

    @AssertTrue(message = "vkurse.telemetry.kafka.bootstrap-servers is required when telemetry is enabled")
    boolean isBootstrapServersSet() {
        return !enabled
                || (kafka != null
                        && kafka.bootstrapServers() != null
                        && !kafka.bootstrapServers().isBlank());
    }
}
