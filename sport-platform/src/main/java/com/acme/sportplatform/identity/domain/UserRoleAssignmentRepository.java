package com.acme.sportplatform.identity.domain;

import java.util.UUID;

public interface UserRoleAssignmentRepository {

    boolean existsGlobalAssignment(UUID userId, UUID roleId);

    void assignGlobalRole(UUID userId, UUID roleId);

    void revokeGlobalRole(UUID userId, UUID roleId);
}