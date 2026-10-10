/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.Drip;

/** When a lesson opens: {@code days} with {@code AFTER_DAYS}, {@code at} with {@code AT_TIME}. */
public record DripResponse(
        Drip.Kind kind, @Nullable Integer days, @Nullable Instant at) {

    public static DripResponse of(Drip drip) {
        return new DripResponse(drip.kind(), drip.days(), drip.at());
    }
}
