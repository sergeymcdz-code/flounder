package com.acme.sportplatform.publication.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.acme.sportplatform.publication.api.PublicResponses.*;
import com.acme.sportplatform.publication.application.PublicPublicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/public/events/{slug}")
@Tag(name = "Public event", description = "Публичные представления соревнования без авторизации")
public class PublicPublicationController {
    private final PublicPublicationService service;
    public PublicPublicationController(PublicPublicationService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Карточка события")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
            "{\"title\":\"Кубок Самары\",\"registrationOpen\":true,\"venue\":{\"name\":\"Бассейн\",\"city\":\"Самара\",\"lanes\":8,\"courseLength\":25},\"sessions\":[],\"disciplines\":[]}")))
    public Event event(@PathVariable String slug) { return service.event(slug); }

    @GetMapping("/start-list")
    @Operation(summary = "Опубликованный стартовый протокол", description = "Сессии → виды → заплывы → все дорожки; снятые участники помечены withdrawn.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
            "{\"sessions\":[{\"name\":\"Утро\",\"disciplines\":[{\"number\":1,\"name\":\"50 м\",\"heats\":[{\"number\":1,\"scheduledAt\":\"2026-10-17T10:00:00Z\",\"lanes\":[{\"lane\":1,\"empty\":true},{\"lane\":4,\"athlete\":{\"lastName\":\"Иванов\",\"firstName\":\"Иван\",\"birthYear\":2014,\"club\":\"Волна\"},\"seedTimeMs\":32150,\"display\":\"00:32.15\"}]}]}]}]}")))
    public StartList startList(@PathVariable String slug) { return service.startList(slug); }

    @GetMapping("/schedule")
    @Operation(summary = "Расписание по времени")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
            "[{\"sessionName\":\"Утро\",\"disciplineName\":\"50 м\",\"heatNumber\":1,\"sequenceInSession\":1,\"scheduledAt\":\"2026-10-17T10:00:00Z\",\"status\":\"PUBLISHED\"}]")))
    public List<Schedule> schedule(@PathVariable String slug) { return service.schedule(slug); }

    @GetMapping("/live")
    @Operation(summary = "Текущий заплыв и следующие три", description = "Обновление из кэша не реже 10 секунд.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
            "{\"currentUnit\":null,\"next\":[{\"id\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"number\":1,\"ageGroupCode\":null,\"scheduledAt\":\"2026-10-17T10:00:00Z\",\"sequenceInSession\":1,\"status\":\"PUBLISHED\",\"lanes\":[{\"lane\":1,\"empty\":true}],\"disciplineId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"disciplineNumber\":1,\"disciplineName\":\"50 м\"}],\"updatedAt\":\"2026-10-17T09:59:00Z\"}")))
    public Live live(@PathVariable String slug) { return service.live(slug); }

    @GetMapping("/results")
    @Operation(summary = "Опубликованные результаты по видам и возрастным группам")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
            "{\"disciplines\":[{\"id\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"number\":1,\"name\":\"50 м\",\"overall\":[{\"place\":1,\"lastName\":\"Иванов\",\"firstName\":\"Иван\",\"birthYear\":2014,\"club\":\"Волна\",\"display\":\"00:32.15\",\"timeMs\":32150,\"status\":\"VALID\"}],\"byAgeGroup\":[]}]}")))
    public Results results(@PathVariable String slug) { return service.results(slug); }

    @GetMapping("/athletes")
    @Operation(summary = "Найти себя", description = "Префикс фамилии без учёта регистра и ё/е, максимум 20 спортсменов.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", examples = @ExampleObject(value =
            "[{\"athleteKey\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"lastName\":\"Иванов\",\"firstName\":\"Иван\",\"birthYear\":2014,\"club\":\"Волна\",\"swims\":[{\"disciplineName\":\"50 м\",\"heatNumber\":1,\"lane\":4,\"scheduledAt\":null,\"result\":{\"display\":\"00:32.15\",\"status\":\"VALID\",\"place\":1,\"placeInAgeGroup\":1}}]}]")))
    public List<AthleteSwims> athletes(@PathVariable String slug, @RequestParam(defaultValue = "") String q) { return service.athletes(slug,q); }
}
