package com.acme.sportplatform.imports.web;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.imports.api.ImportReport;
import com.acme.sportplatform.imports.application.ExcelImportService;
import com.acme.sportplatform.imports.application.ExcelWorkbookService;
import com.acme.sportplatform.events.EventAccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/events/{eventId}/registrations")
@PreAuthorize("hasAnyRole('ORGANIZER', 'OPERATOR', 'PLATFORM_ADMIN')")
public class ExcelImportController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final EventAccessService access;
    private final ExcelImportService imports;
    private final ExcelWorkbookService workbooks;
    private final EventLookup events;

    public ExcelImportController(EventAccessService access, ExcelImportService imports, ExcelWorkbookService workbooks,
                                 EventLookup events) {
        this.access = access;
        this.imports = imports;
        this.workbooks = workbooks;
        this.events = events;
    }

    @GetMapping(value = "/import-template", produces = XLSX)
    @Operation(summary = "Скачать шаблон импорта заявок")
    public ResponseEntity<byte[]> template(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return download(workbooks.template(eventId), "registration-import-template.xlsx");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Импортировать заявки из Excel",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = Upload.class))),
            responses = @ApiResponse(responseCode = "200", description = "Отчёт по строкам",
                    content = @Content(schema = @Schema(implementation = ImportReport.class),
                            examples = @ExampleObject(value = """
                                    {"total":200,"created":196,"updated":2,"skipped":0,
                                     "errors":[{"row":17,"column":"Дата рождения","message":"неверный формат, ожидается ДД.ММ.ГГГГ"}],
                                     "createdProfiles":180}
                                    """))))
    public ImportReport upload(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal, @RequestPart("file") MultipartFile file) {
        access.assertCanModify(eventId, principal);
        UUID actorId = principal.getUserId();
        return imports.execute(eventId, actorId, file);
    }

    @GetMapping(value = "/export", produces = XLSX)
    @Operation(summary = "Выгрузить все заявки события в Excel")
    public ResponseEntity<byte[]> export(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return download(workbooks.export(eventId), "registrations-" + events.getById(eventId).publicSlug() + ".xlsx");
    }

    private static ResponseEntity<byte[]> download(byte[] bytes, String filename) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    public record Upload(@Schema(type = "string", format = "binary", description = "Файл .xlsx, не более 5 МБ") String file) {}
}
