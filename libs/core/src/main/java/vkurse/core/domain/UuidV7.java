/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.domain;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** Makes ids that sort by the time they were made. An aggregate gets its id from here; so does a telemetry event. */
public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    /** A UUIDv7 (RFC 9562): 48 bits of milliseconds, then random bits, so ids sort by creation time. */
    public static UUID at(Instant now) {
        var random = new byte[10];
        RANDOM.nextBytes(random);
        long high = (now.toEpochMilli() << 16) | 0x7000L | ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);
        long low = 0x8000000000000000L | ((random[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            low |= (random[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(high, low);
    }
}
