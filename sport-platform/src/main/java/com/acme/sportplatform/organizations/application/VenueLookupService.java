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
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ProtocolVenue protocolVenue(UUID venueId) {
        var venue = venueRepository.findById(venueId).orElseThrow(() ->
                new com.acme.sportplatform.common.exception.BusinessException(
                        "organizations.venue_not_found", "Площадка не найдена"));
        return new ProtocolVenue(venue.getName(), venue.getCity(), venue.getTimezone(), venue.getVenueMeta());
    }

    @Override
    public String getMetadata(UUID venueId) {
        return venueRepository.findById(venueId).orElseThrow(() -> new com.acme.sportplatform.common.exception.BusinessException(
                "organizations.venue_not_found", "Площадка не найдена")).getVenueMeta();
    }

    @Override
    public boolean existsById(UUID venueId) {
        return venueRepository.existsById(venueId);
    }
}
