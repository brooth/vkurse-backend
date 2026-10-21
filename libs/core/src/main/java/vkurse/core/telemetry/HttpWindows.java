/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import vkurse.core.domain.UuidV7;

/**
 * Counts requests per minute and route on this node, until the minute is over and {@link #closed} hands it out.
 * A request is counted in the minute it ends, so a minute that has passed is never added to again.
 */
class HttpWindows {
    /** More routes than this means something makes them up; the rest are not counted. */
    private static final int MAX_WINDOWS = 5_000;

    private static final Duration GRACE = Duration.ofSeconds(2);

    private record Key(long minute, String method, String route) {}

    private static final class Window {
        long requests;
        long clientErrors;
        long serverErrors;
        long totalMillis;
        long maxMillis;
        final long[] buckets = new long[LatencyBuckets.COUNT];

        synchronized void add(int status, long millis) {
            requests++;
            if (status >= 500) {
                serverErrors++;
            } else if (status >= 400) {
                clientErrors++;
            }
            totalMillis += millis;
            maxMillis = Math.max(maxMillis, millis);
            buckets[LatencyBuckets.indexOf(millis)]++;
        }
    }

    private final ConcurrentHashMap<Key, Window> windows = new ConcurrentHashMap<>();

    void add(Instant now, String method, String route, int status, long millis) {
        var key = new Key(now.getEpochSecond() / 60, method, route);
        var window = windows.get(key);
        if (window == null) {
            if (windows.size() >= MAX_WINDOWS) {
                return;
            }
            window = windows.computeIfAbsent(key, k -> new Window());
        }
        window.add(status, millis);
    }

    /** The windows of minutes that are over, removed; with {@code everything}, the open ones too (the node stops). */
    List<HttpWindow> closed(Instant now, Source source, boolean everything) {
        var closed = new ArrayList<HttpWindow>();
        var iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var start = Instant.ofEpochSecond(entry.getKey().minute() * 60);
            if (!everything && start.plusSeconds(60).plus(GRACE).isAfter(now)) {
                continue;
            }
            iterator.remove();
            var window = entry.getValue();
            synchronized (window) {
                closed.add(new HttpWindow(
                        UuidV7.at(now),
                        start,
                        source,
                        entry.getKey().method(),
                        entry.getKey().route(),
                        window.requests,
                        window.clientErrors,
                        window.serverErrors,
                        window.totalMillis,
                        window.maxMillis,
                        window.buckets.clone()));
            }
        }
        return closed;
    }
}
