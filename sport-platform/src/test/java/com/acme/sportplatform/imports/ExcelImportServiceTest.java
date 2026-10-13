package com.acme.sportplatform.imports;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.competition.AgeGroup;
import com.acme.sportplatform.imports.application.ExcelImportService;
import com.acme.sportplatform.imports.application.ImportRowService;
import com.acme.sportplatform.imports.application.ImportRowValidator;
import com.acme.sportplatform.imports.domain.ExcelRowParser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelImportServiceTest {
    private final UUID eventId = UUID.randomUUID();
    private final EventDisciplineLookup disciplines = mock(EventDisciplineLookup.class);
    private final ImportRowService rows = mock(ImportRowService.class);
    private final ExcelImportService service = new ExcelImportService(disciplines, rows,
            new ImportRowValidator());
    private final EventDisciplineSummary published = discipline("M-50-FREE");

    @ParameterizedTest
    @MethodSource("rowFailures")
    void reportsRuntimeAndCommitFailuresAndRetriesNextDuplicateRow(RuntimeException failure) throws Exception {
        when(disciplines.findByEventId(eventId)).thenReturn(List.of(published));
        when(disciplines.findPublishedByEventId(eventId)).thenReturn(List.of(published));
        when(rows.execute(eq(eventId), any(), eq(published))).thenThrow(failure)
                .thenReturn(new ImportRowService.Result(true, true));
        var report = service.execute(eventId, UUID.randomUUID(), file(List.of(
                List.of("Иван", "10.05.2014", "М", "M-50-FREE"),
                List.of("Иван", "10.05.2014", "М", "M-50-FREE"))));
        assertThat(report.total()).isEqualTo(2);
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.createdProfiles()).isEqualTo(1);
        assertThat(report.skipped()).isZero();
        assertThat(report.errors()).singleElement().satisfies(error -> {
            assertThat(error.row()).isEqualTo(2);
            assertThat(error.column()).isEqualTo("Строка");
            assertThat(error.message()).isEqualTo("не удалось сохранить заявку, проверьте данные и повторите импорт");
        });
        verify(rows, times(2)).execute(eq(eventId), any(), eq(published));
    }

    static Stream<RuntimeException> rowFailures() {
        return Stream.of(new IllegalStateException("Persistence failure"),
                new UnexpectedRollbackException("Commit rejected"),
                new TransactionSystemException("Commit failed"));
    }

    @Test
    void doesNotInvokeTransactionalPersistenceForInvalidDisciplinesOrGroups() throws Exception {
        when(disciplines.findByEventId(eventId)).thenReturn(List.of(published, discipline("DRAFT")));
        when(disciplines.findPublishedByEventId(eventId)).thenReturn(List.of(published));
        when(rows.execute(eq(eventId), any(), eq(published))).thenReturn(new ImportRowService.Result(true, true));
        var report = service.execute(eventId, UUID.randomUUID(), file(List.of(
                List.of("Код", "10.05.2014", "М", "X-999"),
                List.of("Публикация", "10.05.2014", "М", "DRAFT"),
                List.of("Пол", "10.05.2014", "Ж", "M-50-FREE"),
                List.of("Возраст", "10.05.2000", "М", "M-50-FREE"),
                List.of("Валид", "10.05.2014", "М", "M-50-FREE"))));
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.errors()).extracting(error -> error.message()).containsExactly(
                "неизвестный код дисциплины в событии", "дисциплина не опубликована",
                "не подходит под возрастные группы вида", "не подходит под возрастные группы вида");
        verify(rows).execute(eq(eventId), argThat(row -> row.profile().firstName().equals("Валид")), eq(published));
        verifyNoMoreInteractions(rows);
    }

    private static EventDisciplineSummary discipline(String code) {
        return new EventDisciplineSummary(UUID.randomUUID(), UUID.randomUUID(), code, code, "INDIVIDUAL", "HEAT",
                "TIME", "ASC", null, BigDecimal.ZERO, "RUB", "{}", 50, "FREE", "M",
                List.of(new AgeGroup("young", "Мальчики", "M", 2010, 2015)), "BY_AGE_GROUP", null, null, null);
    }

    private static MockMultipartFile file(List<List<String>> entries) throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            for (int i = 0; i < 8; i++) header.createCell(i).setCellValue(ExcelRowParser.HEADERS.get(i));
            int index = 1;
            for (var entry : entries) {
                var row = sheet.createRow(index++);
                String[] values = {"Тест", entry.get(0), "", entry.get(1), entry.get(2), "", entry.get(3), "32.15"};
                for (int i = 0; i < 8; i++) row.createCell(i).setCellValue(values[i]);
            }
            var output = new ByteArrayOutputStream();
            workbook.write(output);
            return new MockMultipartFile("file", "rows.xlsx", "application/octet-stream", output.toByteArray());
        }
    }
}
