/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import jakarta.persistence.AttributeConverter;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.BadRequestException;

/**
 * The address of a course inside its school ({@code python-for-beginners}): lowercase latin letters and digits in
 * groups joined by single hyphens.
 */
public record CourseSlug(String value) {
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 80;
    public static final String FORMAT = "[a-z0-9]+(-[a-z0-9]+)*";

    private static final Pattern PATTERN = Pattern.compile(FORMAT);

    public CourseSlug {
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH
                || !PATTERN.matcher(value).matches()) {
            throw new BadRequestException("The address must have " + MIN_LENGTH + " to " + MAX_LENGTH
                    + " lowercase latin letters, digits and hyphens");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    /** Stores a {@link CourseSlug} as its text. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<CourseSlug, String> {
        @Override
        public @Nullable String convertToDatabaseColumn(@Nullable CourseSlug slug) {
            return slug == null ? null : slug.value();
        }

        @Override
        public @Nullable CourseSlug convertToEntityAttribute(@Nullable String value) {
            return value == null ? null : new CourseSlug(value);
        }
    }
}
