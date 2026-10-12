package com.acme.sportplatform.identity.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;

@Service
@Transactional(readOnly = true)
public class ProfileLookupService implements ProfileLookup {

    private final ProfileRepository profileRepository;

    public ProfileLookupService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public ProfileLookupResult getByUserId(UUID userId) {
        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Profile not found for user: " + userId
                ));

        Map<String, Object> sportMeta = profile.getSportMeta() == null
                ? Map.of()
                : Map.copyOf(profile.getSportMeta());

        return new ProfileLookupResult(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getMiddleName(),
                profile.getBirthDate(),
                profile.getGender(),
                profile.getCity(),
                profile.getCountryCode(),
                profile.getClubName(),
                sportMeta
        );
    }
}