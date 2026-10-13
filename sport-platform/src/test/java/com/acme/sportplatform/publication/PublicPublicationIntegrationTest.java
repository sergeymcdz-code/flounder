package com.acme.sportplatform.publication;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"APP_CORS_ALLOWED_ORIGINS=https://spectators.example,http://localhost:5173",
        "spring.jpa.properties.hibernate.generate_statistics=true", "spring.jpa.show-sql=true"})
class PublicPublicationIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean(name = "publicationClock") Clock clock;
    @Autowired EntityManagerFactory emf;
    @Autowired @Qualifier("publicationCacheManager") CacheManager caches;
    UUID eventId, sessionId, session2Id, disciplineId, discipline2Id, venueId, templateId;
    UUID athleteId, secondAthleteId, ownerId, draftId;
    List<UUID> heats;
    String slug;
    OffsetDateTime start;
    record Principal(UUID userId, List<GrantedAuthority> authorities) implements AuthenticatedUserPrincipal {
        @Override public UUID getUserId() { return userId; }
        @Override public List<GrantedAuthority> getAuthorities() { return authorities; }
    }

    @BeforeEach
    void fixture() {
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
        eventId = UUID.randomUUID(); sessionId = UUID.randomUUID(); session2Id = UUID.randomUUID();
        disciplineId = UUID.randomUUID(); discipline2Id = UUID.randomUUID(); venueId = UUID.randomUUID();
        templateId = UUID.randomUUID(); athleteId = UUID.randomUUID(); secondAthleteId = UUID.randomUUID();
        ownerId = UUID.randomUUID(); draftId = UUID.randomUUID();
        heats = List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        slug = "public-it-" + eventId;
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(now.toInstant());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        start = now.plusDays(1).withNano(0);
        jdbc.update("insert into venues(id,name,city,timezone,venue_meta) values (?, 'Бассейн','Самара','Europe/Samara', '{\"lanes\":8,\"courseLength\":25}')",venueId);
        jdbc.update("""
                insert into events(id,organization_id,venue_id,sport_id,regulation_version_id,title,description,
                    registration_open_at,registration_close_at,event_start_at,event_end_at,status,public_slug,
                    created_by_user_id,created_at,updated_at)
                values (?, ?, ?, '10000000-0000-0000-0000-000000000001', ?, 'Кубок Самары', 'Описание',
                    now()-interval '1 day',now()+interval '1 day',?,?,'REGISTRATION_OPEN',?,?,now(),now())
                """,eventId,UUID.randomUUID(),venueId,UUID.randomUUID(),start,start.plusHours(6),slug,ownerId);
        jdbc.update("""
                insert into discipline_templates(id,sport_id,code,name,competition_format,unit_type,result_type,ranking_strategy)
                values (?, '10000000-0000-0000-0000-000000000001', ?, 'Плавание','INDIVIDUAL','HEAT','TIME','ASC')
                """,templateId,"template-"+templateId);
        jdbc.update("insert into event_sessions(id,event_id,name,order_no,starts_at) values (?,?,'Утро',1,?),(?,?,'День',2,?)",
                sessionId,eventId,start,session2Id,eventId,start.plusHours(3));
        discipline(disciplineId,sessionId,1,"50 м", "BY_AGE_GROUP");
        discipline(discipline2Id,sessionId,2,"100 м", "BY_TIME_ALL");
        for (int i=0;i<4;i++) heat(heats.get(i),i==3 ? discipline2Id : disciplineId,i==3 ? 1 : i+1,"PUBLISHED",start.plusSeconds(i*90L));
        heat(draftId,disciplineId,4,"DRAFT",start.plusHours(1));
        profile(athleteId,"Иванов","Иван"); profile(secondAthleteId,"Ёлкин","Пётр");
        UUID a = entry(heats.getFirst(),disciplineId,athleteId,4,false);
        UUID b = entry(heats.getFirst(),disciplineId,secondAthleteId,5,true);
        entry(heats.get(3),discipline2Id,athleteId,4,false);
        result(a,32150L,"VALID",7,2);
        result(b,null,"DID_NOT_START",99,99);
        UUID hiddenProfile = UUID.randomUUID(); profile(hiddenProfile,"Ивановский","Скрытый");
        result(entry(draftId,disciplineId,hiddenProfile,1,false),30000L,"VALID",1,1);
        UUID pendingProfile = UUID.randomUUID(); profile(pendingProfile,"Иванченко","Ожидающий");
        result(entry(heats.get(1),disciplineId,pendingProfile,2,false),29000L,"PENDING",null,null);
    }
    private void discipline(UUID id,UUID session,int order,String name,String mode) {
        jdbc.update("""
                insert into event_disciplines(id,event_id,discipline_template_id,code,name,competition_format,unit_type,
                    result_type,ranking_strategy,status,created_at,updated_at,distance_m,stroke,gender,session_id,
                    order_in_session,age_groups_json,seeding_mode)
                values (?,?,?, ?,?,'INDIVIDUAL','HEAT','TIME','ASC','PUBLISHED',now(),now(),50,'FREE','M',?,?,
                    '[{"code":"M-2014","name":"Юноши 2014","gender":"M","birthYearFrom":2014,"birthYearTo":2014}]',?)
                """,id,eventId,templateId,"code-"+id,name,session,order,mode);
    }
    private void heat(UUID id,UUID discipline,int number,String status,OffsetDateTime time) {
        jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status,scheduled_at,age_group_code) values (?,?,'Заплыв',?,?,?,'M-2014')",
                id,discipline,number,status,time);
    }
    private void profile(UUID id,String lastName,String firstName) {
        jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) values (?,?,?,'2014-03-05','M','Волна')",id,lastName,firstName);
    }
    private UUID entry(UUID heat,UUID discipline,UUID profile,int lane,boolean withdrawn) {
        UUID registration = UUID.randomUUID(), entry = UUID.randomUUID();
        jdbc.update("""
                insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,
                    created_at,updated_at,seed_time_ms,source) values (?,?,?,?,'CONFIRMED','{}',now(),now(),33000,'IMPORT')
                """,registration,eventId,discipline,profile);
        jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position,seed_time_ms,entry_status) values (?,?,?,?,32150,?)",
                entry,heat,registration,lane,withdrawn ? "WITHDRAWN" : "ASSIGNED");
        return entry;
    }
    private void result(UUID entry,Long time,String status,Integer place,Integer groupPlace) {
        jdbc.update("insert into results(id,competition_unit_entry_id,result_type,status,time_ms,final_place,place_in_age_group) values (?,?,'TIME',?,?,?,?)",
                UUID.randomUUID(),entry,status,time,place,groupPlace);
    }
    private String url(String suffix) { return "/api/v1/public/events/"+slug+suffix; }
    private JsonNode read(String suffix) throws Exception {
        return mapper.readTree(mvc.perform(get(url(suffix))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private UsernamePasswordAuthenticationToken auth(UUID id,String role) {
        var authorities=List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_"+role));
        return new UsernamePasswordAuthenticationToken(new Principal(id,authorities),null,authorities);
    }

    private void clearPublicCaches() {
        // Simulate expiry of the public TTL after mutations handled by T03.
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
    }
    private void startHeat(UUID unitId) throws Exception {
        mvc.perform(post("/api/v1/competition-units/{id}/start",unitId)
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isOk());
        clearPublicCaches();
    }
    private void finishHeat(UUID unitId) throws Exception {
        startHeat(unitId);
        mvc.perform(post("/api/v1/competition-units/{id}/finish",unitId)
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isOk());
        clearPublicCaches();
    }

    @Test
    void eventCardHasVenueSessionsDisciplinesAndRegistrationFlag() throws Exception {
        JsonNode response = read("");
        assertThat(response.path("venue").path("timezone").asText()).isEqualTo("Europe/Samara");
        assertThat(response.path("venue").path("lanes").asInt()).isEqualTo(8);
        assertThat(response.path("venue").path("courseLength").asInt()).isEqualTo(25);
        assertThat(response.path("venue").path("name").asText()).isEqualTo("Бассейн");
        assertThat(response.path("sessions")).hasSize(2);
        assertThat(response.path("registrationOpen").asBoolean()).isTrue();
        assertThat(response.path("disciplines")).hasSize(2);
        assertThat(response.at("/disciplines/0/number").asInt()).isEqualTo(1);
        assertThat(response.at("/disciplines/0/sessionId").asText()).isEqualTo(sessionId.toString());
        assertThat(response.at("/disciplines/0/unitsPublished").asBoolean()).isTrue();
        jdbc.update("update events set status='REGISTRATION_CLOSED' where id=?",eventId);
        caches.getCache("publicEvent").clear();
        assertThat(read("").path("registrationOpen").asBoolean()).isFalse();
    }

    @Test
    void registrationOpenRequiresStatusAndRegistrationWindowWithNullableBounds() throws Exception {
        var now=OffsetDateTime.now(clock);
        jdbc.update("update events set registration_open_at=?,registration_close_at=? where id=?",now.plusSeconds(1),now.plusDays(1),eventId);
        assertThat(read("").path("registrationOpen").asBoolean()).isFalse();
        jdbc.update("update events set registration_open_at=null,registration_close_at=? where id=?",now.minusSeconds(1),eventId);
        caches.getCache("publicEvent").clear(); assertThat(read("").path("registrationOpen").asBoolean()).isFalse();
        // PostgreSQL stores microseconds, so truncate the clock to test inclusive boundaries exactly.
        var boundary=now.withNano((now.getNano()/1000)*1000);
        when(clock.instant()).thenReturn(boundary.toInstant());
        jdbc.update("update events set registration_open_at=?,registration_close_at=? where id=?",boundary,boundary,eventId);
        caches.getCache("publicEvent").clear(); assertThat(read("").path("registrationOpen").asBoolean()).isTrue();
        jdbc.update("update events set registration_open_at=null,registration_close_at=null where id=?",eventId);
        caches.getCache("publicEvent").clear(); assertThat(read("").path("registrationOpen").asBoolean()).isTrue();
        jdbc.update("update events set status='PUBLISHED' where id=?",eventId);
        caches.getCache("publicEvent").clear(); assertThat(read("").path("registrationOpen").asBoolean()).isFalse();
    }

    @Test
    void unassignedDisciplinesHaveStableStringSessionId() throws Exception {
        jdbc.update("update event_disciplines set session_id=null where id=?",discipline2Id);
        var response=read("/start-list");
        assertThat(response.path("sessions")).hasSize(2);
        assertThat(response.at("/sessions/0/id").asText()).isEqualTo(sessionId.toString());
        assertThat(response.at("/sessions/1/id").asText()).isEqualTo("unassigned");
        assertThat(response.at("/sessions/1/disciplines/0/id").asText()).isEqualTo(discipline2Id.toString());
    }

    @Test
    void startListIncludesOnlyPublishedHeatsAllLanesAndWithdrawnAthletesWithoutNPlusOne() throws Exception {
        var statistics = emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        JsonNode response = read("/start-list");
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(10);
        JsonNode disciplines = response.at("/sessions/0/disciplines");
        assertThat(disciplines).hasSize(2);
        assertThat(disciplines.at("/0/heats")).hasSize(3);
        JsonNode lanes = disciplines.at("/0/heats/0/lanes");
        assertThat(lanes).hasSize(8);
        assertThat(lanes.get(0)).isEqualTo(mapper.readTree("{\"lane\":1,\"empty\":true}"));
        assertThat(lanes.at("/3/athlete/lastName").asText()).isEqualTo("Иванов");
        assertThat(lanes.at("/3/display").asText()).isEqualTo("00:32.15");
        assertThat(lanes.at("/4/withdrawn").asBoolean()).isTrue();
        assertThat(lanes.get(4).has("empty")).isFalse();
        assertThat(lanes.at("/4/athlete/lastName").asText()).isEqualTo("Ёлкин");
        statistics.clear(); assertThat(read("/start-list")).isEqualTo(response);
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void occupiedLaneWithoutSeedTimeIncludesNullTimeAndDisplay() throws Exception {
        jdbc.update("update competition_unit_entries set seed_time_ms=null where competition_unit_id=?",heats.getFirst());
        jdbc.update("update registrations set seed_time_ms=null where event_discipline_id=?",disciplineId);
        JsonNode lane=read("/start-list").at("/sessions/0/disciplines/0/heats/0/lanes/3");
        assertThat(lane.has("seedTimeMs")).isTrue(); assertThat(lane.path("seedTimeMs").isNull()).isTrue();
        assertThat(lane.has("display")).isTrue(); assertThat(lane.path("display").isNull()).isTrue();
    }

    @Test
    void scheduleIsFlatSortedAndSequencesContinueAcrossDisciplines() throws Exception {
        JsonNode schedule = read("/schedule"); assertThat(schedule).hasSize(4);
        for (int i=0;i<4;i++) {
            assertThat(schedule.get(i).path("sequenceInSession").asInt()).isEqualTo(i+1);
            assertThat(schedule.get(i).path("sessionName").asText()).isEqualTo("Утро");
            assertThat(OffsetDateTime.parse(schedule.get(i).path("scheduledAt").asText())).isEqualTo(start.plusSeconds(i*90L));
        }
        assertThat(schedule.get(3).path("disciplineName").asText()).isEqualTo("100 м");
        assertThat(schedule.get(3).path("heatNumber").asInt()).isEqualTo(1);
    }

    @Test
    void scheduleSequenceFollowsScheduledTimeBeforeDisciplineOrderAndNullTimes() throws Exception {
        jdbc.update("update competition_units set scheduled_at=? where id=?",start.minusSeconds(90),heats.get(3));
        jdbc.update("update competition_units set scheduled_at=null where id=?",heats.get(1));
        var response=read("/schedule");
        assertThat(response.get(0).path("disciplineName").asText()).isEqualTo("100 м");
        for (int i=0;i<4;i++) assertThat(response.get(i).path("sequenceInSession").asInt()).isEqualTo(i+1);
        assertThat(response.get(3).path("scheduledAt").isNull()).isTrue();
        assertThat(response.get(3).path("heatNumber").asInt()).isEqualTo(2);
    }

    @Test
    void liveFallsBackThenStartFinishAndPutMoveCurrentHeat() throws Exception {
        JsonNode initial = read("/live");
        assertThat(initial.at("/currentUnit/id").asText()).isEqualTo(heats.getFirst().toString());
        assertThat(initial.path("next")).hasSize(3);
        assertThat(OffsetDateTime.parse(initial.path("updatedAt").asText())).isEqualTo(OffsetDateTime.now(clock));
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.getFirst())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isOk());
        clearPublicCaches();
        assertThat(read("/live").at("/currentUnit/status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(3);
        mvc.perform(post("/api/v1/competition-units/{id}/finish",heats.getFirst())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isOk());
        clearPublicCaches();
        JsonNode finished = read("/live");
        assertThat(finished.at("/currentUnit/id").asText()).isEqualTo(heats.get(1).toString());
        assertThat(finished.path("next")).hasSize(2);
        assertThat(jdbc.queryForObject("select status from competition_units where id=?",String.class,heats.getFirst())).isEqualTo("FINISHED");
        assertThat(read("/results").at("/disciplines/0/overall")).hasSize(2);
        mvc.perform(put("/api/v1/events/{id}/live",eventId).with(authentication(auth(UUID.randomUUID(),"PLATFORM_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentUnitId\":\""+heats.get(3)+"\"}"))
                .andExpect(status().isOk());
        clearPublicCaches();
        assertThat(read("/live").at("/currentUnit/id").asText()).isEqualTo(heats.get(3).toString());
        assertThat(read("/live").path("next")).isEmpty();
    }

    @Test
    void liveUnitsIncludeEveryStartListHeatFieldAndReuseOneEntriesQuery() throws Exception {
        var startList=read("/start-list");
        var statistics=emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        var live=read("/live");
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
        var current=live.path("currentUnit");
        assertHeatFields(startList.at("/sessions/0/disciplines/0/heats/0"),current);
        assertThat(current.path("disciplineId").asText()).isEqualTo(disciplineId.toString());
        assertThat(current.path("disciplineNumber").asInt()).isEqualTo(1);
        assertThat(current.path("disciplineName").asText()).isEqualTo("50 м");
        assertHeatFields(startList.at("/sessions/0/disciplines/0/heats/1"),live.at("/next/0"));
        assertHeatFields(startList.at("/sessions/0/disciplines/0/heats/2"),live.at("/next/1"));
        assertHeatFields(startList.at("/sessions/0/disciplines/1/heats/0"),live.at("/next/2"));
    }
    private void assertHeatFields(JsonNode heat, JsonNode liveUnit) {
        heat.fields().forEachRemaining(field -> assertThat(liveUnit.get(field.getKey())).isEqualTo(field.getValue()));
    }

    @Test
    void clearedLiveStateReturnsFirstThreeUpcomingPublishedHeats() throws Exception {
        mvc.perform(put("/api/v1/events/{id}/live",eventId).with(authentication(auth(ownerId,"ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentUnitId\":null}")).andExpect(status().isOk());
        clearPublicCaches();
        var live=read("/live"); assertThat(live.path("currentUnit").isNull()).isTrue();
        assertThat(live.path("next")).hasSize(3);
        for (int i=0;i<3;i++) assertThat(live.path("next").get(i).path("id").asText()).isEqualTo(heats.get(i).toString());
        jdbc.update("update competition_units set scheduled_at=? where id=?",OffsetDateTime.now(clock).minusSeconds(1),heats.getFirst());
        jdbc.update("update competition_units set scheduled_at=null where id=?",heats.get(1));
        jdbc.update("update competition_units set status='IN_PROGRESS' where id=?",heats.get(2));
        caches.getCache("publicLive").clear();
        live=read("/live"); assertThat(live.path("next")).hasSize(1);
        assertThat(live.at("/next/0/id").asText()).isEqualTo(heats.get(3).toString());
    }

    @Test
    void lastFinishAndExplicitClearKeepCurrentUnitNull() throws Exception {
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.get(3)).with(authentication(auth(ownerId,"ORGANIZER"))))
                .andExpect(status().isOk());
        clearPublicCaches();
        mvc.perform(post("/api/v1/competition-units/{id}/finish",heats.get(3)).with(authentication(auth(ownerId,"ORGANIZER"))))
                .andExpect(status().isOk());
        clearPublicCaches();
        assertThat(read("/live").path("currentUnit").isNull()).isTrue();
        mvc.perform(put("/api/v1/events/{id}/live",eventId).with(authentication(auth(ownerId,"ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentUnitId\":null}")).andExpect(status().isOk());
        clearPublicCaches();
        assertThat(read("/live").path("currentUnit").isNull()).isTrue();
    }

    @Test
    void finishedResultsExposeOnlyNonPendingRowsAndPreserveStoredPlaces() throws Exception {
        assertThat(read("/results").path("disciplines")).isEmpty();
        finishHeat(heats.getFirst());
        finishHeat(heats.get(1));
        JsonNode results = read("/results");
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.path("disciplines")).hasSize(1);
        JsonNode rows = results.at("/disciplines/0/overall"); assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(mapper.readTree("""
                {"place":7,"lastName":"Иванов","firstName":"Иван","birthYear":2014,"club":"Волна",
                 "display":"00:32.15","timeMs":32150,"status":"VALID"}
                """));
        assertThat(rows.at("/1/place").isNull()).isTrue();
        assertThat(rows.at("/1/status").asText()).isEqualTo("DNS");
        assertThat(rows.at("/1/display").asText()).isEqualTo("DNS");
        assertThat(results.at("/disciplines/0/byAgeGroup/0/code").asText()).isEqualTo("M-2014");
        assertThat(results.at("/disciplines/0/byAgeGroup/0/rows/0/place").asInt()).isEqualTo(2);
        jdbc.update("update competition_units set status='DRAFT' where id=?",heats.getFirst());
        caches.getCache("publicResults").clear(); assertThat(read("/results").path("disciplines")).isEmpty();
    }

    @Test
    void publishedStartLanesStayVisibleButResultsRequireStartAndFinish() throws Exception {
        jdbc.update("update competition_units set status='DRAFT' where id=?",heats.getFirst());
        assertThat(read("/results").path("disciplines")).isEmpty();
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(2);
        // Start-list publication belongs to scheduling, not results publication.
        jdbc.update("update competition_units set status='PUBLISHED' where id=?",heats.getFirst());
        clearPublicCaches();
        assertThat(read("/results").path("disciplines")).isEmpty();
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(3);
        startHeat(heats.getFirst());
        assertThat(read("/results").path("disciplines")).isEmpty();
        mvc.perform(post("/api/v1/competition-units/{id}/finish",heats.getFirst())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isOk());
        clearPublicCaches();
        assertThat(read("/results").at("/disciplines/0/overall")).hasSize(2);
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(3);
    }

    @Test
    void judgePublicationCorrectionAndIdempotentFinishKeepPublicResultsAndLiveConsistent() throws Exception {
        UUID unit = heats.getFirst();
        var ids = jdbc.queryForList("select id from competition_unit_entries where competition_unit_id=? order by lane_or_position", UUID.class, unit);
        jdbc.update("update competition_unit_entries set entry_status='ASSIGNED' where competition_unit_id=?", unit);
        UUID third = UUID.randomUUID(), fourth = UUID.randomUUID();
        profile(third, "Сидоров", "Сергей"); profile(fourth, "Петров", "Пётр");
        ids.add(entry(unit, disciplineId, third, 6, false));
        ids.add(entry(unit, disciplineId, fourth, 7, false));
        // Isolate ranking from pre-existing results in other heats of this discipline.
        jdbc.update("delete from results where competition_unit_entry_id in (select ce.id from competition_unit_entries ce join competition_units u on u.id=ce.competition_unit_id where u.event_discipline_id=?)", disciplineId);
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(3);
        assertHiddenResults();

        // Re-saving while only the start list is published is ordinary judge input.
        saveTimes(unit, ids, "46.20", "JUDGE", 200);
        saveTimes(unit, ids, "46.21", "JUDGE", 200);
        assertThat(jdbc.queryForObject("select status from results where competition_unit_entry_id=?", String.class, ids.getFirst())).isEqualTo("VALID");
        assertHiddenResults();
        mvc.perform(post("/api/v1/competition-units/{id}/start", unit)
                .with(authentication(auth(ownerId, "JUDGE")))).andExpect(status().isOk());
        clearPublicCaches();
        assertThat(read("/live").at("/currentUnit/status").asText()).isEqualTo("IN_PROGRESS");
        assertHiddenResults();
        saveTimes(unit, ids, "46.20", "JUDGE", 200);
        assertHiddenResults();
        publication(unit, true, "JUDGE", 204, null);
        assertThat(unitStatus(unit)).isEqualTo("FINISHED");
        assertThat(read("/live").at("/currentUnit/id").asText()).isEqualTo(unit.toString());
        assertThat(read("/live").at("/currentUnit/status").asText()).isEqualTo("FINISHED");
        assertPublishedPlaces("00:48.00");
        assertThat(read("/athletes?q=Иванов").at("/0/swims/0/result/place").asInt()).isEqualTo(1);
        publication(unit, true, "JUDGE", 409, "results.unit_already_published");

        String correctedBody = timesBody(ids, "46.20").replace("48.00", "48.10");
        mvc.perform(post("/api/v1/competition-units/{id}/results", unit)
                .with(authentication(auth(ownerId, "OPERATOR"))).contentType(MediaType.APPLICATION_JSON)
                .content(correctedBody)).andExpect(status().isOk());
        clearPublicCaches();
        assertThat(jdbc.queryForObject("select status from results where competition_unit_entry_id=?", String.class, ids.get(2))).isEqualTo("CORRECTED");
        assertThat(jdbc.queryForObject("select count(*) from rule_execution_logs where event_id=? and kind='RANKING'", Integer.class, eventId)).isEqualTo(1);
        assertPublishedPlaces("00:48.10");
        mvc.perform(post("/api/v1/competition-units/{id}/results", unit)
                .with(authentication(auth(ownerId, "JUDGE"))).contentType(MediaType.APPLICATION_JSON)
                .content(correctedBody)).andExpect(status().isForbidden());
        clearPublicCaches();
        assertPublishedPlaces("00:48.10");
        var publishedAt = jdbc.queryForObject("select updated_at from competition_units where id=?", OffsetDateTime.class, unit);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/competition-units/{id}/finish", unit)
                    .with(authentication(auth(ownerId, "JUDGE")))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentUnitId").value(heats.get(1).toString()));
            clearPublicCaches();
            assertThat(unitStatus(unit)).isEqualTo("FINISHED");
            assertThat(jdbc.queryForObject("select updated_at from competition_units where id=?", OffsetDateTime.class, unit)).isEqualTo(publishedAt);
            assertThat(read("/live").at("/currentUnit/id").asText()).isEqualTo(heats.get(1).toString());
            assertPublishedPlaces("00:48.10");
        }
    }

    @Test
    void publicationRejectsInvalidStatesAndMissingResultsWithoutChangingStatus() throws Exception {
        publication(draftId, true, "OPERATOR", 409, "results.start_list_not_published");
        for (var state : List.of("SCHEDULED", "FINISHED", "COMPLETED", "CANCELLED")) {
            jdbc.update("update competition_units set status=? where id=?", state, draftId);
            publication(draftId, true, "OPERATOR", state.equals("CANCELLED") ? 400 : 409,
                    state.equals("SCHEDULED") ? "results.start_list_not_published" : state.equals("CANCELLED") ? "results.invalid_transition" : "results.unit_already_published");
            assertThat(unitStatus(draftId)).isEqualTo(state);
        }
        startHeat(heats.getFirst());
        UUID missing = UUID.randomUUID(); profile(missing, "Пропущен", "Результат");
        entry(heats.getFirst(), disciplineId, missing, 6, false);
        publication(heats.getFirst(), true, "JUDGE", 409, "results.unit_has_missing_results");
        assertThat(unitStatus(heats.getFirst())).isEqualTo("IN_PROGRESS");
        assertHiddenResults();
        publication(heats.getFirst(), false, "OPERATOR", 409, "results.unit_not_published");
        assertThat(unitStatus(heats.getFirst())).isEqualTo("IN_PROGRESS");
    }

    @Test
    void onlyManagersCanUnpublishFinishedResultsAndStartListRemainsPublic() throws Exception {
        UUID unit = heats.getFirst();
        publication(unit, true, "JUDGE", 204, null);
        publication(unit, false, "JUDGE", 403, null);
        assertThat(unitStatus(unit)).isEqualTo("FINISHED");
        publication(unit, false, "OPERATOR", 204, null);
        assertThat(unitStatus(unit)).isEqualTo("PUBLISHED");
        assertHiddenResults();
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(3);
        for (var state : List.of("DRAFT", "SCHEDULED", "PUBLISHED", "CANCELLED", "COMPLETED")) {
            jdbc.update("update competition_units set status=? where id=?", state, draftId);
            publication(draftId, false, "OPERATOR", state.equals("COMPLETED") ? 400 : 409,
                    state.equals("COMPLETED") ? "results.invalid_transition" : "results.unit_not_published");
            assertThat(unitStatus(draftId)).isEqualTo(state);
        }
        startHeat(unit);
        assertThat(unitStatus(unit)).isEqualTo("IN_PROGRESS");
    }

    private String unitStatus(UUID unit) {
        return jdbc.queryForObject("select status from competition_units where id=?", String.class, unit);
    }

    private void publication(UUID unit, boolean published, String role, int expected, String code) throws Exception {
        var response = mvc.perform(post("/api/v1/competition-units/{id}/publication", unit)
                .with(authentication(auth(ownerId, role))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"published\":" + published + "}")).andExpect(status().is(expected));
        if (code != null) response.andExpect(jsonPath("$.code").value(code));
        clearPublicCaches();
    }

    private String timesBody(List<UUID> ids, String firstTime) throws Exception {
        return mapper.writeValueAsString(List.of(
                Map.of("competitionUnitEntryId", ids.get(0), "status", "VALID", "time", firstTime),
                Map.of("competitionUnitEntryId", ids.get(1), "status", "VALID", "time", "46.20"),
                Map.of("competitionUnitEntryId", ids.get(2), "status", "VALID", "time", "48.00"),
                Map.of("competitionUnitEntryId", ids.get(3), "status", "DNS")));
    }

    private void saveTimes(UUID unit, List<UUID> ids, String firstTime, String role, int expected) throws Exception {
        mvc.perform(post("/api/v1/competition-units/{id}/results", unit)
                .with(authentication(auth(ownerId, role))).contentType(MediaType.APPLICATION_JSON)
                .content(timesBody(ids, firstTime))).andExpect(status().is(expected));
        clearPublicCaches();
    }

    private void assertHiddenResults() throws Exception {
        assertThat(read("/results").path("disciplines")).isEmpty();
        assertThat(read("/athletes?q=Иванов").at("/0/swims/0/result").isNull()).isTrue();
    }

    private void assertPublishedPlaces(String correctedTime) throws Exception {
        var rows = read("/results").at("/disciplines/0/overall");
        assertThat(rows).hasSize(4);
        assertThat(rows.at("/0/place").asInt()).isEqualTo(1);
        assertThat(rows.at("/1/place").asInt()).isEqualTo(1);
        assertThat(rows.at("/2/place").asInt()).isEqualTo(3);
        assertThat(rows.at("/2/display").asText()).isEqualTo(correctedTime);
        assertThat(rows.at("/3/status").asText()).isEqualTo("DNS");
        assertThat(rows.at("/3/place").isNull()).isTrue();
    }

    @Test
    void completedResultsRemainPublicAndCorrectedMapsToValidInResultsAndSearch() throws Exception {
        finishHeat(heats.getFirst());
        jdbc.update("update competition_units set status='COMPLETED' where id=?",heats.getFirst());
        jdbc.update("update results set status='CORRECTED' where competition_unit_entry_id in (select id from competition_unit_entries where competition_unit_id=? and lane_or_position=4)",heats.getFirst());
        var row=read("/results").at("/disciplines/0/overall/0");
        assertThat(row.path("status").asText()).isEqualTo("VALID");
        assertThat(row.path("place").asInt()).isEqualTo(7);
        assertThat(row.path("display").asText()).isEqualTo("00:32.15");
        mvc.perform(get(url("/athletes")).param("q","Иванов")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].swims[0].result.status").value("VALID"));
    }

    @Test
    void exhibitionMapsToValidButNeverReceivesAPlaceInResultsOrSearch() throws Exception {
        finishHeat(heats.getFirst());
        jdbc.update("update results set status='EXHIBITION' where competition_unit_entry_id in (select id from competition_unit_entries where competition_unit_id=? and lane_or_position=4)",heats.getFirst());
        var row=read("/results").at("/disciplines/0/overall/0");
        assertThat(row.path("status").asText()).isEqualTo("VALID");
        assertThat(row.path("place").isNull()).isTrue();
        assertThat(row.path("display").asText()).isEqualTo("00:32.15");
        assertThat(read("/results").at("/disciplines/0/byAgeGroup/0/rows/0/place").isNull()).isTrue();
        mvc.perform(get(url("/athletes")).param("q","Иванов")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].swims[0].result.status").value("VALID"))
                .andExpect(jsonPath("$[0].swims[0].result.place").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].swims[0].result.placeInAgeGroup").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void resultsMapDnfAndDsqToShortCodesAndPlaceNull() throws Exception {
        finishHeat(heats.getFirst());
        for (String internal : List.of("DID_NOT_FINISH","DISQUALIFIED")) {
            jdbc.update("update results set status=? where competition_unit_entry_id in (select id from competition_unit_entries where competition_unit_id=? and lane_or_position=5)",internal,heats.getFirst());
            caches.getCache("publicResults").clear();
            JsonNode row = read("/results").at("/disciplines/0/overall/1");
            assertThat(row.path("place").isNull()).isTrue();
            assertThat(row.path("status").asText()).isEqualTo(internal.equals("DISQUALIFIED") ? "DSQ" : "DNF");
        }
    }

    @Test
    void byTimeAllGroupsUseGenderAndInclusiveBirthYearsAndKeepStoredTies() throws Exception {
        jdbc.update("update event_disciplines set age_groups_json='[{\"code\":\"F-2014\",\"name\":\"Девушки 2014\",\"gender\":\"F\",\"birthYearFrom\":2014,\"birthYearTo\":2014}]' where id=?",discipline2Id);
        for (int i=0;i<4;i++) {
            UUID id=UUID.randomUUID(); profile(id,"Спортсмен"+i,"Имя");
            jdbc.update("update profiles set gender=?,birth_date=?::date where id=?",i==3 ? "M" : "F",i==2 ? "2013-01-01" : "2014-01-01",id);
            result(entry(heats.get(3),discipline2Id,id,i+5,false),i<2 ? 30000L : 32000L,"VALID",i<2 ? 1 : 3,i<2 ? 1 : null);
        }
        finishHeat(heats.getFirst());
        finishHeat(heats.get(3));
        JsonNode results=read("/results").path("disciplines").get(1);
        assertThat(results.path("overall")).hasSize(4);
        assertThat(results.at("/overall/0/place").asInt()).isEqualTo(1);
        assertThat(results.at("/overall/1/place").asInt()).isEqualTo(1);
        assertThat(results.at("/overall/2/place").asInt()).isEqualTo(3);
        assertThat(results.at("/byAgeGroup/0/rows")).hasSize(2);
        assertThat(results.at("/byAgeGroup/0/rows/1/place").asInt()).isEqualTo(1);
    }

    @Test
    void validatedResultsStayHiddenInProgressAndBecomePublicOnFinish() throws Exception {
        assertThat(read("/results").path("disciplines")).isEmpty();
        startHeat(heats.getFirst());
        assertThat(read("/results").path("disciplines")).isEmpty();
        mvc.perform(get(url("/athletes")).param("q","Иванов")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].swims[0].result").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/v1/competition-units/{id}/finish",heats.getFirst())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isOk());
        clearPublicCaches();
        assertThat(read("/results").at("/disciplines/0/overall")).hasSize(2);
        mvc.perform(get(url("/athletes")).param("q","Иванов")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].swims[0].result.place").value(7));
    }

    @Test
    void sequencesRestartForAnotherSessionAndRegistrationWithdrawalKeepsAthleteData() throws Exception {
        UUID discipline=UUID.randomUUID(), heat=UUID.randomUUID();
        discipline(discipline,session2Id,1,"200 м","BY_TIME_ALL");
        heat(heat,discipline,1,"PUBLISHED",start.plusHours(3));
        assertThat(read("/schedule").get(4).path("sequenceInSession").asInt()).isEqualTo(1);
        jdbc.update("update registrations set status='WITHDRAWN' where participant_profile_id=? and event_discipline_id=?",athleteId,disciplineId);
        JsonNode lane=read("/start-list").at("/sessions/0/disciplines/0/heats/0/lanes/3");
        assertThat(lane.path("withdrawn").asBoolean()).isTrue(); assertThat(lane.has("empty")).isFalse();
        assertThat(lane.at("/athlete/lastName").asText()).isEqualTo("Иванов");
    }

    @Test
    void findSelfUsesCaseInsensitivePrefixAndYoNormalizationWithAllPublishedSwims() throws Exception {
        finishHeat(heats.getFirst());
        JsonNode ivan = mapper.readTree(mvc.perform(get(url("/athletes")).param("q","ИВА"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(ivan).hasSize(2);
        JsonNode athlete = ivan.get(0); assertThat(athlete.path("athleteKey").asText()).isEqualTo(athleteId.toString());
        assertThat(athlete.path("swims")).hasSize(2);
        assertThat(athlete.at("/swims/0/result/place").asInt()).isEqualTo(7);
        assertThat(athlete.at("/swims/1/result").isNull()).isTrue();
        for (String query : List.of("елк","ЁЛК"," ёлк ")) {
            mvc.perform(get(url("/athletes")).param("q",query)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].lastName").value("Ёлкин"));
        }
        mvc.perform(get(url("/athletes")).param("q","%" )).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(url("/athletes")).param("q","ван" )).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(read("/athletes")).isEmpty();
    }

    @Test
    void athleteSearchCapsProfilesRatherThanSwimsAtTwenty() throws Exception {
        for (int i=0;i<25;i++) {
            UUID id=UUID.randomUUID(); profile(id,"Иванов"+i,"Иван"); entry(heats.get(2),disciplineId,id,i+1,false);
        }
        mvc.perform(get(url("/athletes")).param("q","ива")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void anonymousPublicEndpointsHideDraftEventsAndUnknownSlugs() throws Exception {
        for (String suffix : List.of("","/start-list","/schedule","/live","/results","/athletes")) {
            mvc.perform(get("/api/v1/public/events/unknown"+suffix)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("events.public_not_found"))
                    .andExpect(jsonPath("$.message").value("Публичное событие не найдено."));
        }
        jdbc.update("update events set status='DRAFT' where id=?",eventId);
        mvc.perform(get(url(""))).andExpect(status().isNotFound());
    }

    @Test
    void canonicalLiveMutationsUseEventAccessAndRejectForeignUnitsAndInvalidTransitions() throws Exception {
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.getFirst())).andExpect(status().isUnauthorized());
        clearPublicCaches();
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.getFirst())
                .with(authentication(auth(UUID.randomUUID(),"ORGANIZER")))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("events.access_denied"));
        clearPublicCaches();
        mvc.perform(post("/api/v1/competition-units/{id}/finish",heats.getFirst())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("results.invalid_transition"));
        clearPublicCaches();
        mvc.perform(put("/api/v1/events/{id}/live",eventId).with(authentication(auth(ownerId,"ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentUnitId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isNotFound());
        clearPublicCaches();
        UUID foreignEvent=UUID.randomUUID(), foreignDiscipline=UUID.randomUUID(), foreignUnit=UUID.randomUUID();
        jdbc.update("""
                insert into events(id,organization_id,venue_id,sport_id,regulation_version_id,title,event_start_at,
                    event_end_at,status,public_slug,created_by_user_id,created_at,updated_at)
                select ?,organization_id,venue_id,sport_id,regulation_version_id,title,event_start_at,
                    event_end_at,status,?,created_by_user_id,created_at,updated_at from events where id=?
                """,foreignEvent,"foreign-"+foreignEvent,eventId);
        jdbc.update("""
                insert into event_disciplines(id,event_id,discipline_template_id,code,name,competition_format,unit_type,
                    result_type,ranking_strategy,status,created_at,updated_at)
                select ?,?,discipline_template_id,code,name,competition_format,unit_type,result_type,ranking_strategy,
                    status,created_at,updated_at from event_disciplines where id=?
                """,foreignDiscipline,foreignEvent,disciplineId);
        heat(foreignUnit,foreignDiscipline,1,"PUBLISHED",start);
        mvc.perform(put("/api/v1/events/{id}/live",eventId).with(authentication(auth(ownerId,"ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentUnitId\":\""+foreignUnit+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("results.unit_event_mismatch"));
        clearPublicCaches();
        startHeat(heats.getFirst());
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.get(1))
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("results.unit_in_progress"));
        clearPublicCaches();
        mvc.perform(post("/api/v1/competition-units/{id}/start",UUID.randomUUID())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isNotFound());
        clearPublicCaches();
    }

    @Test
    void noUpcomingPublishedHeatHasNullCurrentAndEmptyNext() throws Exception {
        jdbc.update("update competition_units set scheduled_at=now()-interval '1 day' where event_discipline_id in (?,?)",disciplineId,discipline2Id);
        JsonNode live = read("/live"); assertThat(live.path("currentUnit").isNull()).isTrue();
        assertThat(live.path("next")).isEmpty();
    }

    @Test
    void corsUsesConfiguredOriginsAndSwaggerIsPublicWithExamples() throws Exception {
        finishHeat(heats.getFirst());
        mvc.perform(get("/api/v1/public/competition-units/{id}/results",heats.getFirst())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/events/{id}/disciplines/{discipline}/results",eventId,disciplineId)).andExpect(status().isOk());
        mvc.perform(options(url("/live")).header("Origin","https://spectators.example").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://spectators.example"));
        mvc.perform(options(url("/live")).header("Origin","https://evil.example").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isForbidden());
        JsonNode spec = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(spec.path("paths").path("/api/v1/public/events/{slug}/results").path("get").path("responses")
                .path("200").path("content").path("application/json").path("example").isMissingNode()).isFalse();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void publicEndpointsForSetMHaveBoundedQueriesAndLogLatency() throws Exception {
        jdbc.update("delete from results where competition_unit_entry_id in (select ce.id from competition_unit_entries ce join registrations r on r.id=ce.registration_id where r.event_id=?)",eventId);
        jdbc.update("delete from competition_unit_entries where registration_id in (select id from registrations where event_id=?)",eventId);
        jdbc.update("delete from competition_units where event_discipline_id in (select id from event_disciplines where event_id=?)",eventId);
        jdbc.update("delete from registrations where event_id=?",eventId);
        JsonNode dataset;
        try (var input = getClass().getResourceAsStream("/publication/set-M.json")) {
            assertThat(input).isNotNull(); dataset = mapper.readTree(input);
        }
        assertThat(dataset.path("entries")).hasSize(200);
        Map<Integer,UUID> profiles = new HashMap<>();
        for (JsonNode athlete : dataset.path("athletes")) {
            UUID id=UUID.randomUUID(); profiles.put(athlete.path("id").asInt(),id);
            jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) values (?,?,?,?,?,?)",
                    id,athlete.path("lastName").asText(),athlete.path("firstName").asText(),
                    java.time.LocalDate.parse(athlete.path("birthDate").asText(),java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    athlete.path("gender").asText().equals("М") ? "M" : "F",athlete.path("club").asText());
        }
        Map<String,UUID> disciplineIds = new LinkedHashMap<>();
        disciplineIds.put("M-50-FREE",disciplineId); disciplineIds.put("F-50-FREE",discipline2Id);
        disciplineIds.put("M-100-FREE",UUID.randomUUID()); disciplineIds.put("F-100-FREE",UUID.randomUUID());
        discipline(disciplineIds.get("M-100-FREE"),session2Id,1,"100 м юноши","BY_TIME_ALL");
        discipline(disciplineIds.get("F-100-FREE"),session2Id,2,"100 м девушки","BY_TIME_ALL");
        int scheduledIndex = 0;
        for (var discipline : disciplineIds.entrySet()) {
            String gender=discipline.getKey().substring(0,1);
            List<Map<String,Object>> groups = new ArrayList<>();
            for (int year=2010;year<=2014;year+=2) groups.add(Map.of("code",gender+"-"+year+"-"+(year+1),
                    "name",gender+" "+year+"–"+(year+1),"gender",gender,"birthYearFrom",year,"birthYearTo",year+1));
            jdbc.update("update event_disciplines set code=?,gender=?,distance_m=?,age_groups_json=?::jsonb,seeding_mode='BY_TIME_ALL' where id=?",
                    discipline.getKey(),gender,discipline.getKey().contains("100") ? 100 : 50,mapper.writeValueAsString(groups),discipline.getValue());
            int count=0; UUID heat=null; UUID firstHeat=null;
            // Fixture assignment only; seeding and ranking belong to T02/T08.
            for (JsonNode registration : dataset.path("entries")) {
                if (!discipline.getKey().equals(registration.path("disciplineCode").asText())) continue;
                if (count%8 == 0) {
                    heat=UUID.randomUUID(); heat(heat,discipline.getValue(),count/8+1,"PUBLISHED",start.plusSeconds(scheduledIndex++*150L));
                    if (count == 0) firstHeat=heat;
                }
                UUID entry=entry(heat,discipline.getValue(),profiles.get(registration.path("athleteId").asInt()),count%8+1,false);
                Long time=registration.path("seedTimeMs").isNull() ? null : registration.path("seedTimeMs").asLong();
                jdbc.update("update competition_unit_entries set seed_time_ms=? where id=?",time,entry);
                jdbc.update("update registrations set seed_time_ms=? where id=(select registration_id from competition_unit_entries where id=?)",time,entry);
                result(entry,time,time==null ? "DID_NOT_START" : "VALID",time==null ? null : count+1,time==null ? null : count+1);
                count++;
            }
            jdbc.update("update competition_units set status='FINISHED' where id=?",firstHeat);
        }
        for (String suffix : List.of("","/start-list","/schedule","/live","/results","/athletes?q=К")) {
            read(suffix);
            for (int i=0;i<3;i++) {
                caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
                var statistics = emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
                long began=System.nanoTime(); JsonNode response=read(suffix);
                long elapsed=(System.nanoTime()-began)/1_000_000;
                if (suffix.equals("/start-list")) {
                    assertThat(response.path("sessions")).hasSize(2);
                    assertThat(response.at("/sessions/0/disciplines/0/heats")).hasSize(7);
                }
                long queries=statistics.getPrepareStatementCount();
                assertThat(queries).isLessThanOrEqualTo(10);
                org.slf4j.LoggerFactory.getLogger(getClass()).info("Public endpoint {}: {} ms, {} SQL statements",suffix,elapsed,queries);
                if (suffix.equals("/results")) assertThat(response.path("disciplines")).hasSize(4);
            }
        }
    }
}
