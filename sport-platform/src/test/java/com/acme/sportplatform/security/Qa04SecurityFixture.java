package com.acme.sportplatform.security;

import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Real PostgreSQL fixtures; each test rolls back its users, memberships and mutations. */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"APP_CORS_ALLOWED_ORIGINS=https://qa04.example"})
abstract class Qa04SecurityFixture extends AbstractPostgresIntegrationTest {
    static final java.nio.file.Path DOCUMENT_FILES;
    static {
        try { DOCUMENT_FILES = java.nio.file.Files.createTempDirectory("flounder-qa04-documents-"); }
        catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
    }
    @org.springframework.test.context.DynamicPropertySource
    static void fileProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.files.local-dir", DOCUMENT_FILES::toString);
        registry.add("app.files.encryption-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }
    static final List<String> ACTORS = List.of("anonymous", "participant", "judge", "operator",
            "organizer", "foreign_organizer", "platform_admin");
    static final String PASSWORD = "Qa04-test-password!";
    static final String HASH = new BCryptPasswordEncoder(10).encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager em;
    final Map<String, String> ids = new HashMap<>();

    UUID id(String name) { return UUID.fromString(ids.get(name)); }
    String expand(String value) {
        for (var name : ids.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList())
            value = value.replace("$" + name, ids.get(name));
        return value;
    }
    PlatformUserPrincipal principal(String actor) {
        String role = actor.equals("foreign_organizer") ? "organizer" : actor;
        return new PlatformUserPrincipal(id(actor), actor + "-" + ids.get("event") + "@qa04.example",
                "", "active", List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT))));
    }
    UsernamePasswordAuthenticationToken auth(String actor) {
        var p = principal(actor);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }
    byte[] userBody(String password) throws Exception {
        return json.writeValueAsBytes(Map.of("email", ids.get("newEmail"), "password", password,
                "firstName", "Иван", "lastName", "Петров", "birthDate", "2014-03-05", "gender", "M"));
    }
    String publicUrl(String suffix) { return "/api/v1/public/events/" + ids.get("slug") + suffix; }

    @BeforeEach
    void securityFixture() {
        ids.clear();
        for (String name : List.of("event", "foreignEvent", "org", "foreignOrg", "venue", "version",
                "regTemplate", "template", "discipline", "newDiscipline", "foreignDiscipline", "session",
                "unit", "foreignUnit", "entry", "foreignEntry", "registration", "foreignRegistration",
                "profile", "foreignProfile", "target", "payment", "orgRequest"))
            ids.put(name, UUID.randomUUID().toString());
        for (String actor : ACTORS) if (!actor.equals("anonymous")) ids.put(actor, UUID.randomUUID().toString());
        ids.put("sport", "10000000-0000-0000-0000-000000000001");
        ids.put("slug", "qa04-" + ids.get("event"));
        ids.put("newEmail", "new-" + ids.get("event") + "@qa04.example");
        ids.put("participantEmail", "participant-" + ids.get("event") + "@qa04.example");
        for (String actor : ACTORS) if (!actor.equals("anonymous")) user(actor, actor.equals("foreign_organizer") ? "organizer" : actor);
        user("target", "participant");
        for (String organization : List.of("org", "foreignOrg"))
            jdbc.update("insert into organizations(id,type,name,created_at,updated_at) values (?,'club','QA04',now(),now())", id(organization));
        for (String actor : List.of("organizer", "operator", "judge")) member("org", actor, actor);
        member("foreignOrg", "foreign_organizer", "owner");
        jdbc.update("insert into venues(id,name,city,address,timezone,venue_meta) values (?,'Бассейн','Самара','private-address-qa04','Europe/Samara','{\"lanes\":8,\"courseLength\":25}')", id("venue"));
        jdbc.update("insert into regulation_templates(id,sport_id,code,name,created_at) values (?,?,?,'QA04',now())", id("regTemplate"), id("sport"), "qa04-" + ids.get("event"));
        jdbc.update("insert into regulation_versions(id,regulation_template_id,version_no,status,rules_json,created_at) values (?,?,1,'PUBLISHED','{\"sections\":[]}',now())", id("version"), id("regTemplate"));
        jdbc.update("insert into discipline_templates(id,sport_id,code,name,competition_format,unit_type,result_type,ranking_strategy) values (?,?,?,'QA04','INDIVIDUAL','HEAT','TIME','ASC')", id("template"), id("sport"), "qa04-" + ids.get("event"));
        event("event", "org", "organizer", ids.get("slug"));
        event("foreignEvent", "foreignOrg", "foreign_organizer", "foreign-" + ids.get("event"));
        jdbc.update("insert into event_sessions(id,event_id,name,order_no,starts_at) values (?,?,'Утро',1,now()+interval '1 day')", id("session"), id("event"));
        discipline("discipline", "event", "M-50-FREE", true);
        discipline("newDiscipline", "event", "M-100-FREE", false);
        discipline("foreignDiscipline", "foreignEvent", "M-50-FREE", false);
        profile("profile", "participant"); profile("foreignProfile", "target");
        registration("registration", "event", "discipline", "profile", "participant");
        registration("foreignRegistration", "foreignEvent", "foreignDiscipline", "foreignProfile", "target");
        unit("unit", "discipline"); unit("foreignUnit", "foreignDiscipline");
        entry("entry", "unit", "registration"); entry("foreignEntry", "foreignUnit", "foreignRegistration");
        jdbc.update("insert into payments(id,registration_id,amount,currency,status,provider,idempotency_key,created_at,updated_at) values (?,?,100,'RUB','PENDING','MANUAL',?,now(),now())", id("payment"), id("registration"), UUID.randomUUID());
        pendingRequest("target", id("orgRequest"));
    }
    void user(String actor, String role) {
        jdbc.update("insert into users(id,email,password_hash,status,created_at,updated_at) values (?,?,?,'active',now(),now())", id(actor), actor + "-" + ids.get("event") + "@qa04.example", HASH);
        jdbc.update("insert into user_role_assignments(id,user_id,role_id,created_at) select ?,?,id,now() from roles where code=?", UUID.randomUUID(), id(actor), role);
    }
    void member(String organization, String actor, String role) {
        jdbc.update("insert into organization_members(id,organization_id,user_id,member_role,created_at) values (?,?,?,?,now())", UUID.randomUUID(), id(organization), id(actor), role);
    }
    void event(String name, String org, String owner, String slug) {
        jdbc.update("""
                insert into events(id,organization_id,venue_id,sport_id,regulation_version_id,title,
                registration_open_at,registration_close_at,event_start_at,event_end_at,status,public_slug,
                created_by_user_id,created_at,updated_at)
                values (?,?,?,?,?,'QA04 кубок',now()-interval '1 day',now()+interval '2 days',
                now()+interval '1 day',now()+interval '2 days','REGISTRATION_OPEN',?,?,now(),now())
                """, id(name), id(org), id("venue"), id("sport"), id("version"), slug, id(owner));
    }
    void discipline(String name, String event, String code, boolean session) {
        jdbc.update("""
                insert into event_disciplines(id,event_id,discipline_template_id,code,name,competition_format,
                unit_type,result_type,ranking_strategy,status,created_at,updated_at,distance_m,stroke,gender,
                session_id,order_in_session,seeding_mode,entry_fee_amount,entry_fee_currency)
                values (?,?,?,?,'50 м','INDIVIDUAL','HEAT','TIME','ASC','PUBLISHED',now(),now(),50,'FREE','M',?,1,'BY_TIME_ALL',100,'RUB')
                """, id(name), id(event), id("template"), code, session ? id("session") : null);
    }
    void profile(String profile, String actor) {
        jdbc.update("insert into profiles(id,user_id,last_name,first_name,birth_date,gender,club_name) values (?,?,'Иванов','Иван','2014-03-05','M','Волна')", id(profile), id(actor));
    }
    void registration(String registration, String event, String discipline, String profile, String actor) {
        jdbc.update("""
                insert into registrations(id,event_id,event_discipline_id,participant_profile_id,participant_user_id,
                status,participant_snapshot,seed_time_ms,payment_status,created_at,updated_at)
                values (?,?,?,?,?,'CONFIRMED','{"lastName":"Иванов","firstName":"Иван","birthDate":"2014-03-05",
                "gender":"M","clubName":"Волна","email":"private-email-qa04@example.org","phone":"private-phone-qa04",
                "address":"private-address-qa04"}',32150,'PENDING',now(),now())
                """, id(registration), id(event), id(discipline), id(profile), id(actor));
    }
    void unit(String name, String discipline) {
        jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status,scheduled_at) values (?,?,'Заплыв',1,'FINISHED',now()+interval '1 day')", id(name), id(discipline));
    }
    void entry(String entry, String unit, String registration) {
        jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position,seed_time_ms) values (?,?,?,1,32150)", id(entry), id(unit), id(registration));
        jdbc.update("insert into results(id,competition_unit_entry_id,result_type,status,time_ms,raw_value,final_place,place_in_age_group) values (?,?,'TIME','VALID',32150,'00:32.15',1,1)", UUID.randomUUID(), id(entry));
    }
    void pendingRequest(String actor, UUID request) {
        jdbc.update("insert into organization_registration_requests(id,applicant_user_id,organization_type,organization_name,status,created_at,updated_at) values (?,?,'club','QA04','PENDING',now(),now())", request, id(actor));
    }
}
