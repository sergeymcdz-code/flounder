package com.acme.sportplatform.identity;

public interface ImportedProfileLookup {
    Result findOrCreateByExternalKey(ImportedProfileRequest request);

    record Result(ProfileLookupResult profile, boolean created) {}
}
