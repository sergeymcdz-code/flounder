package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.RegistrationPaymentLookup;
import com.acme.sportplatform.registrations.api.PaymentRequests.*;
import com.acme.sportplatform.registrations.api.PaymentSummary;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class RegistrationPaymentService implements RegistrationPaymentLookup {
    private final jakarta.persistence.EntityManager entityManager;
    private final com.acme.sportplatform.identity.ProfileLookup profiles;
    private final RegistrationRepository registrations;
    private final com.acme.sportplatform.events.EventLockService locks;
    private final RegistrationFeeService fees;
    private final RegistrationPaymentJournal journal;
    private final com.acme.sportplatform.identity.UserLookup users;

    public RegistrationPaymentService(
            RegistrationRepository registrations,
            com.acme.sportplatform.events.EventLockService locks,
            RegistrationFeeService fees,
            RegistrationPaymentJournal journal,
            jakarta.persistence.EntityManager entityManager,
            com.acme.sportplatform.identity.ProfileLookup profiles,
            com.acme.sportplatform.identity.UserLookup users) {
        this.users = users;
        this.entityManager = entityManager;
        this.profiles = profiles;
        this.registrations = registrations;
        this.locks = locks;
        this.fees = fees;
        this.journal = journal;
    }

    private RegistrationEntity get(UUID id) {
        return registrations
                .findById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "registrations.not_found", "Заявка не найдена"));
    }

    @Override
    @Transactional(readOnly = true)
    public UUID eventId(UUID id) {
        return get(id).getEventId();
    }

    // Provider callbacks retain their existing interface. Their actor is the paying account.
    @Override
    @Transactional
    public void markPaid(UUID id) {
        var r = get(id);
        UUID actor =
                r.getParticipantUserId() == null
                        ? profiles.getById(r.getParticipantProfileId()).userId()
                        : r.getParticipantUserId();
        markPaid(id, actor, new MarkPaid(null, Method.ONLINE, null, null, null));
    }

    @Transactional
    public void markPaid(UUID id, UUID actor, MarkPaid request) {
        var r = locked(id);
        apply(r, actor, "MARK_PAID", request == null ? MarkPaid.empty() : request);
    }

    @Transactional
    public void change(UUID id, UUID actor, String action, BigDecimal amount, String comment) {
        var r = locked(id);
        apply(r, actor, action, new MarkPaid(amount, null, null, comment, null));
    }

    private RegistrationEntity locked(UUID id) {
        locks.lockWritableForUpdate(eventId(id));
        var registration =
                registrations
                        .lockById(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "registrations.not_found", "Заявка не найдена"));
        entityManager.refresh(registration);
        assertPaymentEnabled(registration.getEventId());
        return registration;
    }

    public static void assertTransition(String status, String payment, String action) {
        if ("MARK_PAID".equals(action)
                && "PAID".equals(payment)
                && Set.of("CONFIRMED", "PAID", "ADMITTED").contains(status))
            throw new BusinessException("registrations.already_paid", "Оплата уже отмечена");
        boolean allowed =
                switch (action) {
                    case "MARK_PAID", "WAIVE" ->
                            Set.of("CONFIRMED", "ADMITTED").contains(status)
                                    && !Set.of("PAID", "WAIVED", "REFUNDED").contains(payment);
                    case "REFUND" ->
                            Set.of("PAID", "ADMITTED", "WITHDRAWN").contains(status)
                                    && "PAID".equals(payment);
                    case "RESET" ->
                            Set.of("PAID", "CONFIRMED", "ADMITTED", "WITHDRAWN").contains(status)
                                    && Set.of("PAID", "WAIVED", "REFUNDED").contains(payment);
                    default -> false;
                };
        if (!allowed)
            throw new BusinessException(
                    "registrations.payment_not_applicable",
                    "Операция оплаты недоступна для текущего статуса заявки");
    }

    private void assertPaymentEnabled(UUID eventId) {
        if (!fees.settingsForUpdate(eventId).enabled())
            throw new BusinessException(
                    "registrations.payment_disabled", "Оплата в событии выключена");
    }

    private void apply(RegistrationEntity r, UUID actor, String action, MarkPaid request) {
        assertTransition(r.getStatus(), r.getPaymentStatus(), action);
        if (request.amount() != null && request.amount().signum() < 0)
            throw new BusinessException("validation_error", "Сумма не может быть отрицательной");
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal amount =
                request.amount() == null
                        ? (Set.of("REFUND", "RESET").contains(action)
                                ? r.getPaidAmount()
                                : r.getFeeAmount())
                        : request.amount();
        if ("REFUND".equals(action)
                && amount != null
                && r.getPaidAmount() != null
                && amount.compareTo(r.getPaidAmount()) > 0)
            throw new BusinessException(
                    "validation_error", "Сумма возврата превышает оплаченную сумму");
        switch (action) {
            case "MARK_PAID" -> {
                r.setPaymentStatus("PAID");
                r.setPaidAmount(amount);
                r.setPaidAt(request.paidAt() == null ? now : request.paidAt());
                r.setPaidByUserId(actor);
                r.setPaymentMethod(request.method() == null ? "OTHER" : request.method().name());
                r.setPaymentReference(request.reference());
                if ("CONFIRMED".equals(r.getStatus())) r.setStatus("PAID");
            }
            case "WAIVE" -> {
                r.setPaymentStatus("WAIVED");
                if ("CONFIRMED".equals(r.getStatus())) r.setStatus("PAID");
            }
            case "REFUND" -> r.setPaymentStatus("REFUNDED");
            case "RESET" -> {
                r.setPaymentStatus("PENDING");
                if ("PAID".equals(r.getStatus())) r.setStatus("CONFIRMED");
            }
            default -> throw new IllegalArgumentException("Unknown payment action");
        }
        r.setPaymentComment(request.comment());
        r.setUpdatedAt(now);
        journal.append(r, action, amount, actor, now);
        if ("RESET".equals(action)) {
            r.setPaidAmount(null);
            r.setPaidAt(null);
            r.setPaidByUserId(null);
            r.setPaymentMethod(null);
            r.setPaymentReference(null);
        }
    }

    @Transactional
    public BulkResult bulk(UUID eventId, UUID actor, Bulk request) {
        locks.lockWritableForUpdate(eventId);
        assertPaymentEnabled(eventId);
        var selected =
                registrations.findAllById(request.registrationIds()).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        RegistrationEntity::getId,
                                        java.util.function.Function.identity()));
        var skipped = new ArrayList<Skipped>();
        int updated = 0;
        for (var id : new LinkedHashSet<>(request.registrationIds())) {
            var r = selected.get(id);
            if (r == null || !eventId.equals(r.getEventId())) {
                skipped.add(new Skipped(id, "Заявка не относится к этому событию"));
                continue;
            }
            try {
                apply(
                        r,
                        actor,
                        "MARK_PAID",
                        new MarkPaid(
                                null,
                                request.method(),
                                request.reference(),
                                request.comment(),
                                null));
                updated++;
            } catch (BusinessException e) {
                skipped.add(new Skipped(id, e.getMessage()));
            }
        }
        return new BulkResult(updated, skipped);
    }

    @Transactional
    public int recalculate(UUID eventId) {
        locks.lockWritableForUpdate(eventId);
        int count = 0;
        var rows =
                registrations.findByEventId(eventId).stream()
                        .sorted(
                                Comparator.comparing(RegistrationEntity::getCreatedAt)
                                        .thenComparing(RegistrationEntity::getId))
                        .toList();
        for (var r : rows)
            if (Set.of("CONFIRMED", "PAID").contains(r.getStatus())
                    && r.getFeeAmount() == null
                    && !Set.of("PAID", "WAIVED", "REFUNDED").contains(r.getPaymentStatus())) {
                fees.assign(r);
                r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                count++;
            }
        return count;
    }

    @Transactional(readOnly = true)
    public PaymentSummary summary(UUID eventId) {
        return journal.summary(eventId, fees.settings(eventId).currency());
    }

    @Transactional(readOnly = true)
    public List<com.acme.sportplatform.registrations.api.PaymentLogEntry> history(UUID id) {
        var entries = journal.history(id);
        var ids =
                entries.stream()
                        .map(RegistrationPaymentJournal.Entry::performedBy)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
        var names =
                ids.isEmpty()
                        ? Map.<UUID, String>of()
                        : users.findAllByIds(ids).stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                com.acme.sportplatform.identity.UserLookup.User::id,
                                                user ->
                                                        java.util.stream.Stream.of(
                                                                        user.firstName(),
                                                                        user.lastName())
                                                                .filter(Objects::nonNull)
                                                                .collect(
                                                                        java.util.stream.Collectors
                                                                                .joining(" "))));
        return entries.stream()
                .map(
                        e ->
                                new com.acme.sportplatform.registrations.api.PaymentLogEntry(
                                        e.id(),
                                        e.action(),
                                        e.amount(),
                                        e.currency(),
                                        e.method(),
                                        e.reference(),
                                        e.comment(),
                                        e.performedBy(),
                                        e.performedBy() == null
                                                ? ""
                                                : names.getOrDefault(e.performedBy(), ""),
                                        e.performedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentFee paymentFee(UUID id) {
        var r = get(id);
        var s = fees.settings(r.getEventId());
        return new PaymentFee(
                s.enabled(),
                s.feeMode() == com.acme.sportplatform.events.EventPaymentSettings.FeeMode.PER_EVENT,
                r.getFeeAmount(),
                r.getFeeCurrency(),
                r.getPaymentStatus());
    }
}
