package com.acme.sportplatform.identity.infrastructure.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;

public class PlatformUserPrincipal implements UserDetails, AuthenticatedUserPrincipal  {

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final String status;
    private final Collection<? extends GrantedAuthority> authorities;

    public PlatformUserPrincipal(UUID userId,
                                String email,
                                String passwordHash,
                                String status,
                                Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.authorities = authorities;
    }

    @Override
    public UUID getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "active".equalsIgnoreCase(status);
    }
}