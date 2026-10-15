package com.acme.sportplatform.organizations.api;

import java.util.UUID;

public record MyOrganizationResponse(UUID id, String name, String city, String memberRole, long eventsCount) {}
