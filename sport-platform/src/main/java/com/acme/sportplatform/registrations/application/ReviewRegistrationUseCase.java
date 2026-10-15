package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.ReviewRegistrationRequest;
import com.acme.sportplatform.registrations.domain.RegistrationReviewDecision;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
public class ReviewRegistrationUseCase {

    private final RegistrationFeeService fees;
    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    private final RegistrationCapacityService capacity;

    public ReviewRegistrationUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            org.springframework.context.ApplicationEventPublisher publisher,
            RegistrationCapacityService capacity,
            RegistrationFeeService fees
    ) {
        this.fees = fees;

        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
        this.publisher = publisher;
        this.capacity = capacity;
    }

        @Transactional
        public RegistrationResponse execute(UUID registrationId, UUID reviewerUserId, ReviewRegistrationRequest request) {
        var selectedEventId = registrationRepository.findEventIdById(registrationId).orElseThrow(() ->
                new BusinessException("registrations.notfound", "Заявка не найдена"));
        capacity.lock(selectedEventId);
        RegistrationEntity registration =
                registrationRepository
                        .lockById(registrationId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "registrations.notfound", "Заявка не найдена"));

        if (!java.util.Set.of("SUBMITTED", "UNDER_REVIEW", "WAITLISTED").contains(registration.getStatus())
                && !("CONFIRMED".equals(registration.getStatus()) && request.decision() == RegistrationReviewDecision.REJECTED)) {
            throw new BusinessException(
                    "registrations.notreviewable", "Рассмотреть можно только поданную заявку");
        }

        RegistrationStatus newStatus = switch (request.decision()) {
                case CONFIRMED -> RegistrationStatus.CONFIRMED;
                case REJECTED -> RegistrationStatus.REJECTED;
                case NEEDS_CORRECTION -> RegistrationStatus.NEEDS_CORRECTION;
        };

        if (!RegistrationStatus.valueOf(registration.getStatus()).canTransition(newStatus))
            throw new BusinessException(
                    "registrations.invalid_transition", "Недопустимый переход статуса заявки");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        registration.setStatus(newStatus.name());
        registration.setWaitlistPosition(null);
        registration.setReviewNote(request.reviewNote());
        registration.setReviewedByUserId(reviewerUserId);
        registration.setReviewedAt(now);
        registration.setUpdatedAt(now);

        if (newStatus == RegistrationStatus.CONFIRMED) fees.assign(registration);
        registrationRepository.saveAndFlush(registration);
        if (newStatus == RegistrationStatus.REJECTED || newStatus == RegistrationStatus.NEEDS_CORRECTION)
            capacity.promote(registration.getEventId());
        if (newStatus == RegistrationStatus.CONFIRMED)
            publisher.publishEvent(new com.acme.sportplatform.registrations.RegistrationConfirmedEvent(registrationId));
        if (newStatus == RegistrationStatus.REJECTED)
            publisher.publishEvent(new com.acme.sportplatform.registrations.RegistrationRejectedEvent(registrationId, request.reviewNote()));
        return registrationMapper.toResponse(registration);
        }

    private boolean requiresReviewNote(
            RegistrationReviewDecision decision
    ) {
        return decision == RegistrationReviewDecision.NEEDS_CORRECTION
                || decision == RegistrationReviewDecision.REJECTED;
    }
}