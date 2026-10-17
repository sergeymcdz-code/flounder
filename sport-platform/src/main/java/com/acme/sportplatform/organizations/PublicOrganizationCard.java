package com.acme.sportplatform.organizations;

import java.util.UUID;

public record PublicOrganizationCard(UUID id, String name, String publicSlug, String city, boolean hasLogo) {}
