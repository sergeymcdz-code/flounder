package com.acme.sportplatform.identity.application;

import java.util.Locale;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.acme.sportplatform.common.exception.BusinessException;
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
        com.acme.sportplatform.identity.domain.PasswordPolicy.validate(request.password());
        List<String> roleCodes = List.of("participant");
        if (request.roles() != null) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority())))
                throw new BusinessException("identity.access_denied", "Только администратор платформы может назначать роли при создании пользователя");
            roleCodes = request.roles().stream().distinct().toList();
        }

        String email = com.acme.sportplatform.identity.EmailAddress.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException();
        }

        String externalKey =
                com.acme.sportplatform.identity.ProfileExternalKey.build
                (request.lastName(), request.firstName(), request.birthDate(),
                        request.gender() == null ? "X" : request.gender());
        ProfileEntity imported = profileRepository.findByExternalKey(externalKey).orElse(null);
        if (imported != null && imported.getUserId() == null)
            throw new BusinessException("identity.profile_already_attached",
                    "Спортсмен с такими ФИО, датой рождения и полом уже есть в списке заявок. Для привязки аккаунта обратитесь к организатору");

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus("active");

        UserEntity savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            String detail = ex.getMostSpecificCause().getMessage();
            if (detail != null && (detail.contains("ux_users_email_lower") || detail.contains("users_email_key")))
                throw new UserAlreadyExistsException();
            throw ex;
        }

        ProfileEntity profile = new ProfileEntity();
        profile.setUserId(savedUser.getId());
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setMiddleName(request.middleName());
        profile.setBirthDate(request.birthDate());
        profile.setGender(request.gender() == null ? "X" : request.gender());
        profile.setCity(request.city());
        profile.setCountryCode(request.countryCode());
        profile.setClubName(request.clubName());
        profileRepository.save(profile);

        for (String roleCode : roleCodes) {
            RoleEntity role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new BusinessException("identity.role_not_found", "Роль не найдена"));

            UserRoleAssignmentEntity assignment = new UserRoleAssignmentEntity();
            assignment.setUserId(savedUser.getId());
            assignment.setRoleId(role.getId());
            userRoleAssignmentRepository.save(assignment);
        }

        return new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getStatus().toUpperCase(Locale.ROOT),
                savedUser.getCreatedAt()
        );
    }
}
