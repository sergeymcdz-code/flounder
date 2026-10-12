package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.identity.ImportedProfileLookup;
import com.acme.sportplatform.identity.ImportedProfileRequest;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportedProfileLookupService implements ImportedProfileLookup {
    private final ProfileRepository profiles;
    private final EntityManager entityManager;

    public ImportedProfileLookupService(ProfileRepository profiles, EntityManager entityManager) {
        this.profiles = profiles;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result findOrCreateByExternalKey(ImportedProfileRequest request) {
        // Serialize concurrent imports for this athlete until the row transaction commits.
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .setParameter("key", request.externalKey()).getSingleResult();
        ProfileEntity profile = profiles.findByExternalKey(request.externalKey()).orElse(null);
        boolean created = profile == null;
        if (created) {
            profile = new ProfileEntity();
            profile.setExternalKey(request.externalKey());
            profile.setLastName(request.lastName());
            profile.setFirstName(request.firstName());
            profile.setMiddleName(request.middleName());
            profile.setBirthDate(request.birthDate());
            profile.setGender(request.gender());
        }
        if (profile.getClubName() == null || profile.getClubName().isBlank()) {
            profile.setClubName(request.club());
        }
        profile = profiles.saveAndFlush(profile);
        return new Result(new ProfileLookupResult(profile.getId(), profile.getUserId(), profile.getFirstName(),
                profile.getLastName(), profile.getMiddleName(), profile.getBirthDate(), profile.getGender(),
                profile.getCity(), profile.getCountryCode(), profile.getClubName(), profile.getSportMeta()), created);
    }
}
