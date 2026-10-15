package com.acme.sportplatform.organizations;

import java.util.UUID;

public record PublicOrganization(UUID id, String name, String publicSlug, String city,
        String description, String website, String publicEmail, String publicPhone, boolean hasLogo) {}
