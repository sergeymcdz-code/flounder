package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.UserRoleLookup;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.RoleEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.RoleRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignRoleToUserUseCase implements UserRoleLookup {

    @Override
    @Transactional(readOnly = true)
    public void requireUser(UUID userId) {
        if (!userRepository.existsById(userId)) throw new BusinessException("identity.user_not_found", "Пользователь не найден");
    }

    @Override
    @Transactional
    public void ensureOrganizer(UUID userId) { execute(userId, "organizer"); }

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public AssignRoleToUserUseCase(UserRepository userRepository,
                                   RoleRepository roleRepository,
                                   UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Transactional
    public boolean execute(UUID userId, String roleCode) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("identity.user_not_found", "Пользователь не найден"));

        RoleEntity role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException("identity.role_not_found", "Роль не найдена"));

        boolean exists = userRoleAssignmentRepository
                .existsByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(user.getId(), role.getId());

        if (exists) {
            return false;
        }

        UserRoleAssignmentEntity assignment = new UserRoleAssignmentEntity();
        assignment.setUserId(user.getId());
        assignment.setRoleId(role.getId());
        assignment.setEventId(null);
        assignment.setOrganizationId(null);

        userRoleAssignmentRepository.save(assignment);
        return true;
    }
}