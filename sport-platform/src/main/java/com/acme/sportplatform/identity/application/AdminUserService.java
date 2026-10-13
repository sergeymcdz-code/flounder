package com.acme.sportplatform.identity.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.api.UserPageResponse;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.RoleRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final UserRoleAssignmentRepository assignments;
    private final RoleRepository roles;
    private final PasswordService passwords;
    public AdminUserService(UserRepository users, ProfileRepository profiles, UserRoleAssignmentRepository assignments,
                            RoleRepository roles, PasswordService passwords) {
        this.users = users; this.profiles = profiles; this.assignments = assignments; this.roles = roles; this.passwords = passwords;
    }
    @Transactional(readOnly = true)
    public UserPageResponse search(String query, String role, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException("identity.invalid_page", "Номер страницы должен быть не меньше 0, размер — от 1 до 100");
        String q = query == null ? "" : query.trim();
        String r = role == null ? "" : role.trim();
        String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        var result = users.search(q, pattern, r, PageRequest.of(page, size));
        // Count the complete filtered result in the page query. Empty later pages need a separate count.
        long total = result.isEmpty() ? (page == 0 ? 0 : users.countSearch(q, pattern, r)) : result.getFirst().getTotal();
        var userIds = result.stream().map(u -> u.getId()).toList();
        Map<UUID, ProfileEntity> profilesByUser = userIds.isEmpty() ? Map.of() : profiles.findAllByUserIdIn(userIds).stream()
                .collect(toMap(ProfileEntity::getUserId, p -> p));
        Map<UUID, List<String>> rolesByUser = userIds.isEmpty() ? Map.of() : assignments.findRoleCodesByUserIds(userIds).stream()
                .collect(groupingBy(row -> (UUID) row[0], mapping(row -> (String) row[1], toList())));
        var items = result.stream().map(u -> {
            var p = profilesByUser.get(u.getId());
            return new UserPageResponse.Item(u.getId(), u.getEmail(), p == null ? null : p.getFirstName(),
                    p == null ? null : p.getLastName(), u.getStatus().toUpperCase(Locale.ROOT),
                    rolesByUser.getOrDefault(u.getId(), List.of()).stream().distinct().sorted().toList(), u.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        }).toList();
        return new UserPageResponse(items, page, size, total);
    }
    @Transactional
    public void changePassword(UUID id, String password) {
        passwords.changeByAdministrator(id, password);
    }
    @Transactional(readOnly = true)
    public List<Role> roles() {
        return roles.findAll(Sort.by("code")).stream().map(r -> new Role(r.getCode(), r.getName())).toList();
    }
    public record Role(String code, String name) {}
}
