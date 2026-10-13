package com.acme.sportplatform.organizations.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.organizations.api.VenueResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueRepository;

@Service
public class GetVenuesUseCase {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;

    public GetVenuesUseCase(VenueRepository venueRepository, VenueMapper venueMapper) {
        this.venueRepository = venueRepository;
        this.venueMapper = venueMapper;
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> execute() {
        return venueRepository.findAll()
                .stream()
                .map(venueMapper::toResponse)
                .toList();
    }
}