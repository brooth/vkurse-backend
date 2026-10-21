/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.telemetry;

/**
 * The shared scale of {@link HttpWindow#buckets()}: bucket {@code i} counts the answers that took at most
 * {@code UPPER_MILLIS[i]}; the last one counts all the slower ones.
 */
public final class LatencyBuckets {
    public static final int[] UPPER_MILLIS = {
        1, 2, 3, 5, 7, 10, 15, 20, 30, 50, 75, 100, 150, 200, 300, 500, 750, 1000, 2000, 5000, 10000
    };

    public static final int COUNT = UPPER_MILLIS.length + 1;

    private LatencyBuckets() {}

    public static int indexOf(long millis) {
        for (int i = 0; i < UPPER_MILLIS.length; i++) {
            if (millis <= UPPER_MILLIS[i]) {
                return i;
            }
        }
        return UPPER_MILLIS.length;
    }
}
