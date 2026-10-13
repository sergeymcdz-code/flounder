package com.acme.sportplatform.identity.application;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.identity.UserMembershipLookup;
import com.acme.sportplatform.identity.api.UserDetailsResponse;
import com.acme.sportplatform.identity.domain.UserNotFoundException;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;

@Service
public class GetUserByIdUseCase {

    private final UserMembershipLookup memberships;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public GetUserByIdUseCase(UserRepository userRepository,
                              ProfileRepository profileRepository,
                              UserRoleAssignmentRepository userRoleAssignmentRepository, UserMembershipLookup memberships) {
        this.memberships = memberships;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public UserDetailsResponse execute(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        var roles = userRoleAssignmentRepository.findRolesByUserId(userId);

        return new UserDetailsResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus().toUpperCase(Locale.ROOT),
                user.getCreatedAt(),
                new UserDetailsResponse.Profile(
                        profile.getFirstName(),
                        profile.getLastName(),
                        profile.getMiddleName(),
                        profile.getBirthDate(),
                        profile.getGender(),
                        profile.getCity(),
                        profile.getCountryCode(),
                        profile.getClubName()
                ),
                roles, memberships.membershipsOf(userId)
        );
    }
}
