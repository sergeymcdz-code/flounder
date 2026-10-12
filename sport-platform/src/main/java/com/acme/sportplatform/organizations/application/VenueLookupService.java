package com.acme.sportplatform.organizations.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.organizations.VenueLookup;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueRepository;

@Service
public class VenueLookupService implements VenueLookup {

    private final VenueRepository venueRepository;

    public VenueLookupService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Override
    public boolean existsById(UUID venueId) {
        return venueRepository.existsById(venueId);
    }
}