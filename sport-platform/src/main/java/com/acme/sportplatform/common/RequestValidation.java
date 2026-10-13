package com.acme.sportplatform.common;

import com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation;
import com.acme.sportplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.*;

import jakarta.validation.Validator;

import java.util.*;

public final class RequestValidation {
    private RequestValidation() {}

    public static <T> T read(
            ObjectMapper json,
            JsonNode body,
            Class<T> type,
            Validator validator,
            String prefix,
            List<FieldViolation> errors) {
        try {
            if (body == null || !body.isObject()) {
                errors.add(
                        new FieldViolation(
                                prefix.isEmpty() ? "body" : prefix, "Ожидается объект JSON"));
                return null;
            }
            T value = json.treeToValue(body, type);
            validator.validate(value).stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .forEach(
                            v ->
                                    errors.add(
                                            new FieldViolation(
                                                    prefix + v.getPropertyPath(), v.getMessage())));
            return value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            String path =
                    e instanceof JsonMappingException m
                            ? m.getPath().stream()
                                    .map(
                                            ref ->
                                                    ref.getFieldName() != null
                                                            ? ref.getFieldName()
                                                            : "[" + ref.getIndex() + "]")
                                    .reduce((a, b) -> a + "." + b)
                                    .orElse("")
                            : "";
            errors.add(new FieldViolation(prefix + path, "Некорректное значение поля"));
            return null;
        }
    }

    public static void requireValid(String code, List<FieldViolation> errors) {
        if (!errors.isEmpty()) throw new BusinessException(code, "Ошибка проверки запроса", errors);
    }

    public static BusinessException field(String code, String field, String message) {
        return new BusinessException(code, message, List.of(new FieldViolation(field, message)));
    }
}
