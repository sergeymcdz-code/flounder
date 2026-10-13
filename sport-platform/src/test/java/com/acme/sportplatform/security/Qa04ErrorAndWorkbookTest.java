package com.acme.sportplatform.security;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.common.web.GlobalExceptionHandler;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.imports.application.*;
import com.acme.sportplatform.imports.domain.*;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class Qa04ErrorAndWorkbookTest {
    @Test
    void unexpected500HidesSqlStackAndPrivateData() {
        var response = new GlobalExceptionHandler().handleUnexpected(new IllegalStateException(
                "SQL select password_hash; private-email-qa04@example.org; at secret.java:12"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Непредвиденная внутренняя ошибка сервера");
        assertThat(response.getBody().violations()).isEmpty();
    }
    @Test
    void oversizedAndCompressedBombWorkbooksAreRejectedBeforePersistence() throws Exception {
        var disciplines = mock(EventDisciplineLookup.class);
        var rows = mock(ImportRowService.class);
        var service = new ExcelImportService(disciplines, rows, new ImportRowValidator());
        var oversized = new MockMultipartFile("file", "large.xlsx", "application/octet-stream",
                new byte[(int) ExcelImportService.MAX_FILE_SIZE + 1]);
        assertThatThrownBy(() -> service.execute(UUID.randomUUID(), UUID.randomUUID(), oversized))
                .isInstanceOf(MaxUploadSizeExceededException.class);
        byte[] workbook;
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            for (int r = 0; r < 1500; r++) sheet.createRow(r).createCell(0).setCellValue("compressible-".repeat(500));
            book.write(output);
            try (var source = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(output.toByteArray()));
                 var packed = new ByteArrayOutputStream(); var target = new java.util.zip.ZipOutputStream(packed)) {
                java.util.zip.ZipEntry zip;
                while ((zip = source.getNextEntry()) != null) {
                    target.putNextEntry(new java.util.zip.ZipEntry(zip.getName()));
                    byte[] data = source.readAllBytes();
                    if (zip.getName().equals("xl/worksheets/sheet1.xml"))
                        data = new String(data, java.nio.charset.StandardCharsets.UTF_8)
                                .replace("</worksheet>", "<!--" + "A".repeat(2_000_000) + "--></worksheet>")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    target.write(data); target.closeEntry();
                }
                target.finish(); workbook = packed.toByteArray();
            }
        }
        // A valid OOXML container with a highly compressed sheet triggers POI's zip-bomb guard.
        var bomb = new MockMultipartFile("file", "bomb.xlsx", "application/octet-stream", workbook);
        assertThatThrownBy(() -> service.execute(UUID.randomUUID(), UUID.randomUUID(), bomb))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo("imports.invalid_file");
        verifyNoInteractions(rows, disciplines);
    }
    @Test
    void formulaCellsAreRejectedAndFormulaLikeStringsRemainLiteral() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet(); var header = sheet.createRow(0);
            for (int i = 0; i < ExcelRowParser.HEADERS.size(); i++) header.createCell(i).setCellValue(ExcelRowParser.HEADERS.get(i));
            var row = sheet.createRow(1);
            String[] values = {"Иванов", "Иван", "", "05.03.2014", "М", "=HYPERLINK(\"https://evil.example\")", "M-50-FREE", "32.15"};
            for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            var parser = new ExcelRowParser(); var columns = parser.headers(header);
            assertThat(parser.parse(row, columns).profile().club()).startsWith("=HYPERLINK");
            row.getCell(5).setCellFormula("1+1");
            assertThatThrownBy(() -> parser.parse(row, columns)).isInstanceOf(ImportRowException.class)
                    .hasMessageContaining("формулы");
        }
    }
}
