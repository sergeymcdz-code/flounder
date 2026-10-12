package com.acme.sportplatform.publication;

import java.time.OffsetDateTime;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired EntityManagerFactory emf;
    @Autowired @Qualifier("publicationCacheManager") CacheManager caches;
    UUID eventId, sessionId, session2Id, disciplineId, discipline2Id, venueId, templateId;
    UUID athleteId, secondAthleteId, ownerId, draftId;
    List<UUID> heats;
    String slug;
    OffsetDateTime start;
    record Principal(UUID userId) implements AuthenticatedUserPrincipal {
        @Override public UUID getUserId() { return userId; }
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
        start = OffsetDateTime.now().plusDays(1).withNano(0);
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
        return new UsernamePasswordAuthenticationToken(new Principal(id),null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    }

    @Test
    void eventCardHasVenueSessionsDisciplinesAndRegistrationFlag() throws Exception {
        JsonNode response = read("");
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
        assertThat(lanes.at("/4/empty").asBoolean()).isTrue();
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
    void liveFallsBackThenStartFinishAndPutMoveCurrentHeat() throws Exception {
        JsonNode initial = read("/live");
        assertThat(initial.at("/currentUnit/id").asText()).isEqualTo(heats.getFirst().toString());
        assertThat(initial.path("next")).hasSize(3);
        assertThat(initial.has("updatedAt")).isTrue(); assertThat(initial.path("updatedAt").isNull()).isTrue();
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.getFirst())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isOk());
        assertThat(read("/live").at("/currentUnit/status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(3);
        mvc.perform(post("/api/v1/competition-units/{id}/finish",heats.getFirst())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isOk());
        JsonNode finished = read("/live");
        assertThat(finished.at("/currentUnit/id").asText()).isEqualTo(heats.get(1).toString());
        assertThat(finished.path("next")).hasSize(2);
        assertThat(jdbc.queryForObject("select status from competition_units where id=?",String.class,heats.getFirst())).isEqualTo("FINISHED");
        assertThat(read("/results")).isEmpty();
        mvc.perform(put("/api/v1/events/{id}/live",eventId).with(authentication(auth(UUID.randomUUID(),"PLATFORM_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentUnitId\":\""+heats.get(3)+"\"}"))
                .andExpect(status().isOk());
        assertThat(read("/live").at("/currentUnit/id").asText()).isEqualTo(heats.get(3).toString());
        assertThat(read("/live").path("next")).isEmpty();
    }

    @Test
    void lastFinishAndExplicitClearKeepCurrentUnitNull() throws Exception {
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.get(3)).with(authentication(auth(ownerId,"ORGANIZER"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/competition-units/{id}/finish",heats.get(3)).with(authentication(auth(ownerId,"ORGANIZER"))))
                .andExpect(status().isOk());
        assertThat(read("/live").path("currentUnit").isNull()).isTrue();
        mvc.perform(put("/api/v1/events/{id}/live",eventId).with(authentication(auth(ownerId,"ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentUnitId\":null}")).andExpect(status().isOk());
        assertThat(read("/live").path("currentUnit").isNull()).isTrue();
    }

    @Test
    void resultsExposeOnlyPublishedNonPendingRowsAndPreserveStoredPlaces() throws Exception {
        JsonNode results = read("/results"); assertThat(results).hasSize(1);
        JsonNode rows = results.at("/0/overall"); assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(mapper.readTree("""
                {"place":7,"lastName":"Иванов","firstName":"Иван","birthYear":2014,"club":"Волна",
                 "display":"00:32.15","timeMs":32150,"status":"VALID"}
                """));
        assertThat(rows.at("/1/place").isNull()).isTrue();
        assertThat(rows.at("/1/status").asText()).isEqualTo("DNS");
        assertThat(rows.at("/1/display").asText()).isEqualTo("DNS");
        assertThat(results.at("/0/byAgeGroup/0/code").asText()).isEqualTo("M-2014");
        assertThat(results.at("/0/byAgeGroup/0/rows/0/place").asInt()).isEqualTo(2);
        jdbc.update("update competition_units set status='DRAFT' where id=?",heats.getFirst());
        caches.getCache("publicResults").clear(); assertThat(read("/results")).isEmpty();
    }

    @Test
    void publishingOneHeatMakesExactlyItsResultsAndStartLanesPublic() throws Exception {
        jdbc.update("update competition_units set status='DRAFT' where id=?",heats.getFirst());
        assertThat(read("/results")).isEmpty();
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(2);
        mvc.perform(post("/api/v1/competition-units/{id}/publication",heats.getFirst())
                .with(authentication(auth(ownerId,"PLATFORM_ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"published\":true}")).andExpect(status().isNoContent());
        // Publication changes become visible on TTL expiry; clear the cache to avoid a timed test.
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
        assertThat(read("/results").at("/0/overall")).hasSize(2);
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats")).hasSize(3);
    }

    @Test
    void resultsMapDnfAndDsqToShortCodesAndPlaceNull() throws Exception {
        for (String internal : List.of("DID_NOT_FINISH","DISQUALIFIED")) {
            jdbc.update("update results set status=? where competition_unit_entry_id in (select id from competition_unit_entries where competition_unit_id=? and lane_or_position=5)",internal,heats.getFirst());
            caches.getCache("publicResults").clear();
            JsonNode row = read("/results").at("/0/overall/1");
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
        JsonNode results=read("/results").get(1);
        assertThat(results.path("overall")).hasSize(4);
        assertThat(results.at("/overall/0/place").asInt()).isEqualTo(1);
        assertThat(results.at("/overall/1/place").asInt()).isEqualTo(1);
        assertThat(results.at("/overall/2/place").asInt()).isEqualTo(3);
        assertThat(results.at("/byAgeGroup/0/rows")).hasSize(2);
        assertThat(results.at("/byAgeGroup/0/rows/1/place").asInt()).isEqualTo(1);
    }

    @Test
    void validatedResultsStayHiddenUntilHeatPublication() throws Exception {
        jdbc.update("update competition_units set status='IN_PROGRESS' where id=?",heats.getFirst());
        assertThat(read("/results")).isEmpty();
        mvc.perform(get(url("/athletes")).param("q","Иванов")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].swims[0].result").value(org.hamcrest.Matchers.nullValue()));
        jdbc.update("update competition_units set status='FINISHED' where id=?",heats.getFirst());
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
        assertThat(read("/results")).isEmpty();
    }

    @Test
    void sequencesRestartForAnotherSessionAndRegistrationWithdrawalMakesLaneEmpty() throws Exception {
        UUID discipline=UUID.randomUUID(), heat=UUID.randomUUID();
        discipline(discipline,session2Id,1,"200 м","BY_TIME_ALL");
        heat(heat,discipline,1,"PUBLISHED",start.plusHours(3));
        assertThat(read("/schedule").get(4).path("sequenceInSession").asInt()).isEqualTo(1);
        jdbc.update("update registrations set status='WITHDRAWN' where participant_profile_id=? and event_discipline_id=?",athleteId,disciplineId);
        JsonNode lane=read("/start-list").at("/sessions/0/disciplines/0/heats/0/lanes/3");
        assertThat(lane.path("withdrawn").asBoolean()).isTrue(); assertThat(lane.path("empty").asBoolean()).isTrue();
    }

    @Test
    void findSelfUsesCaseInsensitivePrefixAndYoNormalizationWithAllPublishedSwims() throws Exception {
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
    void liveMutationsRequireOwnerOrAdminAndRejectForeignDraftAndInvalidTransitions() throws Exception {
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.getFirst())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/competition-units/{id}/start",heats.getFirst())
                .with(authentication(auth(UUID.randomUUID(),"ORGANIZER")))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Нет прав на управление этим событием."));
        mvc.perform(post("/api/v1/competition-units/{id}/finish",heats.getFirst())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isConflict());
        for (UUID invalid : List.of(draftId,UUID.randomUUID())) {
            mvc.perform(put("/api/v1/events/{id}/live",eventId).with(authentication(auth(ownerId,"ORGANIZER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentUnitId\":\""+invalid+"\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/competition-units/{id}/start",draftId)
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/competition-units/{id}/start",UUID.randomUUID())
                .with(authentication(auth(ownerId,"ORGANIZER")))).andExpect(status().isNotFound());
    }

    @Test
    void noUpcomingPublishedHeatHasNullCurrentAndEmptyNext() throws Exception {
        jdbc.update("update competition_units set scheduled_at=now()-interval '1 day' where event_discipline_id in (?,?)",disciplineId,discipline2Id);
        JsonNode live = read("/live"); assertThat(live.path("currentUnit").isNull()).isTrue();
        assertThat(live.path("next")).isEmpty();
    }

    @Test
    void corsUsesConfiguredOriginsAndSwaggerIsPublicWithExamples() throws Exception {
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
    void startListForTwoHundredEntriesHasBoundedQueriesAndRespondsUnderThreeHundredMs() throws Exception {
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
            int count=0; UUID heat=null;
            // Fixture assignment only; seeding and ranking belong to T02/T08.
            for (JsonNode registration : dataset.path("entries")) {
                if (!discipline.getKey().equals(registration.path("disciplineCode").asText())) continue;
                if (count%8 == 0) {
                    heat=UUID.randomUUID(); heat(heat,discipline.getValue(),count/8+1,"PUBLISHED",start.plusSeconds(scheduledIndex++*150L));
                }
                UUID entry=entry(heat,discipline.getValue(),profiles.get(registration.path("athleteId").asInt()),count%8+1,false);
                Long time=registration.path("seedTimeMs").isNull() ? null : registration.path("seedTimeMs").asLong();
                jdbc.update("update competition_unit_entries set seed_time_ms=? where id=?",time,entry);
                jdbc.update("update registrations set seed_time_ms=? where id=(select registration_id from competition_unit_entries where id=?)",time,entry);
                result(entry,time,time==null ? "DID_NOT_START" : "VALID",time==null ? null : count+1,time==null ? null : count+1);
                count++;
            }
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
                assertThat(elapsed).as("uncached %s latency in milliseconds",suffix).isLessThanOrEqualTo(300);
            }
        }
    }
}
