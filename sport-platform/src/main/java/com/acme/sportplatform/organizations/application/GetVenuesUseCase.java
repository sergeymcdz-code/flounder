package com.acme.sportplatform.organizations.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.organizations.api.VenueResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueRepository;

@Service
public class GetVenuesUseCase {

    private final VenueRepository venueRepository;

    public GetVenuesUseCase(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> execute() {
        return venueRepository.findAll()
                .stream()
                .map(entity -> new VenueResponse(
                        entity.getId(),
                        entity.getName(),
                        entity.getCountryCode(),
                        entity.getCity(),
                        entity.getAddress(),
                        entity.getTimezone()
                ))
                .toList();
    }
}