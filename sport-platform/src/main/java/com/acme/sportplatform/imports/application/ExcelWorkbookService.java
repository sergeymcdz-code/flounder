package com.acme.sportplatform.imports.application;

import com.acme.sportplatform.common.TimeParser;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.ImportDisciplineLookup;
import com.acme.sportplatform.competition.ImportDisciplineLookup.Discipline;
import com.acme.sportplatform.imports.domain.ExcelRowParser;
import com.acme.sportplatform.registrations.ImportedRegistrationLookup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExcelWorkbookService {
    private final ImportDisciplineLookup disciplines;
    private final ImportedRegistrationLookup registrations;
    private final ObjectMapper mapper;

    public ExcelWorkbookService(ImportDisciplineLookup disciplines, ImportedRegistrationLookup registrations, ObjectMapper mapper) {
        this.disciplines = disciplines;
        this.registrations = registrations;
        this.mapper = mapper;
    }

    public byte[] template(UUID eventId) {
        List<Discipline> items = disciplines.findByEventId(eventId);
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Заявки");
            row(sheet, 0, ExcelRowParser.HEADERS);
            row(sheet, 1, List.of("Иванов", "Иван", "Иванович", "10.05.2014", "М", "Дельфин / Самара",
                    items.isEmpty() ? "M-50-FREE" : items.getFirst().code(), "00:32.15"));
            textColumns(sheet);
            Sheet instructions = workbook.createSheet("Инструкция");
            row(instructions, 0, List.of("Одна строка — одна заявка. Первый лист загружается при импорте."));
            row(instructions, 1, List.of("Удалите строку-пример. Используйте коды с листа «Коды дисциплин»."));
            row(instructions, 2, List.of("Повторяющиеся строки пропускаются; повторный импорт обновляет заявочное время."));
            row(instructions, 3, List.of("Формат даты: ДД.ММ.ГГГГ, ГГГГ-ММ-ДД или дата Excel. Пол: М/Ж или M/F."));
            row(instructions, 4, List.of("Максимум 5 МБ и 5 000 строк. Отчество, клуб и время необязательны."));
            Sheet codes = workbook.createSheet("Коды дисциплин");
            row(codes, 0, List.of("code", "name"));
            for (int i = 0; i < items.size(); i++) row(codes, i + 1, List.of(items.get(i).code(), items.get(i).name()));
            return bytes(workbook);
        } catch (java.io.IOException ex) {
            throw new BusinessException("imports.template_failed", "Не удалось создать шаблон Excel");
        }
    }

    public byte[] export(UUID eventId) {
        Map<UUID, Discipline> byId = disciplines.findByEventId(eventId).stream()
                .collect(Collectors.toMap(Discipline::id, Function.identity()));
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Заявки");
            var headers = new java.util.ArrayList<>(ExcelRowParser.HEADERS);
            headers.addAll(List.of("Статус", "Оплата"));
            row(sheet, 0, headers);
            int index = 1;
            for (var entry : registrations.findByEventId(eventId)) {
                JsonNode p = mapper.readTree(entry.participantSnapshot());
                String gender = p.path("gender").asText("");
                gender = switch (gender) {
                    case "M", "MALE" -> "М";
                    case "F", "FEMALE" -> "Ж";
                    default -> gender;
                };
                String birth = p.path("birthDate").asText("");
                if (!birth.isEmpty()) birth = LocalDate.parse(birth).format(DateTimeFormatter.ofPattern("dd.MM.uuuu"));
                Discipline discipline = byId.get(entry.disciplineId());
                row(sheet, index++, List.of(p.path("lastName").asText(""), p.path("firstName").asText(""),
                        p.path("middleName").asText(""), birth, gender, p.path("clubName").asText(""),
                        discipline == null ? "" : discipline.code(), TimeParser.format(entry.seedTimeMs()),
                        statusLabel(entry.status()), paymentLabel(entry.paymentStatus())));
            }
            textColumns(sheet);
            return bytes(workbook);
        } catch (java.io.IOException ex) {
            throw new BusinessException("imports.export_failed", "Не удалось выгрузить заявки в Excel");
        }
    }

    private static void row(Sheet sheet, int number, List<String> values) {
        Row row = sheet.createRow(number);
        for (int i = 0; i < values.size(); i++) {
            row.createCell(i).setCellValue(values.get(i));
        }
    }

    private static void textColumns(Sheet sheet) {
        var style = sheet.getWorkbook().createCellStyle();
        style.setDataFormat(sheet.getWorkbook().createDataFormat().getFormat("@"));
        for (int column : List.of(3, 7)) {
            sheet.setDefaultColumnStyle(column, style);
            for (Row row : sheet) {
                var cell = row.getCell(column);
                if (cell != null) cell.setCellStyle(style);
            }
        }
    }

    private static void formatSheets(XSSFWorkbook workbook) {
        var font = workbook.createFont();
        font.setBold(true);
        var headerStyle = workbook.createCellStyle();
        headerStyle.setFont(font);
        var textHeaderStyle = workbook.createCellStyle();
        textHeaderStyle.cloneStyleFrom(headerStyle);
        textHeaderStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
        var formatter = new DataFormatter();
        for (Sheet sheet : workbook) {
            Row header = sheet.getRow(0);
            for (var cell : header) {
                cell.setCellStyle("@".equals(cell.getCellStyle().getDataFormatString()) ? textHeaderStyle : headerStyle);
            }
            for (int column = 0; column < header.getLastCellNum(); column++) {
                int width = 18;
                for (Row row : sheet) {
                    width = Math.max(width, formatter.formatCellValue(row.getCell(column)).length() + 2);
                }
                sheet.setColumnWidth(column, Math.min(60, width) * 256);
            }
            sheet.createFreezePane(0, 1);
        }
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "DRAFT" -> "Черновик";
            case "SUBMITTED" -> "Подана";
            case "UNDER_REVIEW" -> "На рассмотрении";
            case "CONFIRMED" -> "Подтверждена";
            case "NEEDS_CORRECTION" -> "Требует уточнения";
            case "REJECTED" -> "Отклонена";
            case "PAID" -> "Оплачена";
            case "ADMITTED" -> "Допущена";
            case "WITHDRAWN" -> "Снята";
            default -> "Неизвестный статус";
        };
    }

    private static String paymentLabel(String status) {
        return switch (status) {
            case "NOT_REQUIRED" -> "Не требуется";
            case "PENDING" -> "Ожидает оплаты";
            case "PAID" -> "Оплачено";
            case "REFUNDED" -> "Возвращено";
            default -> "Неизвестный статус оплаты";
        };
    }

    private static byte[] bytes(XSSFWorkbook workbook) throws java.io.IOException {
        formatSheets(workbook);
        var output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }
}
