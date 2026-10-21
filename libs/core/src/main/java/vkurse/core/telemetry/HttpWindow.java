/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.time.Instant;
import java.util.UUID;

/**
 * All the requests one route answered in one minute, counted on the node. A route is the controller's path template
 * ({@code /schools/{schoolId}/courses}), never the URL asked for, so there are as many as there are endpoints.
 *
 * <p>Percentiles come from {@link #buckets()}: the number of requests in each of the {@link LatencyBuckets}. Buckets of
 * several nodes add up; percentiles of several nodes can't be averaged.
 *
 * @param at the start of the minute
 * @param route {@code UNMATCHED} for a path no controller answers (a 404), so a scan can't make routes up
 * @param clientErrors answers with a 4xx status
 * @param serverErrors answers with a 5xx status
 */
public record HttpWindow(
        UUID id,
        Instant at,
        Source source,
        String method,
        String route,
        long requests,
        long clientErrors,
        long serverErrors,
        long totalMillis,
        long maxMillis,
        long[] buckets)
        implements TelemetryEvent {}
