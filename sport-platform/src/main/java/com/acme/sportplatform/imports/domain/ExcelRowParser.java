package com.acme.sportplatform.imports.domain;

import com.acme.sportplatform.common.TimeParser;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.ImportedProfileRequest;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExcelRowParser {
    public static final List<String> HEADERS = List.of("Фамилия", "Имя", "Отчество",
            "Дата рождения (ДД.ММ.ГГГГ)", "Пол (М/Ж)", "Клуб/Город", "Дисциплина (код)",
            "Заявочное время (мм:сс.сс или пусто)");
    private static final List<Integer> REQUIRED = List.of(0, 1, 3, 4, 6);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public Map<Integer, Integer> headers(Row row) {
        Map<Integer, Integer> result = new HashMap<>();
        if (row != null) {
            for (Cell cell : row) {
                String text = normalizeHeader(formatter.formatCellValue(cell));
                for (int i = 0; i < HEADERS.size(); i++) {
                    if (normalizeHeader(HEADERS.get(i)).equals(text)) {
                        if (result.putIfAbsent(i, cell.getColumnIndex()) != null) {
                            throw new BusinessException("imports.duplicate_column", "Колонка повторяется: " + HEADERS.get(i));
                        }
                    }
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (int i : REQUIRED) if (!result.containsKey(i)) missing.add(HEADERS.get(i));
        if (!missing.isEmpty()) {
            throw new BusinessException("imports.missing_columns", "Отсутствуют обязательные колонки: " + String.join(", ", missing));
        }
        return result;
    }

    public boolean isEmpty(Row row, Map<Integer, Integer> columns) {
        if (row == null) return true;
        return columns.values().stream().allMatch(i -> formatter.formatCellValue(row.getCell(i)).isBlank());
    }

    public ImportRow parse(Row row, Map<Integer, Integer> columns) {
        String lastName = text(row, columns, 0, 128);
        String firstName = text(row, columns, 1, 128);
        if (lastName.isEmpty()) throw error(0, "фамилия не заполнена");
        if (firstName.isEmpty()) throw error(1, "имя не заполнено");
        String middleName = text(row, columns, 2, 128);
        LocalDate birthDate = birthDate(cell(row, columns, 3));
        String gender = gender(text(row, columns, 4, 16));
        String club = text(row, columns, 5, 255);
        String code = text(row, columns, 6, 64);
        if (code.isEmpty()) throw error(6, "код дисциплины не заполнен");
        Cell timeCell = cell(row, columns, 7);
        if (timeCell != null && timeCell.getCellType() == CellType.NUMERIC) {
            throw error(7, "введите время текстом мм:сс.сс");
        }
        String time = text(row, columns, 7, 32);
        Long seedTimeMs = null;
        if (!time.isEmpty()) {
            try { seedTimeMs = TimeParser.parse(time); }
            catch (IllegalArgumentException ex) { throw error(7, ex.getMessage()); }
        }
        ImportedProfileRequest profile = new ImportedProfileRequest(lastName, firstName, nullable(middleName),
                birthDate, gender, nullable(club));
        if (profile.externalKey().length() > 255) throw error(0, "фамилия и имя слишком длинные для ключа участника");
        return new ImportRow(profile, code, seedTimeMs);
    }

    public LocalDate birthDate(Cell cell) {
        String value = formatted(cell, 3);
        if (value.isEmpty()) throw error(3, "дата рождения не заполнена");
        try {
            LocalDate date;
            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                    && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                date = cell.getLocalDateTimeCellValue().toLocalDate();
            } else if (value.matches("[0-9]{2}\\.[0-9]{2}\\.[0-9]{4}")) {
                date = LocalDate.parse(value, DATE);
            } else if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                date = LocalDate.parse(value);
            } else {
                throw error(3, "неверный формат, ожидается ДД.ММ.ГГГГ");
            }
            if (date.getYear() < 1900) throw error(3, "дата рождения не может быть раньше 1900 года");
            if (date.isAfter(LocalDate.now())) throw error(3, "дата рождения не может быть в будущем");
            return date;
        } catch (java.time.DateTimeException | IllegalStateException ex) {
            throw error(3, "неверный формат, ожидается ДД.ММ.ГГГГ");
        }
    }

    public String gender(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "М", "M" -> "M";
            case "Ж", "F" -> "F";
            default -> throw error(4, "неверный пол, ожидается М/Ж или M/F");
        };
    }

    private String text(Row row, Map<Integer, Integer> columns, int index, int maxLength) {
        String value = formatted(cell(row, columns, index), index);
        if (value.length() > maxLength) throw error(index, "значение слишком длинное, максимум " + maxLength + " символов");
        return value;
    }

    private String formatted(Cell cell, int index) {
        if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)) {
            throw error(index, "формулы и ошибки Excel не поддерживаются, укажите значение");
        }
        return formatter.formatCellValue(cell).replaceAll("(?U)\\s+", " ").strip();
    }

    private Cell cell(Row row, Map<Integer, Integer> columns, int index) {
        return columns.containsKey(index) ? row.getCell(columns.get(index)) : null;
    }

    private static String nullable(String value) { return value.isEmpty() ? null : value; }
    private static String normalizeHeader(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("(?U)\\s+", "");
    }
    private static ImportRowException error(int index, String message) {
        return new ImportRowException(HEADERS.get(index), message);
    }
}
