package com.acme.sportplatform.registrations.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
@Transactional(readOnly = true)
public class ListMyRegistrationsUseCase {

    private final DocumentSummaryService summaries;
    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final com.acme.sportplatform.identity.ProfileLookup profiles;

    public ListMyRegistrationsUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            com.acme.sportplatform.identity.ProfileLookup profiles, DocumentSummaryService summaries) {
        this.summaries = summaries;
        this.profiles = profiles;
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
    }

    public List<RegistrationResponse> execute(UUID participantUserId) {
        return summaries.enrich(registrationRepository
                .findByParticipantProfileId(profiles.getByUserId(participantUserId).id())
                .stream()
                .map(registrationMapper::toResponse)
                .toList());
    }
}