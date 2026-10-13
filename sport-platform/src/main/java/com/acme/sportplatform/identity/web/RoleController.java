package com.acme.sportplatform.identity.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.acme.sportplatform.identity.application.AdminUserService;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
    private final AdminUserService users;
    public RoleController(AdminUserService users) { this.users = users; }
    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<AdminUserService.Role> list() { return users.roles(); }
}
