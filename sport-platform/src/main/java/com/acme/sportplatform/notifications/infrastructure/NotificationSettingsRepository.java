package com.acme.sportplatform.notifications.infrastructure;

import com.acme.sportplatform.notifications.api.NotificationSetting;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationSettingsRepository {
    private final JdbcTemplate jdbc;
    public NotificationSettingsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean enabled(UUID organizationId, String template) {
        var rows = jdbc.queryForList("select enabled from organization_notification_settings where organization_id=? and template_code=?",
                Boolean.class, organizationId, template);
        return rows.isEmpty() || rows.getFirst();
    }
    public List<NotificationSetting> find(UUID id) {
        return jdbc.query("select template_code,enabled from organization_notification_settings where organization_id=?",
                (rs, n) -> new NotificationSetting(rs.getString(1), rs.getBoolean(2)), id);
    }
    public void save(UUID id, NotificationSetting setting) {
        jdbc.update("""
                insert into organization_notification_settings(organization_id,template_code,enabled) values (?,?,?)
                on conflict (organization_id,template_code) do update set enabled=excluded.enabled
                """, id, setting.templateCode(), setting.enabled());
    }
}
