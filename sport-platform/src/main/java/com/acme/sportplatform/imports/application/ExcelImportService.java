package com.acme.sportplatform.imports.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.ImportDisciplineLookup;
import com.acme.sportplatform.competition.ImportDisciplineLookup.Discipline;
import com.acme.sportplatform.imports.api.ImportReport;
import com.acme.sportplatform.imports.api.ImportReport.RowError;
import com.acme.sportplatform.imports.domain.ExcelRowParser;
import com.acme.sportplatform.imports.domain.ImportRow;
import com.acme.sportplatform.imports.domain.ImportRowException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExcelImportService {
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    public static final int MAX_ROWS = 5000;
    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);
    private final ImportDisciplineLookup disciplines;
    private final ImportRowService rowService;

    public ExcelImportService(ImportDisciplineLookup disciplines, ImportRowService rowService) {
        this.disciplines = disciplines;
        this.rowService = rowService;
    }

    public ImportReport execute(UUID eventId, UUID actorId, MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("imports.invalid_file_type", "Поддерживаются только файлы .xlsx");
        }
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException("imports.file_too_large", "Размер файла превышает 5 МБ");
        if (file.isEmpty()) throw new BusinessException("imports.empty_file", "Файл пуст");
        ExcelRowParser parser = new ExcelRowParser();
        // Validate the entire file structure before committing any rows.
        try (XSSFWorkbook workbook = open(file)) {
            if (workbook.getNumberOfSheets() == 0) throw new BusinessException("imports.empty_file", "В файле нет листов");
            Sheet sheet = workbook.getSheetAt(0);
            Map<Integer, Integer> columns = parser.headers(sheet.getRow(0));
            int total = 0;
            for (Row row : sheet) {
                if (row.getRowNum() > 0 && !parser.isEmpty(row, columns)) total++;
            }
            if (total > MAX_ROWS) throw new BusinessException("imports.too_many_rows", "В файле больше 5 000 строк заявок");
            Map<String, Discipline> byCode = disciplines.findByEventId(eventId).stream()
                    .collect(Collectors.toMap(Discipline::code, Function.identity()));
            var errors = new ArrayList<RowError>();
            Set<String> imported = new HashSet<>();
            int created = 0, updated = 0, skipped = 0, createdProfiles = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0 || parser.isEmpty(row, columns)) continue;
                try {
                    ImportRow parsed = parser.parse(row, columns);
                    if (imported.contains(parsed.key())) { skipped++; continue; }
                    ImportRowService.Result result = rowService.execute(eventId, parsed, byCode.get(parsed.disciplineCode()));
                    imported.add(parsed.key());
                    if (result.created()) created++; else updated++;
                    if (result.createdProfile()) createdProfiles++;
                } catch (ImportRowException ex) {
                    errors.add(new RowError(row.getRowNum() + 1, ex.column(), ex.getMessage()));
                } catch (org.springframework.dao.DataAccessException ex) {
                    log.warn("Import row failed: event={}, row={}", eventId, row.getRowNum() + 1, ex);
                    errors.add(new RowError(row.getRowNum() + 1, "Строка", "не удалось сохранить заявку, проверьте данные и повторите импорт"));
                }
            }
            ImportReport report = new ImportReport(total, created, updated, skipped, errors, createdProfiles);
            // No audit_logs table exists yet. Use the application log as the task permits.
            log.info("Импорт: кто={}, событие={}, файл={}, total={}, created={}, updated={}, skipped={}, errors={}, createdProfiles={}",
                    actorId, eventId, name.replaceAll("[\\r\\n]", "_"), total, created, updated, skipped, errors.size(), createdProfiles);
            return report;
        } catch (java.io.IOException ex) {
            throw new BusinessException("imports.invalid_file", "Не удалось прочитать файл Excel");
        }
    }

    private XSSFWorkbook open(MultipartFile file) {
        try (var input = file.getInputStream()) { return new XSSFWorkbook(input); }
        catch (Exception ex) { throw new BusinessException("imports.invalid_file", "Файл повреждён или не является книгой .xlsx"); }
    }
}
