/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/** What {@code vkurse.telemetry.enabled=false} gets: nothing is counted or sent. */
public class NoopTelemetry implements Telemetry {

    @Override
    public void request(
            String requestId,
            String method,
            String route,
            String path,
            int status,
            long millis,
            @Nullable String userId) {}

    @Override
    public void job(String job, long millis, @Nullable Throwable failure) {}

    @Override
    public void outsideCall(
            String client, String method, URI uri, int status, long millis, @Nullable Throwable failure) {}
}
