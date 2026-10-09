/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.error;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.DatabindException;
import vkurse.core.error.Errors.ClientException;
import vkurse.core.error.Errors.ForbiddenException;
import vkurse.core.error.Errors.ServerException;
import vkurse.core.error.Errors.UnauthorizedException;
import vkurse.core.validation.Validation;

/**
 * Turns every exception into an RFC 9457 problem body with a machine-readable {@code code}:
 *
 * <pre>{@code
 * { "type": "about:blank", "title": "Not Found", "status": 404, "detail": "Course not found",
 *   "instance": "/schools/…/courses/…", "code": "not-found" }
 * }</pre>
 *
 * Client errors are logged at WARN in one line. Server errors are logged with their stack trace, and the client only
 * gets a generic message.
 */
@RestControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ClientException.class)
    ResponseEntity<ProblemDetail> handleClientException(ClientException e) {
        var body = problem(e.getStatus(), e.getCode(), e.getMessage());
        e.getRecords().forEach(body::setProperty);
        logClientError(body);
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn(
                "Sending 409 client error conflict: {}",
                e.getMostSpecificCause().getMessage());
        var status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(problem(status, "conflict", "The request conflicts with existing data"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(OptimisticLockingFailureException e) {
        log.warn("Sending 409 client error conflict/stale: {}", e.getMessage());
        var status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(problem(status, "conflict/stale", "The resource was changed by someone else, reload and retry"));
    }

    /** Thrown by method security ({@code @AdminOnly}) inside a controller. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
        return handleClientException(new ForbiddenException());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException e) {
        return handleClientException(new UnauthorizedException());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleServerError(Exception e) {
        if (e instanceof ServerException server && !server.getRecords().isEmpty()) {
            log.error("Unhandled server error {}", server.getRecords(), e);
        } else {
            log.error("Unhandled server error", e);
        }
        var status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(problem(status, "server-error", "Server error, please try again later"));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = validationProblem(Validation.constraints(ex.getBindingResult()));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = validationProblem(Validation.constraints(ex));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** A JSON body that parses but doesn't fit the payload (wrong type, bad enum value) names the offending field. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof DatabindException json) {
            var constraints = Validation.constraints(json);
            if (!constraints.isEmpty()) {
                return handleExceptionInternal(ex, validationProblem(constraints), headers, status, request);
            }
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Sending {} server error", statusCode.value(), ex);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /**
     * Adds a {@code code} to the problem bodies Spring MVC builds for its own exceptions (404, 405, 415, …) and logs
     * client errors. The body is only complete here: the built-in handlers pass {@code null} and let the base class
     * build it.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (!hasCode(problem)) {
                problem.setProperty("code", codeOf(statusCode));
            }
            if (statusCode.is4xxClientError()) {
                logClientError(problem);
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** One line per client error: {@code Sending 404 client error not-found: Course not found}. */
    private static void logClientError(ProblemDetail problem) {
        var properties = new LinkedHashMap<>(Objects.requireNonNullElse(problem.getProperties(), Map.of()));
        var code = properties.remove("code");
        log.warn(
                "Sending {} client error {}: {}{}",
                problem.getStatus(),
                code,
                problem.getDetail(),
                properties.isEmpty() ? "" : " " + properties);
    }

    private static ProblemDetail validationProblem(Map<String, Object> constraints) {
        var body = problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation error. See constraints");
        body.setProperty("constraints", constraints);
        return body;
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, @Nullable String detail) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return body;
    }

    private static boolean hasCode(ProblemDetail problem) {
        var properties = problem.getProperties();
        return properties != null && properties.containsKey("code");
    }

    /** {@code 404 Not Found} → {@code not-found}, {@code 405 Method Not Allowed} → {@code method-not-allowed}. */
    private static String codeOf(HttpStatusCode statusCode) {
        var status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return statusCode.is5xxServerError() ? "server-error" : "client-error";
        }
        return status.getReasonPhrase()
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace(' ', '-');
    }
}
