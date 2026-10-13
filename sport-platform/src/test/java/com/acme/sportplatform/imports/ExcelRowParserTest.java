package com.acme.sportplatform.imports;

import com.acme.sportplatform.common.TimeParser;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.ProfileData;
import com.acme.sportplatform.identity.ProfileExternalKey;
import java.time.ZoneId;
import com.acme.sportplatform.imports.domain.ExcelRowParser;
import com.acme.sportplatform.imports.domain.ImportRowException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ExcelRowParserTest {
    private final ExcelRowParser parser = new ExcelRowParser();

    @Test
    void acceptsStrictRussianIsoAndExcelDates() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var row = workbook.createSheet().createRow(0);
            var cell = row.createCell(0);
            cell.setCellValue("10.05.2014");
            assertThat(parser.birthDate(cell)).isEqualTo(LocalDate.of(2014, 5, 10));
            cell.setCellValue("2014-05-10");
            assertThat(parser.birthDate(cell)).isEqualTo(LocalDate.of(2014, 5, 10));
            cell.setCellValue(LocalDate.of(2014, 5, 10));
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy"));
            cell.setCellStyle(style);
            assertThat(parser.birthDate(cell)).isEqualTo(LocalDate.of(2014, 5, 10));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"31.02.2013", "29.02.2013", "2013-02-31", "2014/05/10", "1.5.2014", "01.01.14"})
    void rejectsInvalidDatesWithColumn(String value) throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var cell = workbook.createSheet().createRow(0).createCell(0);
            cell.setCellValue(value);
            assertThatThrownBy(() -> parser.birthDate(cell)).isInstanceOfSatisfying(ImportRowException.class,
                    ex -> assertThat(ex.column()).isEqualTo(ExcelRowParser.HEADERS.get(3)))
                    .hasMessageContaining("ДД.ММ.ГГГГ");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void reportsMissingBirthDate(String value) throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var cell = value == null ? null : workbook.createSheet().createRow(0).createCell(0);
            if (cell != null) cell.setCellValue(value);
            assertThatThrownBy(() -> parser.birthDate(cell)).isInstanceOfSatisfying(ImportRowException.class,
                    ex -> assertThat(ex.column()).isEqualTo(ExcelRowParser.HEADERS.get(3)))
                    .hasMessage("дата рождения не заполнена");
        }
    }

    @Test
    void validatesBirthDateBoundsForTextAndExcelDates() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var cell = workbook.createSheet().createRow(0).createCell(0);
            cell.setCellValue("1899-12-31");
            assertThatThrownBy(() -> parser.birthDate(cell)).hasMessage("дата рождения не может быть раньше 1900 года");
            cell.setCellValue("01.01.1900");
            assertThat(parser.birthDate(cell)).isEqualTo(LocalDate.of(1900, 1, 1));
            cell.setCellValue(LocalDate.now(ZoneId.of("Europe/Moscow")).toString());
            assertThat(parser.birthDate(cell)).isEqualTo(LocalDate.now(ZoneId.of("Europe/Moscow")));
            cell.setCellValue(LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(1).toString());
            assertThatThrownBy(() -> parser.birthDate(cell)).hasMessage("дата рождения не может быть в будущем");
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy"));
            cell.setCellStyle(style);
            cell.setCellValue(LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(1));
            assertThatThrownBy(() -> parser.birthDate(cell)).hasMessage("дата рождения не может быть в будущем");
            cell.setCellValue(LocalDate.of(1900, 1, 1));
            assertThat(parser.birthDate(cell)).isEqualTo(LocalDate.of(1900, 1, 1));
        }
    }

    @Test
    void rejectsNumericSeedTimesRegardlessOfDisplayFormat() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            for (int i = 0; i < 8; i++) header.createCell(i).setCellValue(ExcelRowParser.HEADERS.get(i));
            Row row = sheet.createRow(1);
            String[] values = {"Иванов", "Иван", "", "10.05.2014", "М", "", "M-50-FREE", ""};
            for (int i = 0; i < 8; i++) row.createCell(i).setCellValue(values[i]);
            var time = row.getCell(7);
            for (String format : List.of("General", "@", "mm:ss.00")) {
                var style = workbook.createCellStyle();
                style.setDataFormat(workbook.createDataFormat().getFormat(format));
                time.setCellStyle(style);
                time.setCellValue(32.15 / 86400);
                assertThatThrownBy(() -> parser.parse(row, parser.headers(header)))
                        .isInstanceOfSatisfying(ImportRowException.class,
                                ex -> assertThat(ex.column()).isEqualTo(ExcelRowParser.HEADERS.get(7)))
                        .hasMessage("введите время текстом мм:сс.сс");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"М,M", "M,M", "m,M", "Ж,F", "F,F", "f,F"})
    void parsesGender(String input, String expected) { assertThat(parser.gender(input)).isEqualTo(expected); }

    @ParameterizedTest
    @ValueSource(strings = {"", "X", "MALE", "мужской"})
    void rejectsOtherGender(String input) { assertThatThrownBy(() -> parser.gender(input)).isInstanceOf(ImportRowException.class); }

    @ParameterizedTest
    @CsvSource({"32.15,32150", "0:32.15,32150", "00:32.15,32150", "1:02:30.50,3750500", "02:08.00,128000", "0:32,32000", "32,32000", "32.1,32100", "32.123,32123"})
    void parsesSwimmingTime(String input, long expected) { assertThat(TimeParser.parse(input)).isEqualTo(expected); }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1.00", "00:60.00", "1:60:00.00", "00:00.00", "NT", "32,15", "32.1234", "999999999999999999999:01:01.00"})
    void rejectsInvalidTime(String input) { assertThatThrownBy(() -> TimeParser.parse(input)).isInstanceOf(BusinessException.class); }

    @Test
    void normalizesHeadersAndIgnoresUnknownOptionalColumns() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Row headers = workbook.createSheet().createRow(0);
            for (int i : List.of(0, 1, 3, 4, 6)) {
                headers.createCell(i).setCellValue("  " + ExcelRowParser.HEADERS.get(i).toUpperCase() + "  ");
            }
            headers.createCell(8).setCellValue("Комментарий");
            Map<Integer, Integer> columns = parser.headers(headers);
            Row row = headers.getSheet().createRow(1);
            row.createCell(0).setCellValue("  Киселёв  ");
            row.createCell(1).setCellValue("Иван");
            row.createCell(3).setCellValue("10.05.2014");
            row.createCell(4).setCellValue("М");
            row.createCell(6).setCellValue("M-50-FREE");
            var parsed = parser.parse(row, columns);
            assertThat(parsed.externalKey()).isEqualTo("киселев|иван|2014-05-10|m");
            assertThat(parsed.profile().middleName()).isNull();
            assertThat(parsed.profile().club()).isNull();
            assertThat(parsed.seedTimeMs()).isNull();
            assertThat(parser.isEmpty(headers.getSheet().createRow(2), columns)).isTrue();
            row.getCell(0).setCellValue("  ");
            assertThatThrownBy(() -> parser.parse(row, columns)).hasMessageContaining("фамилия");
        }
    }

    @Test
    void mapsSharedTimeParserBusinessExceptionToTimeColumn() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            String[] values = {"Иванов", "Иван", "", "10.05.2014", "М", "", "M-50-FREE", "00:60.00"};
            var row = sheet.createRow(1);
            for (int i = 0; i < 8; i++) {
                header.createCell(i).setCellValue(ExcelRowParser.HEADERS.get(i));
                row.createCell(i).setCellValue(values[i]);
            }
            assertThatThrownBy(() -> parser.parse(row, parser.headers(header)))
                    .isInstanceOfSatisfying(ImportRowException.class,
                            ex -> assertThat(ex.column()).isEqualTo(ExcelRowParser.HEADERS.get(7)))
                    .hasMessage("Неверное время: используйте сс.сс, мм:сс.сс или ч:мм:сс.сс");
        }
    }

    @Test
    void reportsAllMissingHeadersAndRejectsFormula() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Row headers = workbook.createSheet().createRow(0);
            headers.createCell(0).setCellValue("Фамилия");
            assertThatThrownBy(() -> parser.headers(headers)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Имя", "Дата рождения", "Пол", "Дисциплина");
            for (int i = 0; i < ExcelRowParser.HEADERS.size(); i++) headers.createCell(i).setCellValue(ExcelRowParser.HEADERS.get(i));
            Row row = headers.getSheet().createRow(1);
            row.createCell(0).setCellFormula("1+1");
            assertThatThrownBy(() -> parser.parse(row, parser.headers(headers))).hasMessageContaining("формулы");
        }
    }

    @Test
    void normalizesWhitespaceCaseAndYoInIdentityAndFormatsTime() {
        var profile = new ProfileData("  КИСЕЛЁВ\u00a0 Петров  ", " Иван   Семён ", null,
                LocalDate.of(2014, 5, 10), "M", null);
        assertThat(ProfileExternalKey.build(profile.lastName(), profile.firstName(), profile.birthDate(), profile.gender())).isEqualTo("киселев петров|иван семен|2014-05-10|m");
        assertThat(TimeParser.format(32150L)).isEqualTo("00:32.15");
        assertThat(TimeParser.format(3750500L)).isEqualTo("62:30.50");
        assertThat(TimeParser.format(null)).isNull();
    }
}
