package com.acme.sportplatform.imports.domain;

import com.acme.sportplatform.identity.ProfileData;
import com.acme.sportplatform.identity.ProfileExternalKey;

public record ImportRow(ProfileData profile, String disciplineCode, Long seedTimeMs) {
    public String externalKey() {
        return ProfileExternalKey.build(profile.lastName(), profile.firstName(), profile.birthDate(), profile.gender());
    }

    public String key() { return externalKey() + "|" + disciplineCode; }
}
