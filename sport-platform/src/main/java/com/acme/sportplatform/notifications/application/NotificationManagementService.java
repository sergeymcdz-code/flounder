package com.acme.sportplatform.notifications.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.UserMembershipLookup;
import com.acme.sportplatform.notifications.api.*;
import com.acme.sportplatform.notifications.domain.NotificationTemplate;
import com.acme.sportplatform.notifications.infrastructure.*;
import com.acme.sportplatform.organizations.OrganizationLookup;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationManagementService {
    private final NotificationSettingsRepository settings;
    private final NotificationOutboxRepository outbox;
    private final OrganizationLookup organizations;
    private final UserMembershipLookup memberships;
    private final EventAccessService access;
    public NotificationManagementService(NotificationSettingsRepository settings, NotificationOutboxRepository outbox,
            OrganizationLookup organizations, UserMembershipLookup memberships, EventAccessService access) {
        this.settings = settings; this.outbox = outbox; this.organizations = organizations; this.memberships = memberships; this.access = access;
    }
    @Transactional(readOnly = true)
    public List<NotificationSetting> settings(UUID id, AuthenticatedUserPrincipal principal) {
        assertOwner(id, principal);
        var saved = new HashMap<String, Boolean>();
        settings.find(id).forEach(s -> saved.put(s.templateCode(), s.enabled()));
        return NotificationTemplate.organizerTemplates().stream()
                .map(t -> new NotificationSetting(t.name(), saved.getOrDefault(t.name(), true))).toList();
    }
    public List<NotificationSetting> update(UUID id, List<NotificationSetting> changes, AuthenticatedUserPrincipal principal) {
        assertOwner(id, principal);
        if (changes == null) throw new BusinessException("notifications.invalid_settings", "Укажите настройки уведомлений");
        Set<String> seen = new HashSet<>();
        for (var setting : changes) {
            if (setting == null || setting.templateCode() == null || setting.enabled() == null)
                throw new BusinessException("notifications.invalid_settings", "Укажите шаблон и состояние уведомления");
            NotificationTemplate.organizerTemplate(setting.templateCode());
            if (!seen.add(setting.templateCode())) throw new BusinessException("notifications.duplicate_template", "Шаблон указан дважды");
        }
        changes.forEach(s -> settings.save(id, s));
        return settings(id, principal);
    }
    @Transactional(readOnly = true)
    public NotificationLogPage journal(UUID id, String status, int page, AuthenticatedUserPrincipal principal) {
        access.assertCanManage(id, principal);
        if (page < 0 || status != null && !Set.of("PENDING", "SENT", "FAILED").contains(status))
            throw new BusinessException("notifications.invalid_filter", "Недопустимый статус или номер страницы");
        return outbox.journal(id, status, page);
    }
    private void assertOwner(UUID id, AuthenticatedUserPrincipal principal) {
        if (!organizations.existsById(id)) throw new BusinessException("organizations.organization_not_found", "Организация не найдена");
        if (principal != null && principal.isPlatformAdmin()) return;
        if (principal != null && memberships.membershipsOf(principal.getUserId()).stream().anyMatch(m ->
                id.equals(m.organizationId()) && Set.of("owner", "admin").contains(m.memberRole().toLowerCase(Locale.ROOT)))) return;
        throw new BusinessException("organizations.access_denied", "Только владелец или администратор организации может менять уведомления");
    }
}
