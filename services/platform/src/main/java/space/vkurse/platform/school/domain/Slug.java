/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.domain;

import jakarta.persistence.AttributeConverter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import space.vkurse.core.error.Errors.BadRequestException;

/**
 * The public address of a school ({@code my-school}): lowercase latin letters and digits in groups joined by single
 * hyphens. Words that are, or may become, a path next to a slug are not allowed.
 */
public record Slug(String value) {
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 40;
    public static final String FORMAT = "[a-z0-9]+(-[a-z0-9]+)*";

    private static final Pattern PATTERN = Pattern.compile(FORMAT);
    private static final Set<String> RESERVED =
            Set.of("admin", "api", "auth", "by-slug", "help", "new", "school", "schools", "studying", "support");

    public Slug {
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH
                || !PATTERN.matcher(value).matches()) {
            throw new BadRequestException("The address must have " + MIN_LENGTH + " to " + MAX_LENGTH
                    + " lowercase latin letters, digits and hyphens");
        }
        if (RESERVED.contains(value)) {
            throw new BadRequestException("This address is reserved");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    /** Stores a {@link Slug} as its text. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<Slug, String> {
        @Override
        public @Nullable String convertToDatabaseColumn(@Nullable Slug slug) {
            return slug == null ? null : slug.value();
        }

        @Override
        public @Nullable Slug convertToEntityAttribute(@Nullable String value) {
            return value == null ? null : new Slug(value);
        }
    }
}
