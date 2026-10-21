/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One run of a scheduled job on this node.
 *
 * @param job {@code Class.method}, as the log names it
 * @param error the exception's class when the run failed
 */
public record JobRun(
        UUID id,
        Instant at,
        Source source,
        String job,
        long millis,
        @Nullable String error) implements TelemetryEvent {}
