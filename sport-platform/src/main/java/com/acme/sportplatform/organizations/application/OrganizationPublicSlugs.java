package com.acme.sportplatform.organizations.application;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The event generator is internal to events; preserve its transliteration rule here. */
@Component
public class OrganizationPublicSlugs implements ApplicationRunner {
    private static final String CYRILLIC = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String[] LATIN = {"a","b","v","g","d","e","yo","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f","kh","ts","ch","sh","shch","","y","","e","yu","ya"};
    private final NamedParameterJdbcTemplate jdbc;
    public OrganizationPublicSlugs(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        lock();
        var rows=jdbc.queryForList("select id,name from organizations where public_slug is null order by created_at,id for update",Map.of());
        for(var row:rows) assign((UUID) row.get("id"),(String) row.get("name"));
    }
    public void lock() {
        jdbc.getJdbcTemplate().execute("select pg_advisory_xact_lock(803908)");
    }
    @Transactional
    public void ensure(UUID id) {
        lock();
        var rows=jdbc.queryForList("select id,name from organizations where id=:id and public_slug is null for update",Map.of("id",id));
        if(!rows.isEmpty()) assign(id,(String) rows.getFirst().get("name"));
    }
    private void assign(UUID id,String name) {
        String base=base(name), slug=base; int suffix=2;
        while(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from organizations where public_slug=:slug)",Map.of("slug",slug),Boolean.class)))
            slug=base+"-"+suffix++;
        jdbc.update("update organizations set public_slug=:slug where id=:id and public_slug is null",Map.of("slug",slug,"id",id));
    }
    static String base(String name) {
        var value=new StringBuilder();
        for(char c:(name==null ? "" : name).toLowerCase(Locale.ROOT).toCharArray()) {
            int index=CYRILLIC.indexOf(c); value.append(index<0 ? String.valueOf(c) : LATIN[index]);
        }
        String base=value.toString().replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");
        if(base.length()<3 || Set.of("new","admin","api").contains(base)) base="org-"+base;
        if(base.length()>110) base=base.substring(0,110).replaceAll("-+$","");
        return base;
    }
}
