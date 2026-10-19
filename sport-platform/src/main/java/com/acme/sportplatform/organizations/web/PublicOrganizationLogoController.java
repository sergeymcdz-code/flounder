package com.acme.sportplatform.organizations.web;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.acme.sportplatform.organizations.application.PublicOrganizationProfileService;

@RestController
@RequestMapping("/api/v1/public/organizations/{slug}/logo")
public class PublicOrganizationLogoController {
    private final PublicOrganizationProfileService service;
    public PublicOrganizationLogoController(PublicOrganizationProfileService service) { this.service=service; }
    @GetMapping
    public ResponseEntity<byte[]> logo(@PathVariable String slug) {
        byte[] bytes=service.logo(slug);
        return ResponseEntity.ok().header("Cache-Control","public, max-age=3600").header("X-Content-Type-Options","nosniff")
                .contentType(bytes.length>0 && bytes[0]==(byte)0x89 ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG).body(bytes);
    }
}
