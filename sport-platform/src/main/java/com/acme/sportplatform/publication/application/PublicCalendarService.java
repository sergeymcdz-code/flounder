package com.acme.sportplatform.publication.application;

import java.time.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.acme.sportplatform.organizations.OrganizationLookup;
import com.acme.sportplatform.publication.api.CalendarResponses.*;
import com.acme.sportplatform.publication.infrastructure.CalendarReadRepository;

@Service
public class PublicCalendarService {
    private final CalendarReadRepository reads;
    private final OrganizationLookup organizations;
    private final Clock clock;
    public PublicCalendarService(CalendarReadRepository reads, OrganizationLookup organizations, @Qualifier("publicationClock") Clock clock) {
        this.reads=reads; this.organizations=organizations; this.clock=clock;
    }
    public Calendar calendar(CalendarQuery q) { return reads.calendar(q, OffsetDateTime.now(clock)); }
    public OrganizationPage organization(String slug) {
        var o=organizations.publicBySlug(slug); var now=OffsetDateTime.now(clock);
        return new OrganizationPage(o,reads.stats(o.id(),now),calendar(new CalendarQuery(null,null,"",slug,"","upcoming",0,10)).items(),
                calendar(new CalendarQuery(null,null,"",slug,"","past",0,10)).items());
    }
    public void requireOrganization(String slug) { organizations.publicBySlug(slug); }
}
