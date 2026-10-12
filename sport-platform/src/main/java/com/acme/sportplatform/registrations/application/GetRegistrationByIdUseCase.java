package com.acme.sportplatform.registrations.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
public class GetRegistrationByIdUseCase {

    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;

    public GetRegistrationByIdUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
    }

    public RegistrationResponse execute(UUID registrationId) {
        RegistrationEntity registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "registrations.not_found",
                        "Registration not found: " + registrationId
                ));

        return registrationMapper.toResponse(registration);
    }
}