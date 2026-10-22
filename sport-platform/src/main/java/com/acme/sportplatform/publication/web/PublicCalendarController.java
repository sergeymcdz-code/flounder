package com.acme.sportplatform.publication.web;

import java.time.*;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.Objects;
import com.acme.sportplatform.publication.api.CalendarResponses.*;
import com.acme.sportplatform.publication.application.*;

@RestController
@RequestMapping("/api/v1/public")
public class PublicCalendarController {
    private final PublicCalendarService service;
    private final Cache calendars;
    private final Cache organizations;
    public PublicCalendarController(PublicCalendarService service, @Qualifier("publicationCacheManager") CacheManager caches) {
        this.service=service;
        this.calendars=Objects.requireNonNull(caches.getCache("publicCalendar"));
        this.organizations=Objects.requireNonNull(caches.getCache("publicOrganization"));
    }
    @GetMapping("/calendar")
    public ResponseEntity<Calendar> calendar(@RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to,
            @RequestParam(defaultValue="") String city, @RequestParam(defaultValue="") String organization, @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="upcoming") String status, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return cached(calendars,new CalendarQuery(from,to,city,organization,q,status,page,size),() -> service.calendar(new CalendarQuery(from,to,city,organization,q,status,page,size)));
    }
    @GetMapping("/organizations/{slug}")
    public ResponseEntity<OrganizationPage> organization(@PathVariable String slug) {
        // Visibility and slug changes apply immediately, including during the response cache TTL.
        service.requireOrganization(slug);
        return cached(organizations,slug,() -> service.organization(slug));
    }
    @GetMapping("/organizations/{slug}/events")
    public ResponseEntity<Calendar> events(@PathVariable String slug, @RequestParam(defaultValue="upcoming") String status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        service.requireOrganization(slug);
        var key=new CalendarQuery(null,null,"",slug,"",status,page,size);
        return cached(calendars,key,() -> service.calendar(key));
    }
    private <K,V> ResponseEntity<V> cached(Cache cache,K key,Supplier<V> supplier) {
        boolean hit=cache.get(key)!=null;
        V body;
        try { body=cache.get(key,supplier::get); }
        catch (Cache.ValueRetrievalException ex) {
            if (ex.getCause() instanceof RuntimeException cause) throw cause;
            throw ex;
        }
        return ResponseEntity.ok().header("Cache-Control","public, max-age=15").header("X-Flounder-Cached",Boolean.toString(hit)).body(body);
    }
}
