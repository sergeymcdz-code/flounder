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

    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;

    public ListMyRegistrationsUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
    }

    public List<RegistrationResponse> execute(UUID participantUserId) {
        return registrationRepository
                .findByParticipantUserId(participantUserId)
                .stream()
                .map(registrationMapper::toResponse)
                .toList();
    }
}