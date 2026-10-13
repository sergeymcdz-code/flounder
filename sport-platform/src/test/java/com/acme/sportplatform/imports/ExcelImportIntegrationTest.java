package com.acme.sportplatform.imports;

import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.imports.api.ImportReport;
import com.acme.sportplatform.imports.application.ImportRowValidator;
import com.acme.sportplatform.competition.ImportDisciplineLookup.Discipline;
import com.acme.sportplatform.imports.domain.ExcelRowParser;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ExcelImportIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final UUID ADMIN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SWIMMING = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final PlatformUserPrincipal PRINCIPAL = principal(ADMIN, "PLATFORM_ADMIN");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EventRepository events;
    @Autowired EventDisciplineRepository disciplines;
    @Autowired DisciplineTemplateRepository templates;
    @Autowired RegistrationRepository registrations;
    @Autowired ProfileRepository profiles;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoSpyBean ImportRowValidator validator;

    @Test
    void importsDemoMInTenSecondsAndReportsExactlyThreeErrorsOnReload() throws Exception {
        UUID event = event();
        byte[] file = demoFile("set-M.xlsx");
        long started = System.nanoTime();
        ImportReport first = upload(event, file);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(elapsedMs).isLessThanOrEqualTo(10000);
        assertThat(first.total()).isEqualTo(200);
        assertThat(first.created()).isEqualTo(200);
        assertThat(first.updated()).isZero();
        assertThat(first.skipped()).isZero();
        assertThat(first.createdProfiles()).isEqualTo(120);
        assertThat(first.errors()).isEmpty();
        var rows = registrations.findByEventId(event);
        assertThat(rows).hasSize(200).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo("CONFIRMED");
            assertThat(r.getSource()).isEqualTo("IMPORT");
            assertThat(r.getParticipantUserId()).isNull();
            assertThat(r.getPaymentStatus()).isIn("PENDING", "NOT_REQUIRED");
        });
        assertThat(rows.stream().filter(r -> r.getSeedTimeMs() == null)).hasSize(20);
        assertThat(rows.stream().filter(r -> r.getPaymentStatus().equals("PENDING"))).hasSize(53);
        ImportReport second = upload(event, demoFile("set-M-with-errors.xlsx"));
        assertThat(second.total()).isEqualTo(203);
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(200);
        assertThat(second.createdProfiles()).isZero();
        assertThat(second.errors()).extracting(ImportReport.RowError::row).containsExactly(202, 203, 204);
        assertThat(second.errors()).extracting(ImportReport.RowError::column)
                .containsExactly(ExcelRowParser.HEADERS.get(3), ExcelRowParser.HEADERS.get(6), "Фамилия");
        assertThat(registrations.findByEventId(event)).hasSize(200);
        ImportReport third = upload(event, file);
        assertThat(third.created()).isZero();
        assertThat(third.updated()).isEqualTo(200);
        assertThat(third.errors()).isEmpty();
        System.out.println("set-M.xlsx import duration: " + elapsedMs + " ms");
    }

    @Test
    void importsTemplateRowsSkipsDuplicateAndExportsAllColumns() throws Exception {
        UUID event = event();
        byte[] template = mvc.perform(get(base(event) + "/import-template").with(user(PRINCIPAL)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] file;
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetAt(2).getSheetName()).isEqualTo("Коды дисциплин");
            assertThat(workbook.getSheetAt(2).getLastRowNum()).isEqualTo(4);
            for (int i = 0; i < 8; i++) assertThat(workbook.getSheetAt(0).getRow(0).getCell(i).getStringCellValue())
                    .isEqualTo(ExcelRowParser.HEADERS.get(i));
            var sheet = workbook.getSheetAt(0);
            for (int column : List.of(3, 7)) {
                assertThat(sheet.getColumnStyle(column).getDataFormatString()).isEqualTo("@");
                assertThat(sheet.getRow(1).getCell(column).getCellStyle().getDataFormatString()).isEqualTo("@");
            }
            for (var tab : workbook) {
                for (var cell : tab.getRow(0)) {
                    assertThat(workbook.getFontAt(cell.getCellStyle().getFontIndex()).getBold()).isTrue();
                }
                assertThat(tab.getPaneInformation().isFreezePane()).isTrue();
                assertThat(tab.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
            }
            fill(sheet.createRow(1), "Проверкин", "Иван", "10.05.2014", "М", "M-50-FREE", "00:32.15");
            fill(sheet.createRow(2), "Проверкина", "Анна", "2014-05-10", "F", "F-50-FREE", "");
            fill(sheet.createRow(3), "Ошибкин", "Иван", "10.05.2014", "М", "X-999", "");
            fill(sheet.createRow(4), "Ошибкин", "Иван", "31.02.2014", "М", "M-50-FREE", "");
            fill(sheet.createRow(5), "  ПРОВЕРКИН ", " ИВАН ", "10.05.2014", "M", "M-50-FREE", "00:32.15");
            file = bytes(workbook);
        }
        ImportReport first = upload(event, file);
        assertThat(first.total()).isEqualTo(5);
        assertThat(first.created()).isEqualTo(2);
        assertThat(first.updated()).isZero();
        assertThat(first.skipped()).isEqualTo(1);
        assertThat(first.errors()).hasSize(2);
        var repeated = upload(event, file);
        assertThat(repeated.created()).isZero();
        assertThat(repeated.updated()).isEqualTo(2);
        assertThat(repeated.skipped()).isEqualTo(1);
        assertThat(repeated.errors()).hasSize(2);
        byte[] exported = mvc.perform(get(base(event) + "/export").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("registrations-import-test-" + event + ".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("Статус");
            assertThat(sheet.getRow(0).getCell(9).getStringCellValue()).isEqualTo("Оплата");
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("00:32.15");
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("Подтверждена");
            assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo("Не требуется");
            assertThat(workbook.getFontAt(sheet.getRow(0).getCell(8).getCellStyle().getFontIndex()).getBold()).isTrue();
        }
        var roundTrip = upload(event, exported);
        assertThat(roundTrip.updated()).isEqualTo(2);
        assertThat(roundTrip.errors()).isEmpty();
    }

    @Test
    void validatesGroupsPublicationAndRollsBackOnlyFailingRows() throws Exception {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(validator).validate(any(), nullable(Discipline.class));
        UUID event = event();
        var draft = disciplines.findByEventId(event).getFirst();
        draft.setStatus("DRAFT");
        disciplines.saveAndFlush(draft);
        String activeCode = "M-50-FREE".equals(draft.getCode()) ? "M-100-FREE" : "M-50-FREE";
        byte[] file = file(List.of(
                List.of("Контроль", "Иван", "10.05.2014", "М", activeCode, "32.15"),
                List.of("Контроль", "Анна", "10.05.2014", "Ж", activeCode, ""),
                List.of("Контроль", "Борис", "10.05.2001", "М", activeCode, ""),
                List.of("Контроль", "Олег", "10.05.2014", "М", draft.getCode(), ""),
                List.of("Контроль", "Лев", "10.05.2014", "М", activeCode, "00:60.00")));
        var report = upload(event, file);
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.createdProfiles()).isEqualTo(1);
        assertThat(report.errors()).hasSize(4);
        assertThat(report.errors().getFirst().message()).isEqualTo("не подходит под возрастные группы вида");
        assertThat(report.errors().get(1).message()).isEqualTo("не подходит под возрастные группы вида");
        assertThat(report.errors().get(2).message()).contains("не опубликована");
        assertThat(registrations.findByEventId(event)).hasSize(1);
        verify(validator, times(4)).validate(any(), any());
    }

    @Test
    void updatesTimeKeepsPaymentAndCreatesNewRegistrationAfterWithdrawal() throws Exception {
        UUID event = event();
        byte[] initial = file(List.of(List.of("Киселёв", "Тест", "10.05.2014", "М", "M-100-FREE", "32.15")));
        assertThat(upload(event, initial).created()).isEqualTo(1);
        RegistrationEntity registration = registrations.findByEventId(event).getFirst();
        UUID oldId = registration.getId();
        registration.setPaymentStatus("PAID");
        registration.setStatus("PAID");
        registrations.saveAndFlush(registration);
        byte[] changed = file(List.of(List.of("  КИСЕЛЕВ ", "ТЕСТ", "10.05.2014", "M", "M-100-FREE", "30.50")));
        assertThat(upload(event, changed).updated()).isEqualTo(1);
        registration = registrations.findById(oldId).orElseThrow();
        assertThat(registration.getSeedTimeMs()).isEqualTo(30500);
        assertThat(registration.getPaymentStatus()).isEqualTo("PAID");
        assertThat(registration.getStatus()).isEqualTo("PAID");
        byte[] blankTime = file(List.of(List.of("Киселев", "Тест", "10.05.2014", "M", "M-100-FREE", "")));
        assertThat(upload(event, blankTime).updated()).isEqualTo(1);
        assertThat(registrations.findById(oldId).orElseThrow().getSeedTimeMs()).isEqualTo(30500);
        var profile = profiles.findById(registration.getParticipantProfileId()).orElseThrow();
        profile.setClubName(null);
        profiles.saveAndFlush(profile);
        upload(event, blankTime);
        assertThat(profiles.findById(profile.getId()).orElseThrow().getClubName()).isEqualTo("Дельфин");
        registration.setStatus("WITHDRAWN");
        registrations.saveAndFlush(registration);
        assertThat(upload(event, changed).created()).isEqualTo(1);
        assertThat(registrations.findByEventId(event)).hasSize(2);
        byte[] export = mvc.perform(get(base(event) + "/export").with(user(PRINCIPAL)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(8).getStringCellValue()).isEqualTo("Снята");
        }
    }

    @Test
    void rejectsNumericTimesAndInvalidBirthDateBoundsPerRow() throws Exception {
        UUID event = event();
        byte[] file = file(List.of(
                List.of("Даты", "Валид", "10.05.2014", "М", "M-50-FREE", "32.15"),
                List.of("Даты", "Будущее", LocalDate.now().plusDays(1).toString(), "М", "M-50-FREE", ""),
                List.of("Даты", "Прошлое", "31.12.1899", "М", "M-50-FREE", ""),
                List.of("Даты", "Пусто", "", "М", "M-50-FREE", ""),
                List.of("Время", "Число", "10.05.2014", "М", "M-50-FREE", "")));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            workbook.getSheetAt(0).getRow(5).getCell(7).setCellValue(32.15);
            file = bytes(workbook);
        }
        var report = upload(event, file);
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.createdProfiles()).isEqualTo(1);
        assertThat(report.errors()).extracting(ImportReport.RowError::row).containsExactly(3, 4, 5, 6);
        assertThat(report.errors()).extracting(ImportReport.RowError::message).containsExactly(
                "дата рождения не может быть в будущем", "дата рождения не может быть раньше 1900 года",
                "дата рождения не заполнена", "введите время текстом мм:сс.сс");
        assertThat(report.errors()).extracting(ImportReport.RowError::column).containsExactly(
                ExcelRowParser.HEADERS.get(3), ExcelRowParser.HEADERS.get(3), ExcelRowParser.HEADERS.get(3),
                ExcelRowParser.HEADERS.get(7));
        assertThat(registrations.findByEventId(event)).hasSize(1);
    }

    @Test
    void rejectsInvalidFilesAndEnforcesAccessForAllEndpoints() throws Exception {
        UUID event = event();
        byte[] valid = file(List.of(List.of("Доступ", "Тест", "10.05.2014", "М", "M-50-FREE", "")));
        var other = principal(UUID.randomUUID(), "ORGANIZER");
        for (String endpoint : List.of("/import-template", "/export")) {
            mvc.perform(get(base(event) + endpoint)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Для доступа требуется авторизация"));
            mvc.perform(get(base(event) + endpoint).with(user(other))).andExpect(status().isForbidden());
            mvc.perform(get(base(event) + endpoint).with(user(principal(ADMIN, "PARTICIPANT"))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Доступ запрещён"));
            mvc.perform(get(base(event) + endpoint).with(user(principal(ADMIN, "ORGANIZER"))))
                    .andExpect(status().isOk());
            mvc.perform(get(base(event) + endpoint).with(user(principal(ADMIN, "OPERATOR"))))
                    .andExpect(status().isOk());
        }
        mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", valid)).with(user(other)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", valid))
                .with(user(principal(ADMIN, "PARTICIPANT")))).andExpect(status().isForbidden());
        mvc.perform(multipart(base(event) + "/import").file(part("rows.xls", valid)).with(user(PRINCIPAL)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("imports.invalid_file_type"));
        mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", new byte[5 * 1024 * 1024 + 1])).with(user(PRINCIPAL)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.message").value("Размер файла превышает 5 МБ"));
        mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", new byte[0])).with(user(PRINCIPAL)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", "broken".getBytes())).with(user(PRINCIPAL)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("imports.invalid_file"));
        mvc.perform(multipart(base(event) + "/import").with(user(PRINCIPAL)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Не передан файл в поле file"));
        mvc.perform(get(base(UUID.randomUUID()) + "/export").with(user(PRINCIPAL)))
                .andExpect(status().isNotFound());
        assertThat(registrations.findByEventId(event)).isEmpty();
    }

    @Test
    void rejectsMissingHeadersAndRowLimitBeforeWritingAnything() throws Exception {
        UUID event = event();
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Фамилия");
            mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", bytes(workbook))).with(user(PRINCIPAL)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("imports.missing_columns"))
                    .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Дата рождения")));
            for (int i = 0; i < 8; i++) sheet.getRow(0).createCell(i).setCellValue(ExcelRowParser.HEADERS.get(i));
            for (int i = 1; i <= 5001; i++) fill(sheet.createRow(i), "Лимит", "Иван", "10.05.2014", "М", "M-50-FREE", "");
            mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", bytes(workbook))).with(user(PRINCIPAL)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("imports.too_many_rows"));
        }
        assertThat(registrations.findByEventId(event)).isEmpty();
    }

    @Test
    void databaseFailureRollsBackProfileAndContinuesWithNextRow() throws Exception {
        UUID event = event();
        jdbc.execute("alter table registrations add constraint test_import_failure check (participant_snapshot->>'firstName' <> 'Сбой')");
        try {
            byte[] file = file(List.of(
                    List.of("Откат", "Сбой", "10.05.2014", "М", "M-50-FREE", "32.15"),
                    List.of("Успех", "Продолжение", "10.05.2014", "М", "M-50-FREE", "32.15")));
            ImportReport report = upload(event, file);
            assertThat(report.created()).isEqualTo(1);
            assertThat(report.createdProfiles()).isEqualTo(1);
            assertThat(report.errors()).extracting(ImportReport.RowError::row).containsExactly(2);
            assertThat(profiles.findByExternalKey("откат|сбой|2014-05-10|m")).isEmpty();
            assertThat(registrations.findByEventId(event)).hasSize(1);
        } finally {
            jdbc.execute("alter table registrations drop constraint test_import_failure");
        }
    }

    @Test
    void concurrentImportsShareProfileAndRegistration() throws Exception {
        UUID event = event();
        byte[] file = file(List.of(List.of("Параллелев", "Тест", "10.05.2014", "М", "M-50-FREE", "32.15")));
        var a = CompletableFuture.supplyAsync(() -> uncheckedUpload(event, file));
        var b = CompletableFuture.supplyAsync(() -> uncheckedUpload(event, file));
        var first = a.get();
        var second = b.get();
        assertThat(first.created() + second.created()).isEqualTo(1);
        assertThat(first.updated() + second.updated()).isEqualTo(1);
        assertThat(first.createdProfiles() + second.createdProfiles()).isEqualTo(1);
        assertThat(first.errors()).isEmpty();
        assertThat(second.errors()).isEmpty();
        assertThat(registrations.findByEventId(event)).hasSize(1);
    }

    @Test
    void documentsMultipartAndReportExampleInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs").with(user(PRINCIPAL))).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventId}/registrations/import'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventId}/registrations/import'].post.responses['200'].content['application/json'].example.total").value(200));
    }

    private ImportReport uncheckedUpload(UUID event, byte[] bytes) {
        try { return upload(event, bytes); } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private ImportReport upload(UUID event, byte[] bytes) throws Exception {
        String result = mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", bytes)).with(user(PRINCIPAL)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readValue(result, ImportReport.class);
    }

    private static MockMultipartFile part(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static byte[] demoFile(String name) throws Exception {
        try (var stream = new ClassPathResource("demo/" + name).getInputStream()) {
            return stream.readAllBytes();
        }
    }

    private static String base(UUID event) { return "/api/v1/events/" + event + "/registrations"; }

    private static PlatformUserPrincipal principal(UUID id, String role) {
        return new PlatformUserPrincipal(id, "test@example.com", "", "active", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static byte[] file(List<List<String>> rows) throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Заявки");
            var header = sheet.createRow(0);
            for (int i = 0; i < 8; i++) header.createCell(i).setCellValue(ExcelRowParser.HEADERS.get(i));
            int index = 1;
            for (var values : rows) fill(sheet.createRow(index++), values.get(0), values.get(1), values.get(2), values.get(3), values.get(4), values.get(5));
            return bytes(workbook);
        }
    }

    private static void fill(Row row, String last, String first, String birth, String gender, String code, String time) {
        String[] values = {last, first, "", birth, gender, "Дельфин", code, time};
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private static byte[] bytes(XSSFWorkbook workbook) throws Exception {
        var stream = new ByteArrayOutputStream();
        workbook.write(stream);
        return stream.toByteArray();
    }

    private UUID event() {
        OffsetDateTime now = OffsetDateTime.now();
        EventEntity event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(ADMIN);
        event.setOrganizationId(UUID.randomUUID());
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SWIMMING);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Excel import test");
        event.setEventStartAt(now.plusDays(1));
        event.setEventEndAt(now.plusDays(2));
        event.setStatus("REGISTRATION_OPEN");
        event.setPublicSlug("import-test-" + event.getId());
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        events.saveAndFlush(event);
        DisciplineTemplateEntity template = new DisciplineTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setSportId(SWIMMING);
        template.setCode("import-" + UUID.randomUUID());
        template.setName("Swimming");
        template.setCompetitionFormat("INDIVIDUAL");
        template.setUnitType("HEAT");
        template.setResultType("TIME");
        template.setRankingStrategy("ASC");
        template.setDefaultMeta("{}");
        templates.saveAndFlush(template);
        for (String code : List.of("M-50-FREE", "F-50-FREE", "M-100-FREE", "F-100-FREE")) {
            EventDisciplineEntity d = new EventDisciplineEntity();
            d.setId(UUID.randomUUID());
            d.setEventId(event.getId());
            d.setDisciplineTemplateId(template.getId());
            d.setCode(code);
            d.setName(code);
            d.setCompetitionFormat("INDIVIDUAL");
            d.setUnitType("HEAT");
            d.setResultType("TIME");
            d.setRankingStrategy("ASC");
            d.setStatus("PUBLISHED");
            d.setGender(code.substring(0, 1));
            d.setAgeGroupsJson("""
                    [{"code":"young","gender":"X","birthYearFrom":2014,"birthYearTo":2015},
                     {"code":"middle","gender":"X","birthYearFrom":2012,"birthYearTo":2013},
                     {"code":"old","gender":"X","birthYearFrom":2010,"birthYearTo":2011}]
                    """);
            d.setEntryFeeAmount(code.equals("M-100-FREE") ? new BigDecimal("100.00") : BigDecimal.ZERO);
            d.setSettingsJson("{}");
            d.setCreatedAt(now);
            d.setUpdatedAt(now);
            disciplines.saveAndFlush(d);
        }
        return event.getId();
    }
}
