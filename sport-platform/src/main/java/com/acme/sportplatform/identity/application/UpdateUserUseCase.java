package com.acme.sportplatform.identity.application;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.api.UpdateUserRequest;
import com.acme.sportplatform.identity.api.UserDetailsResponse;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;

@Service
public class UpdateUserUseCase {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final GetUserByIdUseCase getUser;

    public UpdateUserUseCase(UserRepository userRepository, ProfileRepository profileRepository, GetUserByIdUseCase getUser) {
        this.getUser = getUser;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public UserDetailsResponse execute(UUID userId, UpdateUserRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("identity.user_not_found", "Пользователь не найден"));
        
        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("identity.user_not_found", "Профиль пользователя не найден"));

        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getStatus() != null) {
            String status = request.getStatus().toUpperCase(Locale.ROOT);
            if (!Set.of("ACTIVE", "BLOCKED").contains(status))
                throw new BusinessException("identity.invalid_status", "Допустимые статусы: ACTIVE и BLOCKED");
            if ("BLOCKED".equals(status)) AdminSelfProtection.assertNotSelf(userId);
            user.setStatus(status.toLowerCase(Locale.ROOT));
        }

        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getMiddleName() != null) profile.setMiddleName(request.getMiddleName());
        if (request.getBirthDate() != null) profile.setBirthDate(request.getBirthDate());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getCountryCode() != null) profile.setCountryCode(request.getCountryCode());
        if (request.getClubName() != null) profile.setClubName(request.getClubName());

        userRepository.save(user);
        profileRepository.save(profile);

        return getUser.execute(userId);
    }
}
