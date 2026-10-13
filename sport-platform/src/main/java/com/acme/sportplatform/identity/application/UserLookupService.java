package com.acme.sportplatform.identity.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserLookupService implements UserLookup {
    private final UserRepository users;
    private final ProfileRepository profiles;
    public UserLookupService(UserRepository users, ProfileRepository profiles) {
        this.users = users; this.profiles = profiles;
    }
    public Optional<User> findByEmail(String email) { return users.findByEmailIgnoreCase(email.trim()).map(this::map); }
    public Optional<User> findById(UUID id) { return users.findById(id).map(this::map); }
    private User map(UserEntity user) {
        var profile = profiles.findByUserId(user.getId()).orElse(null);
        return new User(user.getId(), user.getEmail(), profile == null ? null : profile.getFirstName(),
                profile == null ? null : profile.getLastName());
    }
}
