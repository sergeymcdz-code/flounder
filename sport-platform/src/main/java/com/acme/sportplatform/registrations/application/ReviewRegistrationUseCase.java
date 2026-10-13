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

    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public ReviewRegistrationUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            org.springframework.context.ApplicationEventPublisher publisher
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
        this.publisher = publisher;
    }

        @Transactional
        public RegistrationResponse execute(UUID registrationId, UUID reviewerUserId, ReviewRegistrationRequest request) {
        RegistrationEntity registration =
                registrationRepository
                        .lockById(registrationId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "registrations.notfound", "Заявка не найдена"));

        if (!java.util.Set.of("SUBMITTED", "UNDER_REVIEW").contains(registration.getStatus())) {
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
        registration.setReviewNote(request.reviewNote());
        registration.setReviewedByUserId(reviewerUserId);
        registration.setReviewedAt(now);
        registration.setUpdatedAt(now);

        registrationRepository.save(registration);
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