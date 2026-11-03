package com.acme.sportplatform.documents.application.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** Only a small XLSX row window stays in memory. POI temporary files are removed on close. */
public interface ExportWriter extends AutoCloseable {
    void row(List<?> cells) throws IOException;
    @Override void close() throws IOException;

    static ExportWriter csv(OutputStream output) throws IOException {
        var csv = new CsvWriter(output);
        return new ExportWriter() {
            public void row(List<?> cells) throws IOException { csv.row(cells); }
            public void close() throws IOException { csv.close(); }
        };
    }

    static ExportWriter xlsx(OutputStream output, String title, boolean official) {
        return new ExportWriter() {
            final SXSSFWorkbook workbook = new SXSSFWorkbook(100);
            final Sheet sheet = workbook.createSheet(title);
            int index;
            {
                workbook.setCompressTempFiles(true);
                sheet.getFooter().setCenter(official ? "Официальный протокол" : "Предварительно");
                sheet.createFreezePane(0, 3);
                sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(2, 2, -1, -1));
                sheet.setDefaultColumnWidth(20);
            }
            public void row(List<?> cells) {
                var row = sheet.createRow(index++);
                for (int i = 0; i < cells.size(); i++) {
                    var cell = row.createCell(i);
                    Object value = cells.get(i);
                    if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                    else if (value instanceof Boolean flag) cell.setCellValue(flag);
                    else cell.setCellValue(value == null ? "" : value.toString()); // String cells never become formulas.
                }
            }
            public void close() throws IOException {
                try (workbook) {
                    workbook.write(output);
                }
            }
        };
    }
}
