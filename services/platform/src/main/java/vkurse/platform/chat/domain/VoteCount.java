/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import org.jspecify.annotations.Nullable;

/**
 * The votes on a message.
 *
 * @param mine the vote of the person asking: 1, -1, or {@code null} for none (or when nobody in particular asks)
 */
public record VoteCount(long up, long down, @Nullable Integer mine) {
    public static final VoteCount NONE = new VoteCount(0, 0, null);
}
