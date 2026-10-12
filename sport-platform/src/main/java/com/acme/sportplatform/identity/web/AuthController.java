package com.acme.sportplatform.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.identity.api.AuthTokenResponse;
import com.acme.sportplatform.identity.api.CreateUserRequest;
import com.acme.sportplatform.identity.api.CurrentUserResponse;
import com.acme.sportplatform.identity.api.LoginRequest;
import com.acme.sportplatform.identity.api.UserResponse;
import com.acme.sportplatform.identity.application.CreateUserUseCase;
import com.acme.sportplatform.identity.application.GetCurrentUserUseCase;
import com.acme.sportplatform.identity.application.LoginUseCase;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final CreateUserUseCase createUserUseCase;

    public AuthController(
            LoginUseCase loginUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            CreateUserUseCase createUserUseCase
    ) {
        this.loginUseCase = loginUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.createUserUseCase = createUserUseCase;
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return loginUseCase.execute(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody CreateUserRequest request
    ) {
        return createUserUseCase.execute(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return getCurrentUserUseCase.execute(principal);
    }
}