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

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleLargeUpload(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        if (request.getRequestURI().matches("/api/v1/organizations/[^/]+/logo"))
            return ResponseEntity.badRequest().body(new ApiErrorResponse(
                    "organizations.logo_too_large", "Размер логотипа превышает 2 МБ", OffsetDateTime.now(), List.of()));
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiErrorResponse(
                "files.too_large", "Размер файла превышает допустимый", OffsetDateTime.now(), List.of()));
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "imports.missing_file", "Не передан файл в поле file", OffsetDateTime.now(), List.of()));
    }

    @ExceptionHandler(com.acme.sportplatform.common.exception.TooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginLimit(
            com.acme.sportplatform.common.exception.TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfter()))
                .body(new ApiErrorResponse(ex.getCode(), ex.getMessage(), OffsetDateTime.now(), List.of()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(
            BusinessException ex
    ) {
        HttpStatus status = switch (ex.getCode()) {
            case "organizations.not_found" -> HttpStatus.NOT_FOUND;
            case "organizations.slug_taken" -> HttpStatus.CONFLICT;
            case "documents.generation_failed" -> HttpStatus.INTERNAL_SERVER_ERROR;
            case "documents.busy" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "standings.not_published", "documents.not_found", "files.not_found" -> HttpStatus.NOT_FOUND;
            case "documents.access_denied" -> HttpStatus.FORBIDDEN;
            case "documents.too_many_pages", "scheduling.bib_locked", "scheduling.bib_taken", "documents.diploma_disabled" -> HttpStatus.CONFLICT;
            case "scheduling.bib_not_found" -> HttpStatus.NOT_FOUND;
            case "documents.not_pending" -> HttpStatus.CONFLICT;
            case "files.too_large", "imports.file_too_large" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "files.storage_unavailable" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "files.corrupt" -> HttpStatus.CONFLICT;
            case "scheduling.published_units_exist", "scheduling.validation_failed", "scheduling.active_units_exist", "results.lane_conflict" -> HttpStatus.CONFLICT;
            case "organizations.venue_not_found" -> HttpStatus.NOT_FOUND;
            case "events.session_in_use", "events.session_order_conflict", "competition.discipline_in_use", "competition.discipline_code_already_exists", "identity.profile_already_attached", "results.unit_in_progress", "results.unit_already_started" -> HttpStatus.CONFLICT;
            case "events.session_not_found", "results.unit_not_found", "identity.profile_not_found" -> HttpStatus.NOT_FOUND;
            case "identity.access_denied", "identity.user_blocked", "identity.cannot_modify_self" -> HttpStatus.FORBIDDEN;
            case "organizations.invitation_expired" -> HttpStatus.GONE;
            case "organizations.invitation_not_found" -> HttpStatus.NOT_FOUND;
            case "organizations.invitation_exists", "organizations.already_member", "organizations.invitation_unavailable" -> HttpStatus.CONFLICT;
            case "organizations.invitation_email_mismatch", "organizations.protected_member" -> HttpStatus.FORBIDDEN;
            case "organizations.member_not_found", "organizations.user_not_found" -> HttpStatus.NOT_FOUND;
            case "identity.user_already_exists" ->
                    HttpStatus.CONFLICT;

            case "identity.user_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "identity.role_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "identity.invalid_credentials", "identity.refresh_token_invalid" ->
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

            case "results.unit_already_published", "results.start_list_not_published",
                    "results.unit_not_published", "results.unit_has_no_entries",
                    "results.unit_has_missing_results", "results.unit_has_unvalidated_results",
                    "results.unit_has_unranked_results" -> HttpStatus.CONFLICT;

            case "results.concurrent_update" ->
                    HttpStatus.CONFLICT;

            case "results.public_not_found" ->
                    HttpStatus.NOT_FOUND;

            case "events.access_denied", "organizations.access_denied" -> HttpStatus.FORBIDDEN;
            case "events.event_not_found", "registrations.not_found", "organizations.organization_not_found", "organizations.registration_not_found",
                    "competition.discipline_not_found", "results.competition_unit_not_found", "results.entry_not_found" -> HttpStatus.NOT_FOUND;
            case "registrations.already_paid", "registrations.payment_disabled", "payments.not_required", "registrations.mixed_currencies", "organizations.registration_already_reviewed", "registrations.payment_not_applicable" -> HttpStatus.CONFLICT;
            case "publication.event_not_found", "publication.unit_not_found" -> HttpStatus.NOT_FOUND;
            case "publication.access_denied" -> HttpStatus.FORBIDDEN;
            case "publication.invalid_transition" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status).body(
                new ApiErrorResponse(
                        ex.getCode(),
                        ex.getMessage(),
                        OffsetDateTime.now(),
                        ex.getViolations()
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
                                ? "Не удалось выполнить запрос"
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
                        "Ошибка проверки запроса",
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
                        "Некорректный JSON в теле запроса",
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
                        "Доступ запрещён",
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
                "Некорректное значение параметра '" + field + "'";

        if (UUID.class.equals(ex.getRequiredType())) {
            message =
                    "Некорректный UUID параметра '"
                            + field
                            + "'";
        }

        return ResponseEntity.badRequest().body(
                new ApiErrorResponse(
                        "validation_error",
                        "Ошибка проверки запроса",
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

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethod(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        var response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null) response.allow(ex.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        return response.body(error("method_not_allowed", "Метод запроса не поддерживается"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(error("unsupported_media_type", "Тип содержимого запроса не поддерживается"));
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.ServletRequestBindingException.class})
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(Exception ex) {
        return ResponseEntity.badRequest().body(error("validation_error", "Не передан обязательный параметр запроса"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        // Exception messages and cause chains can contain SQL, constraint names and submitted values.
        log.warn("Database integrity conflict ({})", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("data_conflict", "Данные уже существуют или связаны с другими записями"));
    }

    private ApiErrorResponse error(String code, String message) {
        return new ApiErrorResponse(code, message, OffsetDateTime.now(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex
    ) {
        log.warn("Unexpected internal server error ({})", ex.getClass().getSimpleName());

        return ResponseEntity.status(
                HttpStatus.INTERNAL_SERVER_ERROR
        ).body(
                new ApiErrorResponse(
                        "internal_error",
                        "Непредвиденная внутренняя ошибка сервера",
                        OffsetDateTime.now(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
                "common.not_found", "Ресурс не найден", OffsetDateTime.now(), List.of()));
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
