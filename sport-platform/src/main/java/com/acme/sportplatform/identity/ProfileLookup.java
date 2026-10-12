package com.acme.sportplatform.identity;

import java.util.UUID;

public interface ProfileLookup {

    ProfileLookupResult getByUserId(UUID userId);
}