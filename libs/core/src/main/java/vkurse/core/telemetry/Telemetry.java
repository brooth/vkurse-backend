/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Tells {@code monitoring} what happened. Every method returns at once and never throws: telemetry must not slow a
 * request or fail a job, so what can't be sent is dropped and counted.
 *
 * <p>The framework's hooks call it ({@code HttpLogFilter}, {@code HttpClients}, {@code JobLog}); a service rarely does.
 * With {@code vkurse.telemetry.enabled} off it does nothing.
 */
public interface Telemetry {

    /**
     * A request this service answered.
     *
     * @param route the controller's path template, or {@code UNMATCHED}
     * @param path the URL asked for, without its query
     */
    void request(
            String requestId,
            String method,
            String route,
            String path,
            int status,
            long millis,
            @Nullable String userId);

    /** A run of a scheduled job. */
    void job(String job, long millis, @Nullable Throwable failure);

    /** A call to an outside API; {@code status} is 0 and {@code failure} is set if there was no answer. */
    void outsideCall(String client, String method, URI uri, int status, long millis, @Nullable Throwable failure);
}
