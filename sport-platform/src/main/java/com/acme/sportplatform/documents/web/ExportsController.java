package com.acme.sportplatform.documents.web;

import com.acme.sportplatform.documents.application.ExportService;
import com.acme.sportplatform.documents.application.ExportService.Kind;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ExportsController {
    private final EventAccessService access;
    private final ExportService exports;

    public ExportsController(EventAccessService access, ExportService exports) {
        this.access = access;
        this.exports = exports;
    }

    @GetMapping({"/organizer/events/{id}/exports/results.xlsx", "/organizer/events/{id}/exports/results.csv"})
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    @Operation(summary = "Экспорт результатов")
    @ApiResponse(responseCode = "200", description = "Файл протокола", content = {
            @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    schema = @Schema(type = "string", format = "binary"))})
    public void results(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        access.assertCanManage(id, principal);
        download(exports.prepare(id, Kind.RESULTS), request, response, "results");
    }

    @GetMapping({"/organizer/events/{id}/exports/standings.xlsx", "/organizer/events/{id}/exports/standings.csv"})
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    @Operation(summary = "Экспорт командного зачёта")
    @ApiResponse(responseCode = "200", description = "Файл протокола", content = {
            @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    schema = @Schema(type = "string", format = "binary"))})
    public void standings(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        access.assertCanManage(id, principal);
        download(exports.prepare(id, Kind.STANDINGS), request, response, "standings");
    }

    @GetMapping({"/organizer/events/{id}/exports/start-list.xlsx", "/organizer/events/{id}/exports/start-list.csv"})
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    @Operation(summary = "Экспорт стартового протокола")
    @ApiResponse(responseCode = "200", description = "Файл протокола", content = {
            @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    schema = @Schema(type = "string", format = "binary"))})
    public void startList(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        access.assertCanManage(id, principal);
        download(exports.prepare(id, Kind.START_LIST), request, response, "start-list");
    }

    @GetMapping("/public/events/{slug}/exports/results.csv")
    @Operation(summary = "Экспорт опубликованных результатов")
    @ApiResponse(responseCode = "200", description = "Файл протокола", content =
            @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    public void publicResults(@PathVariable String slug, HttpServletRequest request, HttpServletResponse response) throws IOException {
        download(exports.publicResults(slug), request, response, "results");
    }

    private void download(ExportService.Plan plan, HttpServletRequest request, HttpServletResponse response, String filename) throws IOException {
        // Public responses normally pass through an ETag filter that buffers the entire body.
        org.springframework.web.filter.ShallowEtagHeaderFilter.disableContentCaching(request);
        boolean xlsx = request.getRequestURI().endsWith(".xlsx");
        response.setContentType(xlsx ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(filename + (xlsx ? ".xlsx" : ".csv"), StandardCharsets.UTF_8).build().toString());
        exports.write(plan, xlsx, response.getOutputStream());
    }
}
