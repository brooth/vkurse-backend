/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import vkurse.core.error.Errors.BadRequestException;

/** The rule of reordering: which lists of ids are an order of a course's sections, or of a section's lessons. */
public final class Arrangement {

    private Arrangement() {}

    /**
     * Checks an order a client asked for. It must name each id once, every id that is there now ({@code current}:
     * nothing may be left out), and only ids that may be there ({@code allowed}: for lessons, those of the same
     * course, so a lesson can move in from another section).
     */
    public static void check(List<UUID> requested, Collection<UUID> current, Collection<UUID> allowed) {
        var named = new HashSet<>(requested);
        if (named.size() != requested.size()) {
            throw new BadRequestException("The order names something twice");
        }
        if (!named.containsAll(current)) {
            throw new BadRequestException("The order must name everything that is there now");
        }
        if (!allowed.containsAll(named)) {
            throw new BadRequestException("The order names something that doesn't belong here");
        }
    }
}
