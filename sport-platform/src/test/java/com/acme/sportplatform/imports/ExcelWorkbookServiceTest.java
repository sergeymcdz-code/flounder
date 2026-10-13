package com.acme.sportplatform.imports;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.imports.application.ExcelWorkbookService;
import com.acme.sportplatform.registrations.RegistrationImportLookup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelWorkbookServiceTest {
    @Test
    void exportsEveryRegistrationAndPaymentStatusInRussian() throws Exception {
        UUID eventId = UUID.randomUUID();
        var disciplines = mock(EventDisciplineLookup.class);
        var registrations = mock(RegistrationImportLookup.class);
        var entries = new ArrayList<RegistrationImportLookup.ExportRow>();
        List<String> statuses = List.of("DRAFT", "SUBMITTED", "UNDER_REVIEW", "CONFIRMED", "NEEDS_CORRECTION",
                "REJECTED", "PAID", "ADMITTED", "WITHDRAWN");
        List<String> labels = List.of("Черновик", "Подана", "На рассмотрении", "Подтверждена", "Требует уточнения",
                "Отклонена", "Оплачена", "Допущена", "Снята");
        List<String> payments = List.of("NOT_REQUIRED", "PENDING", "PAID", "REFUNDED");
        List<String> paymentLabels = List.of("Не требуется", "Ожидает оплаты", "Оплачено", "Возвращено");
        for (int i = 0; i < statuses.size(); i++) {
            entries.add(new RegistrationImportLookup.ExportRow(UUID.randomUUID(), "{}", null,
                    statuses.get(i), payments.get(i % payments.size())));
        }
        when(disciplines.findByEventId(eventId)).thenReturn(List.of());
        when(registrations.findByEventId(eventId)).thenReturn(entries);
        var service = new ExcelWorkbookService(disciplines, registrations, new ObjectMapper());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(eventId)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(statuses.size());
            for (int i = 0; i < statuses.size(); i++) {
                assertThat(sheet.getRow(i + 1).getCell(8).getStringCellValue()).isEqualTo(labels.get(i));
                assertThat(sheet.getRow(i + 1).getCell(9).getStringCellValue()).isEqualTo(paymentLabels.get(i % payments.size()));
            }
        }
    }
}
