package com.acme.sportplatform.identity.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;

import static java.util.stream.Collectors.toMap;

@Service
@Transactional(readOnly = true)
public class UserLookupService implements UserLookup {
    private final UserRepository users;
    private final ProfileRepository profiles;
    public UserLookupService(UserRepository users, ProfileRepository profiles) {
        this.users = users; this.profiles = profiles;
    }
    public Optional<User> findByEmail(String email) { return users.findByEmail(com.acme.sportplatform.identity.EmailAddress.normalize(email)).map(this::map); }
    public Optional<User> findById(UUID id) { return users.findById(id).map(this::map); }
    public List<User> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        var foundUsers = users.findAllById(ids);
        var profilesByUser = profiles.findAllByUserIdIn(ids).stream().collect(toMap(ProfileEntity::getUserId, p -> p));
        return foundUsers.stream().map(user -> map(user, profilesByUser.get(user.getId()))).toList();
    }
    private User map(UserEntity user) {
        var profile = profiles.findByUserId(user.getId()).orElse(null);
        return map(user, profile);
    }
    private User map(UserEntity user, ProfileEntity profile) {
        return new User(user.getId(), user.getEmail(), profile == null ? null : profile.getFirstName(),
                profile == null ? null : profile.getLastName());
    }
}
