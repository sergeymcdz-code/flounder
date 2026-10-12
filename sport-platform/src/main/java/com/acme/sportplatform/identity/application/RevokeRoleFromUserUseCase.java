package com.acme.sportplatform.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.RoleEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.RoleRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;

@Service
public class RevokeRoleFromUserUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public RevokeRoleFromUserUseCase(UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Transactional
    public void execute(UUID userId, String roleCode) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("identity.user_not_found", "User not found"));

        RoleEntity role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException("identity.role_not_found", "Role not found"));

        userRoleAssignmentRepository
                .deleteByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(user.getId(), role.getId());
    }
}