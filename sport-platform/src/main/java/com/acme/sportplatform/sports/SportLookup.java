package com.acme.sportplatform.sports;

import java.util.UUID;

public interface SportLookup {
    boolean existsById(UUID sportId);
}