package com.acme.sportplatform.organizations.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.organizations.api.CreateVenueRequest;
import com.acme.sportplatform.organizations.api.VenueResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueRepository;

@Service
public class CreateVenueUseCase {

    private final VenueRepository venueRepository;

    public CreateVenueUseCase(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional
    public VenueResponse execute(CreateVenueRequest request) {
        VenueEntity entity = new VenueEntity();
        entity.setName(request.name());
        entity.setCountryCode(request.countryCode());
        entity.setCity(request.city());
        entity.setAddress(request.address());
        entity.setTimezone(request.timezone());
        entity.setVenueMeta("{}");

        VenueEntity saved = venueRepository.save(entity);

        return new VenueResponse(
                saved.getId(),
                saved.getName(),
                saved.getCountryCode(),
                saved.getCity(),
                saved.getAddress(),
                saved.getTimezone()
        );
    }
}