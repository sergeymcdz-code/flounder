package com.acme.sportplatform.identity.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.identity.api.CurrentUserResponse;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;

@Service
public class GetCurrentUserUseCase {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public GetCurrentUserUseCase(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    public CurrentUserResponse execute(PlatformUserPrincipal principal) {
        List<String> roles = userRoleAssignmentRepository.findRoleCodesByUserId(principal.getUserId());

        return new CurrentUserResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.getStatus(),
                roles
        );
    }
}