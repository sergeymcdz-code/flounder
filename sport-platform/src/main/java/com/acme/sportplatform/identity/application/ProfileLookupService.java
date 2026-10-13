package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProfileLookupService implements ProfileLookup {

    private final ProfileRepository profileRepository;

    public ProfileLookupService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public java.util.List<ProfileLookupResult> findByIdIn(java.util.Collection<UUID> ids) {
        return profileRepository.findAllById(ids).stream().map(this::toResult).toList();
    }

    @Override
    public ProfileLookupResult getById(UUID id) {
        return toResult(
                profileRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new com.acme.sportplatform.common.exception
                                                .BusinessException(
                                                "identity.profile_not_found",
                                                "Профиль не найден")));
    }

    @Override
    public ProfileLookupResult getByUserId(UUID userId) {
        ProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Profile not found for user: " + userId
                ));

        return toResult(profile);
    }

    @Transactional
    public ProfileLookupResult findOrCreateByExternalKey(
            String externalKey, com.acme.sportplatform.identity.ProfileData data) {
        String key = normalizeKey(externalKey);
        var profile = profileRepository.findByExternalKey(key).orElse(null);
        if (profile == null) {
            profile = new ProfileEntity();
            profile.setExternalKey(key);
            apply(profile, data);
            profile = profileRepository.save(profile);
        }
        return toResult(profile);
    }

    @Transactional
    public ProfileLookupResult attachProfileToUser(String externalKey, UUID userId) {
        var profile =
                profileRepository
                        .findByExternalKey(normalizeKey(externalKey))
                        .orElseThrow(
                                () ->
                                        new com.acme.sportplatform.common.exception
                                                .BusinessException(
                                                "identity.profile_not_found", "Профиль не найден"));
        if (profile.getUserId() != null && !profile.getUserId().equals(userId))
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "identity.profile_already_attached",
                    "Профиль уже связан с другим пользователем");
        if (profileRepository
                .findByUserId(userId)
                .filter(p -> !p.getId().equals(profile.getId()))
                .isPresent())
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "identity.profile_already_attached", "У пользователя уже есть другой профиль");
        profile.setUserId(userId);
        return toResult(profileRepository.save(profile));
    }

    @Transactional
    public ProfileLookupResult upsertForUser(
            UUID userId, com.acme.sportplatform.identity.ProfileData data) {
        var profile = profileRepository.findByUserId(userId).orElse(null);
        String key =
                com.acme.sportplatform.identity.ProfileExternalKey.build(
                        data.lastName(), data.firstName(), data.birthDate(),data.gender());
        var imported = profileRepository.findByExternalKey(key).orElse(null);
        if (profile == null) {
            if (imported != null)
                profile =
                        profileRepository
                                .findById(attachProfileToUser(key, userId).id())
                                .orElseThrow();
            else {
                profile = new ProfileEntity();
                profile.setUserId(userId);
                profile.setExternalKey(key);
            }
        }
        if (profile.getExternalKey() == null || profile.getExternalKey().isBlank())
            profile.setExternalKey(key);
        apply(profile, data);
        return toResult(profileRepository.save(profile));
    }

    private String normalizeKey(String key) {
        return com.acme.sportplatform.identity.ProfileExternalKey.normalize(key);
    }

    private String normalizeGender(String gender) {
        return com.acme.sportplatform.identity.ProfileExternalKey.gender(gender)
                .toUpperCase(java.util.Locale.ROOT);
    }

    private void apply(ProfileEntity p, com.acme.sportplatform.identity.ProfileData d) {
        if (d == null
                || d.firstName() == null
                || d.firstName().isBlank()
                || d.lastName() == null
                || d.lastName().isBlank()
                || d.birthDate() == null
                || d.birthDate().isAfter(java.time.LocalDate.now())
                || d.gender() == null)
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "identity.invalid_profile", "Укажите фамилию, имя, дату рождения и пол");
        p.setFirstName(d.firstName().trim());
        p.setLastName(d.lastName().trim());
        if (d.middleName() != null)
        p.setMiddleName(d.middleName());
        p.setBirthDate(d.birthDate());
        p.setGender(normalizeGender(d.gender()));
        if (d.club() != null)
        p.setClubName(d.club());
    }

    private ProfileLookupResult toResult(ProfileEntity profile) {
        Map<String, Object> sportMeta = profile.getSportMeta() == null
                ? Map.of()
                : Map.copyOf(profile.getSportMeta());

        return new ProfileLookupResult(
                profile.getId(),
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getMiddleName(),
                profile.getBirthDate(),
                profile.getGender(),
                profile.getCity(),
                profile.getCountryCode(),
                profile.getClubName(),
                sportMeta
        );
    }
}
