package com.acme.sportplatform.notifications.infrastructure;

import com.acme.sportplatform.notifications.api.NotificationLogPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationOutboxRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public NotificationOutboxRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public record Pending(UUID id, String recipientEmail, String templateCode, Map<String, Object> payload, int attempts) {}
    public void enqueue(UUID eventId, String recipient, String template, Map<String, Object> payload, String key, Instant now) {
        try {
            jdbc.update("""
                    insert into notification_outbox(id,event_id,recipient_email,template_code,payload,delivery_key,created_at,next_attempt_at)
                    values (?,?,?,?,?::jsonb,?,?,?) on conflict (delivery_key) do nothing
                    """, UUID.randomUUID(), eventId, recipient, template, json.writeValueAsString(payload), key,
                    Timestamp.from(now), Timestamp.from(now));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Invalid mail payload", e); }
    }
    public List<UUID> dueBatch(Instant now) {
        return jdbc.query("""
                select id from notification_outbox
                where status='PENDING' and next_attempt_at<=? and attempts<5
                order by created_at,id limit 50
                """, (rs, n) -> rs.getObject("id", UUID.class), Timestamp.from(now));
    }
    // Recheck eligibility while locking one row in its delivery transaction.
    public Optional<Pending> lockPending(UUID id, Instant now) {
        var rows = jdbc.query("""
                select id,recipient_email,template_code,payload,attempts from notification_outbox
                where id=? and status='PENDING' and next_attempt_at<=? and attempts<5
                for update skip locked
                """, (rs, n) -> {
                    try {
                        return new Pending(rs.getObject("id", UUID.class), rs.getString("recipient_email"),
                                rs.getString("template_code"), json.readValue(rs.getString("payload"), new TypeReference<>() {}),
                                rs.getInt("attempts"));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new java.sql.SQLException("Invalid mail payload", e); }
                }, id, Timestamp.from(now));
        return rows.stream().findFirst();
    }
    public void sent(UUID id, Instant now) {
        jdbc.update("update notification_outbox set status='SENT',attempts=attempts+1,sent_at=?,last_error=null,payload='{}'::jsonb where id=?",
                Timestamp.from(now), id);
    }
    public void failed(Pending row, Instant now, String safeError) {
        int attempt = row.attempts() + 1;
        jdbc.update("update notification_outbox set attempts=?,status=?,last_error=?,next_attempt_at=?"
                        + (attempt >= 5 ? ",payload='{}'::jsonb" : "") + " where id=?",
                attempt, attempt >= 5 ? "FAILED" : "PENDING", safeError,
                Timestamp.from(now.plusSeconds(10L * (1L << (attempt - 1)))), row.id());
    }
    public NotificationLogPage journal(UUID eventId, String status, int page) {
        String filter = status == null ? "" : " and status=?";
        Object[] countArgs = status == null ? new Object[]{eventId} : new Object[]{eventId, status};
        Long count = jdbc.queryForObject("select count(*) from notification_outbox where event_id=?" + filter, Long.class, countArgs);
        Object[] args = status == null ? new Object[]{eventId, (long) page * 50} : new Object[]{eventId, status, (long) page * 50};
        var items = jdbc.query("""
                select id,recipient_email,template_code,status,attempts,last_error,created_at,sent_at
                from notification_outbox where event_id=?
                """ + filter + " order by created_at desc,id limit 50 offset ?", (rs, n) -> new NotificationLogPage.Item(
                rs.getObject("id", UUID.class), rs.getString("recipient_email"), rs.getString("template_code"),
                rs.getString("status"), rs.getInt("attempts"), rs.getString("last_error"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("sent_at", OffsetDateTime.class)), args);
        long total = count == null ? 0 : count;
        return new NotificationLogPage(items, page, total, (int) ((total + 49) / 50));
    }
}
