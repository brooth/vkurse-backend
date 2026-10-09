/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Validation helpers: the constraint-to-code vocabulary used in {@code validation-error} bodies, and custom constraint
 * annotations.
 *
 * <p>Each failed field is described as {@code [code]} or {@code [code, params]}, and nested by field path:
 *
 * <pre>{@code
 * { "title": ["maxLength", { "count": 200 }], "phone": { "code": ["isNotEmpty"] } }
 * }</pre>
 *
 * Only the first error per field is reported.
 */
public final class Validation {

    /** Key for errors that belong to the object as a whole rather than to one field. */
    public static final String OBJECT_KEY = "_";

    private Validation() {}

    /** Builds the {@code constraints} map from binding errors ({@code @Valid @RequestBody}). */
    public static Map<String, Object> constraints(org.springframework.validation.Errors errors) {
        var root = new LinkedHashMap<String, Object>();
        for (var error : errors.getFieldErrors()) {
            put(root, error.getField(), describe(error));
        }
        for (var error : errors.getGlobalErrors()) {
            put(root, OBJECT_KEY, describe(error));
        }
        return root;
    }

    /** Builds the {@code constraints} map from method validation ({@code @PathVariable}, {@code @RequestParam}). */
    public static Map<String, Object> constraints(MethodValidationResult result) {
        var root = new LinkedHashMap<String, Object>();
        for (var parameter : result.getParameterValidationResults()) {
            if (parameter instanceof ParameterErrors errors) {
                constraints(errors).forEach((key, value) -> put(root, key, value));
                continue;
            }
            var name = Objects.requireNonNullElse(parameter.getMethodParameter().getParameterName(), OBJECT_KEY);
            for (var error : parameter.getResolvableErrors()) {
                put(root, name, describe(parameter, error));
            }
        }
        return root;
    }

    /**
     * Builds the {@code constraints} map for a JSON body that could not be mapped to the payload: a wrong type
     * ({@code invalidType}), an unknown enum value ({@code isIn}) or a value a constructor rejected ({@code invalid}).
     * Returns an empty map when the error has no field path.
     */
    public static Map<String, Object> constraints(DatabindException e) {
        var path = jsonPath(e);
        if (path.isEmpty()) {
            return Map.of();
        }
        List<Object> description = switch (e) {
            case InvalidFormatException format
            when format.getTargetType().isEnum() ->
                List.of("isIn", Map.of("values", enumValues(format.getTargetType())));
            case MismatchedInputException _ -> List.of("invalidType");
            default -> List.of("invalid");
        };
        var root = new LinkedHashMap<String, Object>();
        put(root, path, description);
        return root;
    }

    /** Describes one constraint violation as {@code [code]} or {@code [code, params]}. */
    public static List<Object> describe(ConstraintViolation<?> violation) {
        Annotation annotation = violation.getConstraintDescriptor().getAnnotation();
        return switch (annotation) {
            case NotNull _ -> List.of("isDefined");
            case NotBlank _, NotEmpty _ -> List.of("isNotEmpty");
            case Size size -> describeSize(size, violation.getInvalidValue());
            case Min min -> List.of("minValue", Map.of("value", min.value()));
            case DecimalMin min -> List.of("minValue", Map.of("value", min.value()));
            case PositiveOrZero _ -> List.of("minValue", Map.of("value", 0));
            case Positive _ -> List.of("isPositive");
            case Max max -> List.of("maxValue", Map.of("value", max.value()));
            case DecimalMax max -> List.of("maxValue", Map.of("value", max.value()));
            case Pattern pattern -> List.of("invalidFormat", Map.of("regexp", pattern.regexp()));
            case Email _ -> List.of("isEmail");
            case Future _, FutureOrPresent _ -> List.of("isFuture");
            case Past _, PastOrPresent _ -> List.of("isPast");
            default -> List.of("is" + annotation.annotationType().getSimpleName());
        };
    }

    private static List<Object> describe(ObjectError error) {
        if (error.contains(ConstraintViolation.class)) {
            return describe(error.unwrap(ConstraintViolation.class));
        }
        return List.of(Objects.requireNonNullElse(error.getCode(), "invalid"));
    }

    private static List<Object> describe(ParameterValidationResult parameter, MessageSourceResolvable error) {
        try {
            return describe(parameter.unwrap(error, ConstraintViolation.class));
        } catch (IllegalArgumentException e) {
            var codes = error.getCodes();
            return List.of(codes != null && codes.length > 0 ? codes[codes.length - 1] : "invalid");
        }
    }

    private static List<Object> describeSize(Size size, @Nullable Object value) {
        boolean tooShort = sizeOf(value) < size.min();
        boolean isText = value instanceof CharSequence;
        if (tooShort) {
            return List.of(isText ? "minLength" : "arrayMinSize", Map.of("count", size.min()));
        }
        return List.of(isText ? "maxLength" : "arrayMaxSize", Map.of("count", size.max()));
    }

    private static int sizeOf(@Nullable Object value) {
        return switch (value) {
            case null -> 0;
            case CharSequence text -> text.length();
            case Collection<?> collection -> collection.size();
            case Map<?, ?> map -> map.size();
            default -> value.getClass().isArray() ? Array.getLength(value) : 0;
        };
    }

    /** {@code items[0].name}: property names joined by dots, array indexes in brackets. */
    private static String jsonPath(DatabindException e) {
        var path = new StringBuilder();
        for (var reference : e.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String enumValues(Class<?> type) {
        return Arrays.stream(type.getEnumConstants()).map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Puts {@code value} at a dot-separated {@code path}, keeping the first error reported for each field. */
    private static void put(Map<String, Object> root, String path, Object value) {
        var parts = path.split("\\.");
        var node = root;
        for (int i = 0; i < parts.length - 1; i++) {
            var child = node.computeIfAbsent(parts[i], key -> new LinkedHashMap<String, Object>());
            if (!(child instanceof Map<?, ?>)) {
                return; // the parent field already has its own error
            }
            @SuppressWarnings("unchecked") // every nested level is a map created by this method
            var next = (Map<String, Object>) child;
            node = next;
        }
        node.putIfAbsent(parts[parts.length - 1], value);
    }

    // ===== Custom constraints =====

    /** A language code: ISO 639-1 ({@code ru}) or language-region ({@code ru-RU}). {@code null} is valid. */
    @Documented
    @Target({METHOD, FIELD, ANNOTATION_TYPE, PARAMETER, TYPE_USE})
    @Retention(RUNTIME)
    @Constraint(validatedBy = LanguageCodeValidator.class)
    public @interface LanguageCode {
        String message() default "must be a language code, e.g. \"ru\" or \"ru-RU\"";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class LanguageCodeValidator implements ConstraintValidator<LanguageCode, String> {
        private static final Set<String> LANGUAGES = Set.of(Locale.getISOLanguages());
        private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

        @Override
        public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            var parts = value.split("-", -1);
            return switch (parts.length) {
                case 1 -> LANGUAGES.contains(parts[0]);
                case 2 -> LANGUAGES.contains(parts[0]) && COUNTRIES.contains(parts[1]);
                default -> false;
            };
        }
    }

    /** An IANA time zone id, e.g. {@code Europe/Moscow}. {@code null} is valid. */
    @Documented
    @Target({METHOD, FIELD, ANNOTATION_TYPE, PARAMETER, TYPE_USE})
    @Retention(RUNTIME)
    @Constraint(validatedBy = TimeZoneValidator.class)
    public @interface TimeZone {
        String message() default "must be a time zone id, e.g. \"Europe/Moscow\"";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class TimeZoneValidator implements ConstraintValidator<TimeZone, String> {
        @Override
        public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            // ZoneId.of also accepts offsets like "+03:00"; only region ids (and UTC) are stored.
            if (!value.contains("/") && !value.equals("UTC")) {
                return false;
            }
            try {
                ZoneId.of(value);
                return true;
            } catch (DateTimeException e) {
                return false;
            }
        }
    }
}
