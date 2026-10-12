package com.acme.sportplatform.identity.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.identity.api.UserDetailsResponse;
import com.acme.sportplatform.identity.domain.UserNotFoundException;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;

@Service
public class GetUserByIdUseCase {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public GetUserByIdUseCase(UserRepository userRepository,
                              ProfileRepository profileRepository,
                              UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    public UserDetailsResponse execute(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        List<String> roles = userRoleAssignmentRepository.findRoleCodesByUserId(userId);

        return new UserDetailsResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
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
                roles
        );
    }
}