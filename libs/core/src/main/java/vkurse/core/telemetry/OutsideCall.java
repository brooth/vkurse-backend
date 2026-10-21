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
 * One call to an outside HTTP API, made through {@code HttpClients}. Nothing of the URL but the host and the path.
 *
 * @param client the name the client was built with ({@code yookassa})
 * @param status 0 if there was no answer
 * @param error the exception's class when there was no answer
 */
public record OutsideCall(
        UUID id,
        Instant at,
        Source source,
        String client,
        String method,
        String host,
        String path,
        int status,
        long millis,
        @Nullable String error)
        implements TelemetryEvent {}
