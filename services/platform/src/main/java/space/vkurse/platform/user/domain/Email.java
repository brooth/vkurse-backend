/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.user.domain;

import jakarta.persistence.AttributeConverter;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import space.vkurse.core.error.Errors.BadRequestException;

/** An email address, trimmed and lowercased, so two spellings of one address are equal. */
public record Email(String value) {
    public static final int MAX_LENGTH = 320;

    private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+");

    public Email {
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new BadRequestException("Invalid email address");
        }
    }

    /** The part before the {@code @}: the name of a user who gave none. */
    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }

    @Override
    public String toString() {
        return value;
    }

    /** Stores an {@link Email} as its text. */
    @jakarta.persistence.Converter
    public static class Converter implements AttributeConverter<Email, String> {
        @Override
        public @Nullable String convertToDatabaseColumn(@Nullable Email email) {
            return email == null ? null : email.value();
        }

        @Override
        public @Nullable Email convertToEntityAttribute(@Nullable String value) {
            return value == null ? null : new Email(value);
        }
    }
}
