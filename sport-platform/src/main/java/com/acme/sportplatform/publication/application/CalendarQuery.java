package com.acme.sportplatform.publication.application;

import java.time.LocalDate;
import java.util.Set;
import com.acme.sportplatform.common.exception.BusinessException;

public record CalendarQuery(LocalDate from, LocalDate to, String city, String organization, String q, String status, int page, int size) {
    public CalendarQuery {
        city = city == null ? "" : city.strip(); organization = organization == null ? "" : organization.strip();
        q = q == null ? "" : q.strip(); status = status == null || status.isBlank() ? "upcoming" : status;
        if (!Set.of("upcoming", "past", "all").contains(status) || page < 0 || size < 1 || size > 100
                || city.length() > 128 || organization.length() > 128 || q.length() > 200 || (from != null && to != null && from.isAfter(to)))
            throw new BusinessException("publication.invalid_query", "Проверьте фильтры, даты и размер страницы (1–100)");
    }
}
