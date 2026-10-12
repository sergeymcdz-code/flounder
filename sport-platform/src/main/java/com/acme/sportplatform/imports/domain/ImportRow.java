package com.acme.sportplatform.imports.domain;

import com.acme.sportplatform.identity.ImportedProfileRequest;

public record ImportRow(ImportedProfileRequest profile, String disciplineCode, Long seedTimeMs) {
    public String key() { return profile.externalKey() + "|" + disciplineCode; }
}
