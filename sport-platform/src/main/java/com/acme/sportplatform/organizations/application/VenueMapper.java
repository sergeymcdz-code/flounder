package com.acme.sportplatform.organizations.application;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.organizations.api.VenueResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.VenueEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class VenueMapper {
    private static final TypeReference<Map<String, Object>> META_TYPE = new TypeReference<>() {};
    private final ObjectMapper objectMapper;

    public VenueMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serializeMeta(Map<String, Object> venueMeta) {
        try {
            return objectMapper.writeValueAsString(venueMeta == null ? Map.of() : venueMeta);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("organizations.invalid_venue_meta", "Некорректные метаданные площадки");
        }
    }

    public VenueResponse toResponse(VenueEntity entity) {
        try {
            Map<String, Object> venueMeta = objectMapper.readValue(entity.getVenueMeta(), META_TYPE);
            return new VenueResponse(entity.getId(), entity.getName(), entity.getCountryCode(), entity.getCity(),
                    entity.getAddress(), entity.getTimezone(), venueMeta == null ? Map.of() : venueMeta);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Некорректные метаданные площадки в базе данных", exception);
        }
    }
}
