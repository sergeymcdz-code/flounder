package com.acme.sportplatform.security;

import com.acme.sportplatform.analytics.api.EventAnalytics;
import com.acme.sportplatform.analytics.application.AnalyticsService;
import com.acme.sportplatform.analytics.infrastructure.AnalyticsReadRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reuses real RBAC fixtures and the committed 200-registration M dataset. */
class AnalyticsIntegrationTest extends Qa04SecurityFixture {
    @Autowired AnalyticsService analytics;
    @Autowired AnalyticsReadRepository reads;

    EventAnalytics readEvent() throws Exception {
        return json.readValue(mvc.perform(get("/api/v1/events/{id}/analytics",id("event"))
                .with(authentication(auth("organizer")))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(),EventAnalytics.class);
    }
    void owner() { jdbc.update("update organization_members set member_role='owner' where organization_id=? and user_id=?",id("org"),id("organizer")); }

    @Test
    void setMAggregatesMatchDirectSqlAndUncachedEndpointTakesUnder500Ms() throws Exception {
        setM();
        // Warm up query plans/serialization, but measure distinct uncached projections via repository.
        reads.event(id("event"));
        for(int run=0;run<3;run++) {
            long began=System.nanoTime();var a=reads.event(id("event"));long elapsed=(System.nanoTime()-began)/1_000_000;
            assertThat(elapsed).as("uncached M projection ms").isLessThanOrEqualTo(500);
            org.slf4j.LoggerFactory.getLogger(getClass()).info("M analytics uncached projection: {} ms",elapsed);
            assertSums(a);
        }
        long began=System.nanoTime();var a=readEvent();long elapsed=(System.nanoTime()-began)/1_000_000;
        assertThat(elapsed).as("uncached M HTTP request ms").isLessThanOrEqualTo(500);
        org.slf4j.LoggerFactory.getLogger(getClass()).info("M analytics uncached HTTP: {} ms",elapsed);
        assertSums(a);
        assertThat(a.sessions()).hasSize(2);
        assertThat(a.sessions()).allMatch(s -> s.delaySec()==null);
        assertThat(a.results().latencySamples()).isZero();
        assertThat(a.results().medianPublicationSec()).isNull();assertThat(a.results().p95PublicationSec()).isNull();
        assertThat(a.importStats().available()).isFalse();assertThat(a.importStats().recent()).isEmpty();
        assertThat(a.validator().errors()).isEqualTo(2);assertThat(a.validator().warnings()).isEqualTo(1);
        assertThat(a.results().publishedHeats()).isEqualTo(jdbc.queryForObject("select count(*) from competition_units u join event_disciplines d on d.id=u.event_discipline_id where d.event_id=? and u.status in ('FINISHED','COMPLETED') and d.status='PUBLISHED'",Long.class,id("event")));
        assertThat(a.results().corrected()).isEqualTo(2);
    }
    void assertSums(EventAnalytics a) {
        long total=jdbc.queryForObject("select count(*) from registrations where event_id=?",Long.class,id("event"));
        assertThat(total).isEqualTo(200);assertThat(a.registrations().total()).isEqualTo(total);
        assertThat(a.registrations().participants()).isEqualTo(jdbc.queryForObject("select count(distinct participant_profile_id) from registrations where event_id=?",Long.class,id("event")));
        assertThat(a.registrations().byStatus().stream().mapToLong(EventAnalytics.Count::count).sum()).isEqualTo(total);
        assertThat(a.registrations().byDay().stream().mapToLong(EventAnalytics.Day::count).sum()).isEqualTo(total);
        assertThat(a.byDiscipline().stream().mapToLong(EventAnalytics.Discipline::registrations).sum()).isEqualTo(total);
        for(var list:List.of(a.byAgeGroup(),a.byClub(),a.byGender()))
            assertThat(list.stream().mapToLong(EventAnalytics.Breakdown::registrations).sum()).isEqualTo(total);
        var f=a.registrations().funnel();
        assertThat(f.submitted()).isGreaterThanOrEqualTo(f.confirmed());assertThat(f.confirmed()).isGreaterThanOrEqualTo(f.paid());
        assertThat(f.paid()).isGreaterThanOrEqualTo(f.admitted());assertThat(f.admitted()).isGreaterThan(0);
    }

    @Test
    void cacheIsSharedWithExportButEveryReadRechecksAccess() throws Exception {
        var first=readEvent();
        jdbc.update("update registrations set status='WITHDRAWN' where id=?",id("registration"));
        assertThat(readEvent()).isEqualTo(first);
        for(String suffix:List.of("","/export.xlsx")) {
            mvc.perform(get("/api/v1/events/{id}/analytics"+suffix,id("event"))).andExpect(status().isUnauthorized());
            for(String actor:List.of("participant","judge","operator","foreign_organizer"))
                mvc.perform(get("/api/v1/events/{id}/analytics"+suffix,id("event"))
                        .with(authentication(auth(actor)))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/events/{id}/analytics"+suffix,id("event"))
                    .with(authentication(auth("platform_admin")))).andExpect(status().isOk());
        }
        jdbc.update("delete from organization_members where organization_id=? and user_id=?",id("org"),id("organizer"));
        jdbc.update("update events set created_by_user_id=? where id=?",id("foreign_organizer"),id("event"));em.clear();
        mvc.perform(get("/api/v1/events/{id}/analytics",id("event"))
                .with(authentication(auth("organizer")))).andExpect(status().isForbidden());
    }

    @Test
    void organizationRequiresOwnerOrAdminAndCountsRepeatProfilesAcrossEvents() throws Exception {
        setM();owner();
        UUID repeated=jdbc.queryForObject("select participant_profile_id from registrations where event_id=? limit 1",UUID.class,id("event"));
        jdbc.update("insert into events(id,organization_id,venue_id,sport_id,regulation_version_id,title,event_start_at,event_end_at,status,public_slug,created_by_user_id,created_at,updated_at) select ?,organization_id,venue_id,sport_id,regulation_version_id,title,event_start_at,event_end_at,'DRAFT',?,created_by_user_id,now(),now() from events where id=?",UUID.randomUUID(),"empty-"+UUID.randomUUID(),id("event"));
        // Move the pre-existing foreign event into the same organization and share one profile.
        jdbc.update("update events set organization_id=? where id=?",id("org"),id("foreignEvent"));
        jdbc.update("update registrations set participant_profile_id=? where id=?",repeated,id("foreignRegistration"));
        String path="/api/v1/organizations/"+id("org")+"/analytics";
        var a=json.readTree(mvc.perform(get(path).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(a.path("events").asInt()).isEqualTo(3);assertThat(a.path("repeatParticipants").asInt()).isEqualTo(1);
        long unique=jdbc.queryForObject("select count(distinct r.participant_profile_id) from registrations r join events e on e.id=r.event_id where e.organization_id=? and r.status not in ('DRAFT','REJECTED','WITHDRAWN')",Long.class,id("org"));
        assertThat(a.path("uniqueParticipants").asLong()).isEqualTo(unique);
        assertThat(a.path("participants").asLong()).isEqualTo(unique+1);
        assertThat(a.path("averageEventSize").asDouble()).isEqualTo((unique+1)/3.0);
        for(String suffix:List.of("","/export.xlsx")) {
            mvc.perform(get(path+suffix).with(authentication(auth("foreign_organizer")))).andExpect(status().isForbidden());
            mvc.perform(get(path+suffix).with(authentication(auth("operator")))).andExpect(status().isForbidden());
        }
        jdbc.update("update organization_members set member_role='organizer' where organization_id=? and user_id=?",id("org"),id("organizer"));
        mvc.perform(get(path).with(authentication(auth("organizer")))).andExpect(status().isForbidden());
        jdbc.update("update organization_members set member_role='admin' where organization_id=? and user_id=?",id("org"),id("organizer"));
        mvc.perform(get(path).with(authentication(auth("organizer")))).andExpect(status().isOk());
    }

    @Test
    void bothExportsOpenInPoiAndContainTheSameAggregatesAndUnavailableCells() throws Exception {
        setM();owner();var a=readEvent();
        var response=mvc.perform(get("/api/v1/events/{id}/analytics/export.xlsx",id("event"))
                .with(authentication(auth("organizer")))).andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("attachment;").contains("analytics.xlsx");
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(w.getNumberOfSheets()).isEqualTo(12);
            for(String sheet:List.of("Обзор","Статусы заявок","Заявки по дням","Воронка","Виды","Возрастные группы","Клубы","Пол","Сессии","Результаты","Валидатор","Импорты")) assertThat(w.getSheet(sheet)).isNotNull();
            assertThat(w.getSheet("Обзор").getRow(5).getCell(1).getNumericCellValue()).isEqualTo(a.registrations().total());
            assertThat(w.getSheet("Результаты").getRow(2).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
        }
        var org=mvc.perform(get("/api/v1/organizations/{id}/analytics/export.xlsx",id("org"))
                .with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn().getResponse();
        try(var w=new XSSFWorkbook(new ByteArrayInputStream(org.getContentAsByteArray()))) {
            assertThat(w.getNumberOfSheets()).isEqualTo(3);assertThat(w.getSheet("События по статусам")).isNotNull();assertThat(w.getSheet("Выручка")).isNotNull();
        }
    }

    @Test
    void optionalImmutableTimestampsGiveInterpolatedMedianAndP95WithoutUsingUpdatedAt() {
        // Test-only transactional DDL models the future producer; production has no migration.
        jdbc.execute("alter table competition_units add column actual_finished_at timestamptz, add column results_published_at timestamptz");
        for(int i=1;i<=20;i++) {
            UUID heat=i==1 ? id("unit") : UUID.randomUUID();
            if(i>1) jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status) values (?,?,'Synthetic',?,'FINISHED')",heat,id("discipline"),i);
            jdbc.update("update competition_units set actual_finished_at='2026-10-01T10:00:00Z',results_published_at='2026-10-01T10:00:00Z'::timestamptz+make_interval(secs=>?),updated_at=now() where id=?",i*10,heat);
        }
        var r=reads.results(id("event"));assertThat(r.latencySamples()).isEqualTo(20);
        assertThat(r.medianPublicationSec()).isEqualTo(105.0);assertThat(r.p95PublicationSec()).isEqualTo(190.5);
        jdbc.update("update competition_units set results_published_at=null where id=?",id("unit"));
        assertThat(reads.results(id("event")).latencySamples()).isEqualTo(19);
    }

    @Test
    void sessionDurationIncludesBreaksBetweenDisciplinesAndEmptySessionHasNoEnd() throws Exception {
        jdbc.update("update event_disciplines set session_id=? where id=?",id("session"),id("newDiscipline"));
        jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status) values (?,?,'Second',2,'DRAFT'),(?,?,'Other discipline',1,'DRAFT')",UUID.randomUUID(),id("discipline"),UUID.randomUUID(),id("newDiscipline"));
        jdbc.update("update competition_units set scheduled_at=null where event_discipline_id in (?,?)",id("discipline"),id("newDiscipline"));
        var a=readEvent();var s=a.sessions().getFirst();
        assertThat(s.heats()).isEqualTo(3);assertThat(s.estimatedDurationMin()).isEqualTo(5.5);
        var start=jdbc.queryForObject("select starts_at from event_sessions where id=?",OffsetDateTime.class,id("session"));
        assertThat(s.estimatedEndsAt().toInstant()).isEqualTo(start.plusSeconds(330).toInstant());
        jdbc.update("delete from results where competition_unit_entry_id=?",id("entry"));
        jdbc.update("delete from competition_unit_entries where id=?",id("entry"));
        jdbc.update("delete from competition_units where event_discipline_id in (?,?)",id("discipline"),id("newDiscipline"));
        var empty=reads.event(id("event")).sessions().getFirst();assertThat(empty.heats()).isZero();assertThat(empty.estimatedEndsAt()).isNull();
    }

    @Test
    void timezoneBucketsClubTailAndWithdrawnEntriesAreHandledWithoutInflatingCounts() throws Exception {
        jdbc.update("update venues set timezone='Europe/Samara' where id=?",id("venue"));
        jdbc.update("update events set registration_open_at='2026-10-01T00:00:00Z',registration_close_at='2026-10-05T00:00:00Z' where id=?",id("event"));
        jdbc.update("update registrations set payment_status='PAID',submitted_at='2026-10-01T21:30:00Z' where id=?",id("registration"));
        jdbc.update("update competition_unit_entries set entry_status='WITHDRAWN' where id=?",id("entry"));
        for(int i=0;i<22;i++) {
            UUID p=UUID.randomUUID();jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) values (?,'Test','Test','2014-03-05','M',?)",p,"Club-"+i);
            jdbc.update("insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,created_at,updated_at) values (?,?,?,?,'DRAFT','{}','2026-10-02T10:00:00Z',now())",UUID.randomUUID(),id("event"),id("discipline"),p);
        }
        var a=readEvent();assertThat(a.byClub()).hasSize(21);assertThat(a.byClub().getLast().key()).isEqualTo("__OTHER__");
        assertThat(a.byClub().getLast().registrations()).isEqualTo(3);assertThat(a.registrations().funnel().admitted()).isZero();
        assertThat(a.registrations().byDay()).anyMatch(d -> d.date().equals(LocalDate.of(2026,10,2)) && d.count()==23);
    }

    @Test
    void openAgeBoundsAndSucceededPaymentRevenueFollowExistingContracts() throws Exception {
        jdbc.update("update profiles set gender='male' where id=?",id("profile"));
        jdbc.update("update event_disciplines set age_groups_json='[{\"code\":\"OPEN\",\"name\":\"Открытая группа\",\"gender\":\"M\",\"birthYearFrom\":null,\"birthYearTo\":null}]' where id=?",id("discipline"));
        var event=readEvent();
        assertThat(event.byAgeGroup().getFirst().key()).isEqualTo("OPEN");
        assertThat(event.byGender().getFirst().key()).isEqualTo("M");
        owner();
        jdbc.update("update payments set status='SUCCEEDED',amount=250 where id=?",id("payment"));
        String path="/api/v1/organizations/"+id("org")+"/analytics";
        var paid=json.readTree(mvc.perform(get(path).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(paid.at("/revenue/0/currency").asText()).isEqualTo("RUB");
        assertThat(paid.at("/revenue/0/amount").asDouble()).isEqualTo(250);
        jdbc.update("update payments set status='REFUNDED' where id=?",id("payment"));
        var refunded=json.readTree(mvc.perform(get(path).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(refunded.path("revenue")).isEmpty();
    }

    @Test
    void emptyEventAndOrganizationReturnZerosRatherThanMissingData() throws Exception {
        String event="/api/v1/events/"+id("foreignEvent")+"/analytics";
        jdbc.update("delete from results where competition_unit_entry_id=?",id("foreignEntry"));
        jdbc.update("delete from competition_unit_entries where id=?",id("foreignEntry"));
        jdbc.update("delete from registrations where id=?",id("foreignRegistration"));
        var a=json.readTree(mvc.perform(get(event).with(authentication(auth("foreign_organizer")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(a.at("/registrations/total").asInt()).isZero();assertThat(a.at("/validator/runId").isNull()).isTrue();
        UUID empty=UUID.randomUUID();jdbc.update("insert into organizations(id,type,name,created_at,updated_at) values (?,'club','Empty',now(),now())",empty);
        var o=json.readTree(mvc.perform(get("/api/v1/organizations/{id}/analytics",empty).with(authentication(auth("platform_admin")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(o.path("events").asLong()).isZero();assertThat(o.path("averageEventSize").asDouble()).isZero();
    }

    void setM() throws Exception {
        jdbc.update("delete from payments where registration_id=?",id("registration"));
        jdbc.update("delete from results where competition_unit_entry_id=?",id("entry"));
        jdbc.update("delete from competition_unit_entries where id=?",id("entry"));
        jdbc.update("delete from competition_units where id=?",id("unit"));
        jdbc.update("delete from registrations where id=?",id("registration"));
        jdbc.update("delete from event_disciplines where id=?",id("newDiscipline"));
        jdbc.update("update events set registration_open_at=now()-interval '5 days' where id=?",id("event"));
        JsonNode dataset;try(var input=getClass().getResourceAsStream("/publication/set-M.json")) { dataset=json.readTree(input); }
        Map<Integer,UUID> profiles=new HashMap<>();
        for(var athlete:dataset.path("athletes")) {
            UUID p=UUID.randomUUID();profiles.put(athlete.path("id").asInt(),p);
            jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) values (?,?,?,?,?,?)",p,athlete.path("lastName").asText(),athlete.path("firstName").asText(),LocalDate.parse(athlete.path("birthDate").asText(),DateTimeFormatter.ofPattern("dd.MM.yyyy")),athlete.path("gender").asText().equals("М") ? "M" : "F",athlete.path("club").asText());
        }
        UUID afternoon=UUID.randomUUID();jdbc.update("insert into event_sessions(id,event_id,name,order_no,starts_at) values (?,?,'День',2,now()+interval '1 day 3 hours')",afternoon,id("event"));
        Map<String,UUID> disciplines=new LinkedHashMap<>();int index=0;
        for(String code:List.of("M-50-FREE","F-50-FREE","M-100-FREE","F-100-FREE")) {
            UUID d=index==0 ? id("discipline") : UUID.randomUUID();disciplines.put(code,d);
            if(index>0) jdbc.update("insert into event_disciplines(id,event_id,discipline_template_id,code,name,competition_format,unit_type,result_type,ranking_strategy,status,created_at,updated_at,distance_m,session_id) values (?,?,?, ?,?,'INDIVIDUAL','HEAT','TIME','ASC','PUBLISHED',now(),now(),?,?)",d,id("event"),id("template"),code,code,index<2 ? 50 : 100,index<2 ? id("session") : afternoon);
            String gender=code.substring(0,1);
            jdbc.update("update event_disciplines set age_groups_json=?::jsonb where id=?","[{\"code\":\""+gender+"-2010-2015\",\"name\":\"Группа\",\"gender\":\""+gender+"\",\"birthYearFrom\":2010,\"birthYearTo\":2015}]",d);
            index++;
        }
        Map<UUID,Integer> counts=new HashMap<>();Map<UUID,UUID> heats=new HashMap<>();int row=0;
        for(var entry:dataset.path("entries")) {
            UUID d=disciplines.get(entry.path("disciplineCode").asText());int n=counts.getOrDefault(d,0);
            if(n%8==0) {UUID h=UUID.randomUUID();heats.put(d,h);jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status,scheduled_at) values (?,?,'Заплыв',?,?,now()+interval '1 day')",h,d,n/8+1,n==0 ? "FINISHED" : "PUBLISHED");}
            UUID r=UUID.randomUUID();String status=row%5==0 ? "SUBMITTED" : "CONFIRMED";
            jdbc.update("insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,payment_status,submitted_at,created_at,updated_at) values (?,?,?,?,?,'{}',?,now()-make_interval(days=>?),now(),now())",r,id("event"),d,profiles.get(entry.path("athleteId").asInt()),status,row%3==0 ? "PENDING" : "PAID",row%5);
            UUID ce=UUID.randomUUID();jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position) values (?,?,?,?)",ce,heats.get(d),r,n%8+1);
            jdbc.update("insert into results(id,competition_unit_entry_id,result_type,status,time_ms) values (?,?,'TIME',?,30000)",UUID.randomUUID(),ce,row<2 ? "CORRECTED" : "VALID");
            counts.put(d,n+1);row++;
        }
        assertThat(row).isEqualTo(200);
        jdbc.update("insert into rule_execution_logs(id,event_id,kind,input_json,output_json,violations_json,duration_ms,executed_at) values (?,?,'VALIDATION','{}','{}','[{\"severity\":\"WARNING\"}]',1,now()-interval '1 hour'),(?,?,'VALIDATION','{}','{}','[{\"severity\":\"ERROR\"},{\"severity\":\"ERROR\"},{\"severity\":\"WARNING\"}]',1,now())",UUID.randomUUID(),id("event"),UUID.randomUUID(),id("event"));
    }
}
