package com.acme.sportplatform.admin.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminTestController {

    @GetMapping("/api/v1/admin/ping")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public String ping() {
        return "admin-ok";
    }
}