package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.identity.api.CreateUserRequest;
import com.acme.sportplatform.identity.api.UserResponse;
import com.acme.sportplatform.identity.domain.UserAlreadyExistsException;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.RoleEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.RoleRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CreateUserUseCase {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final PasswordEncoder passwordEncoder;

    public CreateUserUseCase(UserRepository userRepository,
                             ProfileRepository profileRepository,
                             RoleRepository roleRepository,
                             UserRoleAssignmentRepository userRoleAssignmentRepository,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.roleRepository = roleRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse execute(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }

        UserEntity user = new UserEntity();
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus("active");

        UserEntity savedUser = userRepository.save(user);

        ProfileEntity profile = new ProfileEntity();
        profile.setUserId(savedUser.getId());
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setMiddleName(request.middleName());
        profile.setBirthDate(request.birthDate());
        profile.setGender(request.gender());
        profile.setCity(request.city());
        profile.setCountryCode(request.countryCode());
        profile.setClubName(request.clubName());
        profileRepository.save(profile);

        RoleEntity participantRole = roleRepository.findByCode("participant")
                .orElseThrow(() -> new IllegalStateException("Default participant role not found"));

        UserRoleAssignmentEntity assignment = new UserRoleAssignmentEntity();
        assignment.setUserId(savedUser.getId());
        assignment.setRoleId(participantRole.getId());
        userRoleAssignmentRepository.save(assignment);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getStatus(),
                savedUser.getCreatedAt()
        );
    }
}