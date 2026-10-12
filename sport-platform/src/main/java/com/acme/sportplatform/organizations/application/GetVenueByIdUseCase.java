package com.acme.sportplatform.organizations.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.organizations.api.VenueResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueRepository;

@Service
public class GetVenueByIdUseCase {

    private final VenueRepository venueRepository;

    public GetVenueByIdUseCase(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional(readOnly = true)
    public VenueResponse execute(UUID venueId) {
        VenueEntity entity = venueRepository.findById(venueId)
                .orElseThrow(() -> new BusinessException(
                        "organizations.venue_not_found",
                        "Venue not found"
                ));

        return new VenueResponse(
                entity.getId(),
                entity.getName(),
                entity.getCountryCode(),
                entity.getCity(),
                entity.getAddress(),
                entity.getTimezone()
        );
    }
}