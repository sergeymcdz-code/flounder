package com.acme.sportplatform.analytics.application;

import com.acme.sportplatform.analytics.api.*;
import com.acme.sportplatform.analytics.infrastructure.AnalyticsReadRepository;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.OrganizationLookup;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class AnalyticsService {
    private final EventAccessService access;
    private final OrganizationLookup organizations;
    private final AnalyticsReadRepository reads;
    private final Cache<UUID,EventAnalytics> events=Caffeine.newBuilder().maximumSize(500)
            .expireAfterWrite(Duration.ofSeconds(60)).build();
    public AnalyticsService(EventAccessService access,OrganizationLookup organizations,AnalyticsReadRepository reads) {
        this.access=access;this.organizations=organizations;this.reads=reads;
    }
    public EventAnalytics event(UUID id,AuthenticatedUserPrincipal principal) {
        // Authorization always precedes cache lookup, including XLSX and warm-cache requests.
        access.assertCanManage(id,principal);
        return events.get(id,reads::event);
    }
    public OrganizationAnalytics organization(UUID id,AuthenticatedUserPrincipal principal) {
        if(!organizations.existsById(id))
            throw new BusinessException("organizations.organization_not_found","Организация не найдена");
        if(principal==null || !principal.isPlatformAdmin() && !reads.organizationOwnerOrAdmin(id,principal.getUserId()))
            throw new BusinessException("organizations.access_denied","Нет доступа к аналитике организации");
        return reads.organization(id);
    }
}
