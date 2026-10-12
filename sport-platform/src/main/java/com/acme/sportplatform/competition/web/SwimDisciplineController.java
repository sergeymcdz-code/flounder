package com.acme.sportplatform.competition.web;

import com.acme.sportplatform.competition.AgeGroup;
import com.acme.sportplatform.competition.api.*;
import com.acme.sportplatform.competition.application.UpdateEventDisciplineUseCase;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class SwimDisciplineController {
    private final UpdateEventDisciplineUseCase update;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final jakarta.validation.Validator validator;

    public SwimDisciplineController(
            UpdateEventDisciplineUseCase update,
            com.fasterxml.jackson.databind.ObjectMapper json,
            jakarta.validation.Validator validator) {
        this.update = update;
        this.json = json;
        this.validator = validator;
    }

    @PatchMapping("/event-disciplines/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    @io.swagger.v3.oas.annotations.Operation(
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @io.swagger.v3.oas.annotations.media.Content(
                                            schema =
                                                    @io.swagger.v3.oas.annotations.media.Schema(
                                                            implementation =
                                                                    CreateEventDisciplineRequest
                                                                            .class))))
    public EventDisciplineResponse patch(
            @PathVariable UUID id, @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
        CreateEventDisciplineRequest request;
        try {
            request = json.convertValue(body, CreateEventDisciplineRequest.class);
        } catch (IllegalArgumentException ex) {
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "competition.invalid_request", "Некорректный формат параметров дисциплины");
        }
        if (request == null || !validator.validate(request).isEmpty())
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "competition.invalid_request", "Некорректные параметры дисциплины");
        Set<String> cleared = new HashSet<>();
        for (String field :
                List.of("sessionId", "orderInSession", "heatDurationSec", "participantLimit"))
            if (body.has(field) && body.get(field).isNull()) cleared.add(field);
        return update.patch(id, request, cleared);
    }

    @DeleteMapping("/event-disciplines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public void delete(@PathVariable UUID id) {
        update.delete(id);
    }

    @GetMapping("/age-group-presets")
    public List<AgeGroupPreset> presets() {
        int year = Year.now().getValue();
        List<AgeGroup> groups = new ArrayList<>();
        for (String gender : List.of("M", "F"))
            for (int age : List.of(11, 13, 15, 17)) {
                int from = year - age - 1, to = year - age;
                groups.add(
                        new AgeGroup(
                                gender + "-" + from + "-" + to,
                                (gender.equals("M") ? "Юноши " : "Девушки ") + from + "–" + to,
                                gender,
                                from,
                                to));
            }
        return List.of(new AgeGroupPreset("kids-2y", "Детские, по 2 года", groups));
    }

    public record AgeGroupPreset(String code, String name, List<AgeGroup> groups) {}
}
