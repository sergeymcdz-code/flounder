package com.acme.sportplatform.organizations.web;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import com.acme.sportplatform.organizations.api.PublicOrganizationProfile;
import com.acme.sportplatform.organizations.application.PublicOrganizationProfileService;

@RestController
@RequestMapping("/api/v1/organizations/{id}")
@PreAuthorize("isAuthenticated()")
public class OrganizationPublicProfileController {
    private final PublicOrganizationProfileService service;
    private final OrganizationManagementAccess access;
    public OrganizationPublicProfileController(PublicOrganizationProfileService service, OrganizationManagementAccess access) {
        this.service = service; this.access = access;
    }
    private void assertOwner(UUID id, AuthenticatedUserPrincipal user) {
        boolean admin = user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
        if (!admin && !access.isOwnerOrAdmin(id, user.getUserId()))
            throw new BusinessException("organizations.access_denied", "Изменение публичной страницы доступно владельцу и администратору организации");
    }
    @GetMapping("/public-profile")
    public PublicOrganizationProfile profile(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        assertOwner(id, user); return service.profile(id);
    }
    @PutMapping("/public-profile")
    public PublicOrganizationProfile save(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @Valid @RequestBody PublicOrganizationProfile request) {
        assertOwner(id, user); return service.save(id, request);
    }
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        assertOwner(id, user);
        byte[] bytes=service.ownerLogo(id);
        return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
                .contentType(bytes.length>0 && bytes[0]==(byte)0x89 ? org.springframework.http.MediaType.IMAGE_PNG : org.springframework.http.MediaType.IMAGE_JPEG).body(bytes);
    }
    @PostMapping("/logo")
    public PublicOrganizationProfile upload(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal user,
            @RequestParam(required=false) MultipartFile file) {
        assertOwner(id, user);
        if (file == null) throw new BusinessException("organizations.missing_logo", "Не передан файл в поле file");
        return service.upload(id, file);
    }
    @DeleteMapping("/logo")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal user) {
        assertOwner(id, user); service.deleteLogo(id); return ResponseEntity.noContent().build();
    }
}
