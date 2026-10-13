package com.acme.sportplatform.identity.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.api.UserPageResponse;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.RoleRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final UserRoleAssignmentRepository assignments;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    public AdminUserService(UserRepository users, ProfileRepository profiles, UserRoleAssignmentRepository assignments,
                            RoleRepository roles, PasswordEncoder encoder) {
        this.users = users; this.profiles = profiles; this.assignments = assignments; this.roles = roles; this.encoder = encoder;
    }
    @Transactional(readOnly = true)
    public UserPageResponse search(String query, String role, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException("identity.invalid_page", "Номер страницы должен быть не меньше 0, размер — от 1 до 100");
        String q = query == null ? "" : query.trim();
        String r = role == null ? "" : role.trim();
        String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        var result = users.search(q, pattern, r, PageRequest.of(page, size));
        var items = result.getContent().stream().map(u -> {
            var p = profiles.findByUserId(u.getId()).orElse(null);
            return new UserPageResponse.Item(u.getId(), u.getEmail(), p == null ? null : p.getFirstName(),
                    p == null ? null : p.getLastName(), u.getStatus().toUpperCase(Locale.ROOT),
                    assignments.findRoleCodesByUserId(u.getId()).stream().distinct().sorted().toList(), u.getCreatedAt());
        }).toList();
        return new UserPageResponse(items, page, size, result.getTotalElements());
    }
    @Transactional
    public void changePassword(UUID id, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new BusinessException("identity.invalid_password", "Пароль не должен превышать 72 байта в UTF-8");
        var user = users.findById(id).orElseThrow(() -> new BusinessException("identity.user_not_found", "Пользователь не найден"));
        user.setPasswordHash(encoder.encode(password));
    }
    @Transactional(readOnly = true)
    public List<Role> roles() {
        return roles.findAll(Sort.by("code")).stream().map(r -> new Role(r.getCode(), r.getName())).toList();
    }
    public record Role(String code, String name) {}
}
