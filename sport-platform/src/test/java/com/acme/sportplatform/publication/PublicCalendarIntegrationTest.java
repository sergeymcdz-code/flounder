package com.acme.sportplatform.publication;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.acme.sportplatform.organizations.application.PublicOrganizationProfileService;
import com.acme.sportplatform.organizations.api.PublicOrganizationProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(PublicCalendarIntegrationTest.SqlCounting.class)
class PublicCalendarIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("publicationCacheManager") org.springframework.cache.CacheManager caches;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PublicOrganizationProfileService profiles;
    @Autowired com.acme.sportplatform.organizations.application.OrganizationPublicSlugs slugs;
    UUID organization, venue;
    String slug;
    static final AtomicInteger statements = new AtomicInteger();

    @TestConfiguration
    static class SqlCounting {
        @Bean static BeanPostProcessor countStatements() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource source)) return bean;
                    return new DelegatingDataSource(source) {
                        @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                            var connection=super.getConnection();
                            return (java.sql.Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(p,m,args) -> {
                                try {
                                    if (m.getName().equals("prepareStatement")) statements.incrementAndGet();
                                    return m.invoke(connection,args);
                                } catch (InvocationTargetException ex) { throw ex.getCause(); }
                            });
                        }
                    };
                }
            };
        }
    }
    @BeforeEach void fixture() {
        organization=UUID.randomUUID(); venue=UUID.randomUUID(); slug="club-"+organization;
        jdbc.update("insert into organizations(id,type,name,meta,public_slug,created_at,updated_at) values (?,'CLUB','Волна','{\"city\":\"Самара\"}',?,now(),now())",organization,slug);
        jdbc.update("insert into venues(id,name,city,timezone) values (?,'Бассейн','Самара','Europe/Samara')",venue);
    }
    void event(String status, int days, String title) {
        UUID id=UUID.randomUUID();
        var start=OffsetDateTime.now(ZoneOffset.UTC).plusDays(days);
        jdbc.update("""
                insert into events(id,organization_id,venue_id,sport_id,regulation_version_id,title,status,public_slug,
                    event_start_at,event_end_at,registration_open_at,registration_close_at,created_by_user_id,created_at,updated_at)
                values (?,?,?,'10000000-0000-0000-0000-000000000001',?,?,?, ?,?,?,now()-interval '1 day',now()+interval '1 day',?,now(),now())
                """,id,organization,venue,UUID.randomUUID(),title,status,"cup-"+id,start,start.plusHours(6),UUID.randomUUID());
    }
    String calendar(String extra) { return "/api/v1/public/calendar?organization="+slug+extra; }
    @Test void lazyAndStartupSlugsUseUniqueTransliteration() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        String name="Клуб "+UUID.randomUUID().toString().substring(0,8);
        for(UUID id:List.of(first,second))
            jdbc.update("insert into organizations(id,type,name,created_at,updated_at) values (?,'CLUB',?,now(),now())",id,name);
        String firstSlug=profiles.profile(first).publicSlug();
        assertThat(firstSlug).startsWith("klub-").matches("^[a-z0-9][a-z0-9-]{2,127}$");
        slugs.run(null);
        assertThat(profiles.profile(second).publicSlug()).isEqualTo(firstSlug+"-2");
    }
    @Test void ownerAndAdminMembershipCanManageWhileOrdinaryMembersCannot() throws Exception {
        UUID user=UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,status) values (?,?,'test-only','active')",user,user+"@s08.example");
        jdbc.update("insert into organization_members(id,organization_id,user_id,member_role,created_at) values (?,?,?,'owner',now())",UUID.randomUUID(),organization,user);
        com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal=new com.acme.sportplatform.identity.AuthenticatedUserPrincipal() {
            public UUID getUserId() { return user; }
            public List<org.springframework.security.core.GrantedAuthority> getAuthorities() { return List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ORGANIZER")); }
        };
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());
        String path="/api/v1/organizations/"+organization+"/public-profile";
        for(String role:List.of("owner","ADMIN","organizer")) {
            jdbc.update("update organization_members set member_role=? where organization_id=? and user_id=?",role,organization,user);
            mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth)))
                    .andExpect(status().is(role.equals("organizer") ? 403 : 200));
        }
    }
    @Test void visibilityFiltersPagingAndCaching() throws Exception {
        event("DRAFT",1,"Черновик"); event("PUBLISHED",2,"Кубок"); event("REGISTRATION_OPEN",3,"Открытый кубок");
        event("REGISTRATION_CLOSED",4,"Закрытый кубок"); event("LIVE",0,"Сегодня");
        event("COMPLETED",-2,"Итоги"); event("ARCHIVED",-3,"Архив");
        mvc.perform(get(calendar("&status=all&size=2"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.cities").isArray())
                .andExpect(jsonPath("$.items[0].venueTimezone").value("Europe/Samara"))
                .andExpect(header().string("Cache-Control","public, max-age=15")).andExpect(header().string("X-Flounder-Cached","false"));
        mvc.perform(get(calendar("&status=all&size=2"))).andExpect(header().string("X-Flounder-Cached","true"));
        mvc.perform(get(calendar("&status=past"))).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].title").value("Итоги"));
        mvc.perform(get(calendar("&status=upcoming&city=Самара&q=кубок"))).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].title").value("Кубок")).andExpect(jsonPath("$.items[1].registrationOpen").value(true));
        mvc.perform(get(calendar("&city=Москва"))).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(calendar("&status=all&page=10&size=2"))).andExpect(jsonPath("$.total").value(6)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(calendar("&from=2000-01-01&to=2000-01-02"))).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(calendar("&status=invalid"))).andExpect(status().isBadRequest());
        mvc.perform(get(calendar("&size=101"))).andExpect(status().isBadRequest());
    }
    @Test void responsesUseTheSharedPublicationCaches() throws Exception {
        event("PUBLISHED", 2, "Кубок");
        mvc.perform(get(calendar(""))).andExpect(header().string("X-Flounder-Cached", "false"));
        mvc.perform(get(calendar(""))).andExpect(header().string("X-Flounder-Cached", "true"));
        caches.getCache("publicCalendar").clear();
        mvc.perform(get(calendar(""))).andExpect(header().string("X-Flounder-Cached", "false"));
        String path="/api/v1/public/organizations/"+slug;
        mvc.perform(get(path)).andExpect(header().string("X-Flounder-Cached", "false"));
        mvc.perform(get(path)).andExpect(header().string("X-Flounder-Cached", "true"));
        caches.getCache("publicOrganization").clear();
        mvc.perform(get(path)).andExpect(header().string("X-Flounder-Cached", "false"));
    }
    @Test void fiftyEventsUseAtMostFourSqlStatements() throws Exception {
        for(int i=0;i<50;i++) event("PUBLISHED",i+1,"Старт "+i);
        statements.set(0);
        mvc.perform(get(calendar("&size=50"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(50)).andExpect(jsonPath("$.items.length()").value(50));
        assertThat(statements.get()).as("All JDBC prepared statements for 50 cards").isEqualTo(1);
    }
    @Test void organizationVisibilitySlugChangesAndUniqueSlug() throws Exception {
        event("PUBLISHED",2,"Кубок"); event("ARCHIVED",-2,"Архив");
        mvc.perform(get("/api/v1/public/organizations/"+slug)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.eventsTotal").value(2)).andExpect(jsonPath("$.stats.eventsUpcoming").value(1))
                .andExpect(jsonPath("$.upcoming.length()").value(1)).andExpect(jsonPath("$.past.length()").value(1));
        mvc.perform(get("/api/v1/public/organizations/"+slug+"/events?status=past")).andExpect(jsonPath("$.total").value(1));
        profiles.save(organization,new PublicOrganizationProfile(slug+"-new","Клуб","https://example.org",null,null,true,null));
        mvc.perform(get("/api/v1/public/organizations/"+slug)).andExpect(status().isNotFound());
        String next=slug+"-new";
        mvc.perform(get("/api/v1/public/organizations/"+next)).andExpect(status().isOk());
        profiles.save(organization,new PublicOrganizationProfile(next,null,null,null,null,false,null));
        mvc.perform(get("/api/v1/public/organizations/"+next)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("organizations.not_found"));
        // Use a real principal because profile endpoints use the application's principal boundary.
        var principal=new TestPrincipal();
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());
        UUID other=UUID.randomUUID();
        jdbc.update("insert into organizations(id,type,name,public_slug,created_at,updated_at) values (?,'CLUB','Другой','taken-slug',now(),now())",other);
        mvc.perform(put("/api/v1/organizations/"+organization+"/public-profile").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                .contentType(MediaType.APPLICATION_JSON).content("{\"publicSlug\":\"taken-slug\",\"isPublic\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("organizations.slug_taken"));
        for(String reserved: List.of("new","admin","api"))
            mvc.perform(put("/api/v1/organizations/"+organization+"/public-profile").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"publicSlug\":\""+reserved+"\",\"isPublic\":true}"))
                    .andExpect(status().isBadRequest());
    }
    static class TestPrincipal implements com.acme.sportplatform.identity.AuthenticatedUserPrincipal {
        public UUID getUserId() { return UUID.randomUUID(); }
        public List<org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        }
    }
    @Test void logosValidateSizeMagicAndPublicDownload() throws Exception {
        var principal=new TestPrincipal();
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());
        String path="/api/v1/organizations/"+organization+"/logo";
        for(var file:List.of(new MockMultipartFile("file","bad.svg","image/svg+xml","<svg/>".getBytes()),
                new MockMultipartFile("file","bad.png","image/png","wrong magic".getBytes()),
                new MockMultipartFile("file","large.png","image/png",new byte[2*1024*1024+1])))
            mvc.perform(multipart(path).file(file).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth)))
                    .andExpect(status().isBadRequest());
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        mvc.perform(multipart(path).file(new MockMultipartFile("file","logo.png","image/png",png))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))).andExpect(status().isOk());
        mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(content().bytes(png));
        mvc.perform(get("/api/v1/public/organizations/"+slug+"/logo")).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control","public, max-age=3600")).andExpect(content().bytes(png));
        mvc.perform(delete(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/public/organizations/"+slug+"/logo")).andExpect(status().isNotFound());
    }
}
