package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListMyRegistrationsUseCase {

    private final DocumentSummaryService summaries;
    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final RegistrationFeeService fees;
    private final com.acme.sportplatform.identity.ProfileLookup profiles;

    public ListMyRegistrationsUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            com.acme.sportplatform.identity.ProfileLookup profiles,
            DocumentSummaryService summaries,
            RegistrationFeeService fees) {
        this.summaries = summaries;
        this.fees = fees;
        this.profiles = profiles;
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
    }

    public List<RegistrationResponse> execute(UUID participantUserId) {
        var entities =
                registrationRepository.findByParticipantProfileId(
                        profiles.getByUserId(participantUserId).id());
        return summaries.enrich(
                registrationMapper.toResponses(
                        entities,
                        fees.dueDaysFor(
                                entities.stream()
                                        .map(r -> r.getEventId())
                                        .collect(java.util.stream.Collectors.toSet()))));
    }
}
