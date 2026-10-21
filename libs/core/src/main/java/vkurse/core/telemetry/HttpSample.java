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
 * One request kept whole, to look at an example: an error, a slow one, or one picked at random. The query string is
 * left out of {@code path}.
 *
 * @param route the path template; {@code path} is the URL asked for
 * @param reason why this one was kept
 */
public record HttpSample(
        UUID id,
        Instant at,
        Source source,
        String requestId,
        String method,
        String route,
        String path,
        int status,
        long millis,
        @Nullable String userId,
        Reason reason)
        implements TelemetryEvent {

    public enum Reason {
        ERROR,
        SLOW,
        RANDOM
    }
}
