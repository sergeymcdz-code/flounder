package com.acme.sportplatform.identity;

import java.util.UUID;

public interface ProfileLookup {

    ProfileLookupResult getByUserId(UUID userId);

    ProfileLookupResult getById(UUID profileId);

    java.util.List<ProfileLookupResult> findByIdIn(java.util.Collection<UUID> ids);

    ProfileLookupResult findOrCreateByExternalKey(String externalKey, ProfileData data);

    ProfileLookupResult attachProfileToUser(String externalKey, UUID userId);

    ProfileLookupResult upsertForUser(UUID userId, ProfileData data);
}
