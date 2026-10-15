package com.acme.sportplatform.registrations.infrastructure.jpa;

import com.acme.sportplatform.registrations.api.PaymentSummary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class RegistrationPaymentJournal {
    private final JdbcTemplate jdbc;

    public RegistrationPaymentJournal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Entry(
            UUID id,
            String action,
            BigDecimal amount,
            String currency,
            String method,
            String reference,
            String comment,
            UUID performedBy,
            OffsetDateTime performedAt) {}

    public void append(
            RegistrationEntity r,
            String action,
            BigDecimal amount,
            UUID actor,
            OffsetDateTime now) {
        jdbc.update(
                """
                insert into registration_payment_log(id,registration_id,action,amount,currency,method,reference,comment,performed_by,performed_at)
                values (?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                r.getId(),
                action,
                amount,
                r.getFeeCurrency(),
                r.getPaymentMethod(),
                r.getPaymentReference(),
                r.getPaymentComment(),
                actor,
                now);
    }

    public List<Entry> history(UUID id) {
        return jdbc.query(
                "select * from registration_payment_log where registration_id=? order by"
                        + " performed_at,id",
                (r, n) ->
                        new Entry(
                                r.getObject("id", UUID.class),
                                r.getString("action"),
                                r.getBigDecimal("amount"),
                                r.getString("currency"),
                                r.getString("method"),
                                r.getString("reference"),
                                r.getString("comment"),
                                r.getObject("performed_by", UUID.class),
                                r.getObject("performed_at", OffsetDateTime.class)),
                id);
    }

    public PaymentSummary summary(UUID eventId, String defaultCurrency) {
        // One grouped query; no per-club or per-registration reads.
        var rows =
                jdbc.queryForList(
                        """
                        select coalesce(nullif(r.participant_snapshot->>'clubName',''),'Без клуба') club,
                          count(distinct r.participant_profile_id) participants, count(*) registrations,
                          coalesce(sum(r.fee_amount),0) due,
                          coalesce(sum(greatest(coalesce(r.paid_amount,r.fee_amount,0)-case when r.payment_status='REFUNDED' then coalesce(f.amount,0) else 0 end,0)) filter(where r.payment_status in ('PAID','REFUNDED')),0) paid,
                          coalesce(sum(r.fee_amount) filter(where r.payment_status='PENDING'),0) pending,
                          coalesce(sum(r.fee_amount) filter(where r.payment_status='WAIVED'),0) waived,
                          coalesce(sum(coalesce(f.amount,r.paid_amount,r.fee_amount)) filter(where r.payment_status='REFUNDED'),0) refunded,
                          array_agg(r.id order by r.id) filter(where r.payment_status='PENDING') pending_ids,
                          min(r.fee_currency) currency_min,max(r.fee_currency) currency_max
                        from registrations r left join
                          (select distinct on (registration_id) registration_id,amount from registration_payment_log where action='REFUND' order by registration_id,performed_at desc,id desc) f
                          on f.registration_id=r.id
                        where r.event_id=? and (r.status in ('CONFIRMED','PAID','ADMITTED')
                            or r.payment_status in ('PAID','WAIVED','REFUNDED'))
                        group by 1 order by 1
                        """,
                        eventId);
        BigDecimal due = BigDecimal.ZERO, paid = due, pending = due, waived = due, refunded = due;
        var clubs = new ArrayList<PaymentSummary.Club>();
        var currencies = new HashSet<String>();
        for (var r : rows) {
            for (String key : List.of("currency_min", "currency_max"))
                if (r.get(key) != null) currencies.add((String) r.get(key));
            due = due.add((BigDecimal) r.get("due"));
            paid = paid.add((BigDecimal) r.get("paid"));
            pending = pending.add((BigDecimal) r.get("pending"));
            waived = waived.add((BigDecimal) r.get("waived"));
            refunded = refunded.add((BigDecimal) r.get("refunded"));
            List<UUID> ids = List.of();
            if (r.get("pending_ids") instanceof java.sql.Array a)
                try {
                    ids = Arrays.asList((UUID[]) a.getArray());
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            clubs.add(
                    new PaymentSummary.Club(
                            (String) r.get("club"),
                            ((Number) r.get("participants")).longValue(),
                            ((Number) r.get("registrations")).longValue(),
                            (BigDecimal) r.get("due"),
                            (BigDecimal) r.get("paid"),
                            (BigDecimal) r.get("pending"),
                            ids));
        }
        if (currencies.size() > 1)
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "registrations.mixed_currencies",
                    "Сводка требует единой валюты взносов события");
        return new PaymentSummary(
                currencies.stream().findFirst().orElse(defaultCurrency),
                new PaymentSummary.Totals(due, paid, pending, waived, refunded),
                clubs);
    }
}
