package com.acme.sportplatform.documents;

import com.acme.sportplatform.documents.application.export.ExportWriter;
import java.io.*;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExportWriterTest {
    @Test
    void streamsTenThousandRowsWithTypedCellsAndNoFormulaCells() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var writer = ExportWriter.xlsx(output, "Результаты", false)) {
            for (int i = 0; i < 10_000; i++) writer.row(List.of(i, "=1+1", false));
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(10_000);
            assertThat(sheet.getRow(9_999).getCell(0).getNumericCellValue()).isEqualTo(9_999);
            assertThat(sheet.getRow(0).getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("=1+1");
            assertThat(sheet.getFooter().getCenter()).isEqualTo("Предварительно");
        }
    }
}
