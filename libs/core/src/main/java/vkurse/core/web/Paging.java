/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.web;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What a client may ask of a list endpoint: paging and search. Filters and sort always come from the server.
 *
 * <ul>
 *   <li>{@code $limit}: 1–{@value #MAX_LIMIT}, default {@value #DEFAULT_LIMIT}
 *   <li>{@code $skip}: 0 or more
 *   <li>{@code $search}: trimmed, lowercased, at most {@value #MAX_SEARCH_LENGTH} characters
 * </ul>
 *
 * Invalid values are ignored.
 *
 * <pre>{@code
 * @GetMapping
 * List<SessionResponse> list(@GetSession UserSession session, @RequestParam Map<String, String> params) {
 *     return sessions.findMine(session.userId(), Paging.parse(params)).stream().map(SessionResponse::of).toList();
 * }
 * }</pre>
 */
public record Paging(int limit, int offset, @Nullable String search) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    public static final int MAX_SEARCH_LENGTH = 100;

    public static Paging parse(Map<String, String> params) {
        var limit = parseInt(params.get("$limit"))
                .filter(n -> n > 0)
                .map(n -> Math.min(n, MAX_LIMIT))
                .orElse(DEFAULT_LIMIT);
        var offset = parseInt(params.get("$skip")).filter(n -> n >= 0).orElse(0);
        var search = Optional.ofNullable(params.get("$search"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .map(s -> s.length() > MAX_SEARCH_LENGTH ? s.substring(0, MAX_SEARCH_LENGTH) : s)
                .orElse(null);
        return new Paging(limit, offset, search);
    }

    private static Optional<Integer> parseInt(@Nullable String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
