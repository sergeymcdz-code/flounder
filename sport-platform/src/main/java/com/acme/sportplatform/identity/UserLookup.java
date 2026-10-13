package com.acme.sportplatform.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserLookup {
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    List<User> findAllByIds(Collection<UUID> ids);
    record User(UUID id, String email, String firstName, String lastName) {}
}
