package com.acme.sportplatform.common.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.common.api.ApiErrorResponse;
import com.acme.sportplatform.common.exception.BusinessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(
            BusinessException ex
    ) {
        HttpStatus status = switch (ex.getCode()) {
            case "identity.user_already_exists" ->
                    HttpStatus.CONFLICT;

            case "identity.user_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "identity.role_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "identity.invalid_credentials" ->
                    HttpStatus.UNAUTHORIZED;

            case "sports.sport_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "sports.discipline_template_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "regulations.template_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "regulations.version_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "events.public_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "registrations.not_found" ->
                    HttpStatus.NOT_FOUND;

            case "payments.not_found" ->
                    HttpStatus.NOT_FOUND;

            case "sports.sport_already_exists" ->
                    HttpStatus.CONFLICT;

            case "sports.discipline_template_already_exists" ->
                    HttpStatus.CONFLICT;

            case "regulations.template_already_exists" ->
                    HttpStatus.CONFLICT;

            case "regulations.version_already_exists" ->
                    HttpStatus.CONFLICT;

            case "registrations.already_exists" ->
                    HttpStatus.CONFLICT;

            case "payments.already_exists" ->
                    HttpStatus.CONFLICT;

            case "registrations.discipline_limit_reached" ->
                    HttpStatus.CONFLICT;

            case "results.concurrent_update" ->
                    HttpStatus.CONFLICT;

            case "results.public_not_found" ->
                    HttpStatus.NOT_FOUND;

            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status).body(
                new ApiErrorResponse(
                        ex.getCode(),
                        ex.getMessage(),
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler({
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiErrorResponse> handleOptimisticLocking(
            OptimisticLockingFailureException ex
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiErrorResponse(
                        "results.concurrent_update",
                        "Данные были изменены другим пользователем. Обновите страницу и повторите действие.",
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException ex
    ) {
        HttpStatusCode statusCode = ex.getStatusCode();

        return ResponseEntity.status(statusCode).body(
                new ApiErrorResponse(
                        "request_error",
                        ex.getReason() == null
                                ? "Request could not be completed"
                                : ex.getReason(),
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        List<ApiErrorResponse.FieldViolation> violations =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(this::toViolation)
                        .toList();

        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "validation_error",
                        "Request validation failed",
                        OffsetDateTime.now(),
                        violations
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex
    ) {
        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "invalid_request_body",
                        "Request body has an invalid JSON format",
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler({
            AuthorizationDeniedException.class,
            AccessDeniedException.class
    })
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            Exception ex
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new ApiErrorResponse(
                        "access_denied",
                        "Access is denied",
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        String field = ex.getName();

        String message =
                "Invalid value for parameter '" + field + "'";

        if (UUID.class.equals(ex.getRequiredType())) {
            message =
                    "Invalid UUID format for parameter '"
                            + field
                            + "'";
        }

        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "validation_error",
                        "Request validation failed",
                        OffsetDateTime.now(),
                        List.of(
                                new ApiErrorResponse.FieldViolation(
                                        field,
                                        message
                                )
                        )
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex
    ) {
        log.error("Unexpected internal server error", ex);

        return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
        ).body(
                new ApiErrorResponse(
                        "internal_error",
                        "Unexpected internal server error",
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    private ApiErrorResponse.FieldViolation toViolation(
            FieldError error
    ) {
        return new ApiErrorResponse.FieldViolation(
                error.getField(),
                error.getDefaultMessage()
        );
    }
}