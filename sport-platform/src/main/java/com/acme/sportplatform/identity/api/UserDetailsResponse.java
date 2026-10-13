package com.acme.sportplatform.identity.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.acme.sportplatform.identity.UserMembershipLookup;

public record UserDetailsResponse(
        UUID id,
        String email,
        String phone,
        String status,
        OffsetDateTime createdAt,
        Profile profile,
        List<Role> roles,
        List<UserMembershipLookup.Membership> memberships
) {
        public record Role(String code, String name, UUID organizationId, UUID eventId) {}
        public record Profile(
                String firstName,
                String lastName,
                String middleName,
                LocalDate birthDate,
                String gender,
                String city,
                String countryCode,
                String clubName
        ) {}
}
