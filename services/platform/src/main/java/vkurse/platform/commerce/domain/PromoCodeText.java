/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import jakarta.persistence.AttributeConverter;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.BadRequestException;

/**
 * The text of a promo code as a buyer types it: latin letters, digits, hyphens and underscores. It is kept in
 * capitals, so {@code spring25} and {@code SPRING25} are one code.
 */
public record PromoCodeText(String value) {
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;

    private static final Pattern PATTERN = Pattern.compile("[A-Z0-9_-]+");

    public PromoCodeText {
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH
                || !PATTERN.matcher(value).matches()) {
            throw new BadRequestException("A promo code has " + MIN_LENGTH + " to " + MAX_LENGTH
                    + " latin letters, digits, hyphens and underscores");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    /** Stores a {@link PromoCodeText} as its text. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<PromoCodeText, String> {
        @Override
        public @Nullable String convertToDatabaseColumn(@Nullable PromoCodeText code) {
            return code == null ? null : code.value();
        }

        @Override
        public @Nullable PromoCodeText convertToEntityAttribute(@Nullable String value) {
            return value == null ? null : new PromoCodeText(value);
        }
    }
}
