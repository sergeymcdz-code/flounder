package com.acme.sportplatform.publication.api;

import java.time.*;
import java.util.List;
import com.acme.sportplatform.organizations.PublicOrganization;

public final class CalendarResponses {
    private CalendarResponses() {}
    public record Organization(String name, String publicSlug, boolean hasLogo) {}
    public record Card(String slug, String title, LocalDate startDate, LocalDate endDate, String city, String venueName, String venueTimezone,
            Organization organization, String status, boolean registrationOpen, OffsetDateTime registrationCloseAt,
            long participantsCount, long disciplinesCount, boolean hasResults) {}
    public record Calendar(List<Card> items, int page, int size, long total, List<String> cities) {}
    public record Stats(long eventsTotal, long eventsUpcoming, long participantsTotal) {}
    public record OrganizationPage(String name, String publicSlug, String city, String description, String website,
            String publicEmail, String publicPhone, boolean hasLogo, Stats stats, List<Card> upcoming, List<Card> past) {
        public OrganizationPage(PublicOrganization o, Stats stats, List<Card> upcoming, List<Card> past) {
            this(o.name(), o.publicSlug(), o.city(), o.description(), o.website(), o.publicEmail(), o.publicPhone(), o.hasLogo(), stats, upcoming, past);
        }
    }
}
