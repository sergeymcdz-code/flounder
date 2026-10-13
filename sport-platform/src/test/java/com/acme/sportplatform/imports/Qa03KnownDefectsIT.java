package com.acme.sportplatform.imports;

import com.acme.sportplatform.competition.AgeGroup;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.ProfileData;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.imports.api.ImportReport;
import com.acme.sportplatform.imports.domain.ExcelRowParser;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ожидаемо RED до задачи объединения профилей аккаунта и импорта (QA03-04).
 * Запуск: {@code mvn -q test -Dtest=Qa03KnownDefectsIT}.
 */
@AutoConfigureMockMvc
@Tag("known-defect")
class Qa03KnownDefectsIT extends AbstractPostgresIntegrationTest {
    private static final UUID ADMIN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SWIMMING = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final PlatformUserPrincipal PRINCIPAL = principal(ADMIN, "PLATFORM_ADMIN");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EventRepository events;
    @Autowired EventDisciplineRepository disciplines;
    @Autowired DisciplineTemplateRepository templates;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void qa03_04PublicFormCanUseImportedAthleteWhenAccountAlreadyHasProfile() throws Exception {
        UUID event = event();
        var data = new ProfileData("Импортов", "Иван", null, LocalDate.of(2014, 5, 10), "M", "Дельфин");
        assertThat(upload(event, file(List.of(List.of(data.lastName(), data.firstName(),
                "10.05.2014", "M", "M-50-FREE", "32.15")))).created()).isEqualTo(1);
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into users (id,email,password_hash,status) values (?,?,'test','active')",
                userId, "qa03-import-" + userId + "@example.com");
        jdbc.update("insert into profiles (id,user_id,first_name,last_name,birth_date,gender) values (?,?,'Родитель','Демо','1990-01-01','M')",
                UUID.randomUUID(), userId);
        UUID secondDiscipline = disciplines.findByEventId(event).stream()
                .filter(d -> d.getCode().equals("M-100-FREE")).findFirst().orElseThrow().getId();
        var request = new com.acme.sportplatform.registrations.api.PublicRegistrationRequest(data,
                List.of(new com.acme.sportplatform.registrations.api.PublicRegistrationRequest.Item(secondDiscipline, 65000L)));
        mvc.perform(post("/api/v1/public/events/" + events.findById(event).orElseThrow().getPublicSlug() + "/registrations")
                .with(user(principal(userId, "PARTICIPANT"))).contentType("application/json")
                .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }

    private ImportReport upload(UUID event, byte[] bytes) throws Exception {
        String result = mvc.perform(multipart(base(event) + "/import").file(part("rows.xlsx", bytes)).with(user(PRINCIPAL)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readValue(result, ImportReport.class);
    }

    private static MockMultipartFile part(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
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
            d.setAgeGroups(List.of(
                    new AgeGroup("young", "Младшие", "X", 2014, 2015),
                    new AgeGroup("middle", "Средние", "X", 2012, 2013),
                    new AgeGroup("old", "Старшие", "X", 2010, 2011)));
            d.setEntryFeeAmount(code.equals("M-100-FREE") ? new BigDecimal("100.00") : BigDecimal.ZERO);
            d.setSettingsJson("{}");
            d.setCreatedAt(now);
            d.setUpdatedAt(now);
            disciplines.saveAndFlush(d);
        }
        return event.getId();
    }
}
