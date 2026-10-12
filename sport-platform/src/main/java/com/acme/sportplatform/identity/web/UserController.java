package com.acme.sportplatform.identity.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.identity.api.AssignRoleRequest;
import com.acme.sportplatform.identity.api.CreateUserRequest;
import com.acme.sportplatform.identity.api.UpdateUserRequest;
import com.acme.sportplatform.identity.api.UserDetailsResponse;
import com.acme.sportplatform.identity.api.UserResponse;
import com.acme.sportplatform.identity.application.AssignRoleToUserUseCase;
import com.acme.sportplatform.identity.application.CreateUserUseCase;
import com.acme.sportplatform.identity.application.DeleteUserUseCase;
import com.acme.sportplatform.identity.application.GetUserByIdUseCase;
import com.acme.sportplatform.identity.application.RevokeRoleFromUserUseCase;
import com.acme.sportplatform.identity.application.UpdateUserUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final GetUserByIdUseCase getUserByIdUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final AssignRoleToUserUseCase assignRoleToUserUseCase;
    private final RevokeRoleFromUserUseCase revokeRoleFromUserUseCase;

    public UserController(CreateUserUseCase createUserUseCase,
                        GetUserByIdUseCase getUserByIdUseCase,
                        UpdateUserUseCase updateUserUseCase,
                        DeleteUserUseCase deleteUserUseCase,
                        AssignRoleToUserUseCase assignRoleToUserUseCase,
                        RevokeRoleFromUserUseCase revokeRoleFromUserUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.getUserByIdUseCase = getUserByIdUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.assignRoleToUserUseCase = assignRoleToUserUseCase;
        this.revokeRoleFromUserUseCase = revokeRoleFromUserUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return createUserUseCase.execute(request);
    }

    @GetMapping("/{userId}")
    public UserDetailsResponse getById(@PathVariable UUID userId) {
        return getUserByIdUseCase.execute(userId);
    }

    @PatchMapping("/{userId}")
    public UserDetailsResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return updateUserUseCase.execute(userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID userId) {
        deleteUserUseCase.execute(userId);
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> assignRole(@PathVariable UUID userId,
                                        @Valid @RequestBody AssignRoleRequest request) {
        boolean created = assignRoleToUserUseCase.execute(userId, request.roleCode());

        return created
                ? ResponseEntity.status(HttpStatus.CREATED).build()
                : ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/roles/{roleCode}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> revokeRole(@PathVariable UUID userId,
                                        @PathVariable String roleCode) {
        revokeRoleFromUserUseCase.execute(userId, roleCode);
        return ResponseEntity.noContent().build();
    }
}