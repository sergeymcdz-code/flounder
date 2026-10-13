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
    private final VenueMapper venueMapper;

    public CreateVenueUseCase(VenueRepository venueRepository, VenueMapper venueMapper) {
        this.venueRepository = venueRepository;
        this.venueMapper = venueMapper;
    }

    @Transactional
    public VenueResponse execute(CreateVenueRequest request) {
        var meta = request.venueMeta();
        for (String field : java.util.List.of("lanes", "courseLength")) {
            Object value = meta.get(field);
            if (value == null) continue;
            var allowed = field.equals("lanes") ? java.util.Set.of(6, 8, 10) : java.util.Set.of(25, 50);
            if (!(value instanceof Integer number) || !allowed.contains(number))
                throw com.acme.sportplatform.common.RequestValidation.field(
                        "organizations.invalid_venue_meta", "venueMeta." + field,
                        field.equals("lanes") ? "Число дорожек должно быть 6, 8 или 10"
                                : "Длина бассейна должна быть 25 или 50 м");
        }
        VenueEntity entity = new VenueEntity();
        entity.setName(request.name());
        entity.setCountryCode(request.countryCode());
        entity.setCity(request.city());
        entity.setAddress(request.address());
        entity.setTimezone(request.timezone());
        entity.setVenueMeta(venueMapper.serializeMeta(request.venueMeta()));

        VenueEntity saved = venueRepository.save(entity);

        return venueMapper.toResponse(saved);
    }
}
