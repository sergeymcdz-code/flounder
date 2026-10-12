package com.acme.sportplatform.publication.web;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.acme.sportplatform.publication.api.PublicResponses.SetLiveRequest;
import com.acme.sportplatform.publication.application.LiveManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Live management", description = "Управление live-табло организатором")
public class LiveManagementController {
    private final LiveManagementService service;
    public LiveManagementController(LiveManagementService service) { this.service = service; }
    @PutMapping("/events/{eventId}/live")
    @Operation(summary = "Выбрать текущий заплыв; null очищает табло")
    public void setCurrent(@PathVariable UUID eventId, @RequestBody SetLiveRequest request, Authentication authentication) {
        service.setCurrent(eventId,request.currentUnitId(),authentication);
    }
    @PostMapping("/competition-units/{id}/start")
    @Operation(summary = "Начать заплыв и показать его на табло")
    public void start(@PathVariable UUID id, Authentication authentication) { service.start(id,authentication); }
    @PostMapping("/competition-units/{id}/finish")
    @Operation(summary = "Завершить заплыв и перейти к следующему по расписанию")
    public void finish(@PathVariable UUID id, Authentication authentication) { service.finish(id,authentication); }
}
