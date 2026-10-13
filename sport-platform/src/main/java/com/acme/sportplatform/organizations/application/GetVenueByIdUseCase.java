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
    private final VenueMapper venueMapper;

    public GetVenueByIdUseCase(VenueRepository venueRepository, VenueMapper venueMapper) {
        this.venueRepository = venueRepository;
        this.venueMapper = venueMapper;
    }

    @Transactional(readOnly = true)
    public VenueResponse execute(UUID venueId) {
        VenueEntity entity = venueRepository.findById(venueId)
                .orElseThrow(() -> new BusinessException(
                        "organizations.venue_not_found",
                        "Площадка не найдена"
                ));

        return venueMapper.toResponse(entity);
    }
}