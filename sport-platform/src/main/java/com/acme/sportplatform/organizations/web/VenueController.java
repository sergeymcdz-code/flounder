package com.acme.sportplatform.organizations.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.organizations.api.CreateVenueRequest;
import com.acme.sportplatform.organizations.api.VenueResponse;
import com.acme.sportplatform.organizations.application.CreateVenueUseCase;
import com.acme.sportplatform.organizations.application.GetVenuesUseCase;
import com.acme.sportplatform.organizations.application.GetVenueByIdUseCase;
import com.acme.sportplatform.organizations.application.GetVenuesUseCase;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final CreateVenueUseCase createVenueUseCase;
    private final GetVenueByIdUseCase getVenueByIdUseCase;
    private final GetVenuesUseCase getVenuesUseCase;

    public VenueController(
            CreateVenueUseCase createVenueUseCase,
            GetVenueByIdUseCase getVenueByIdUseCase,
            GetVenuesUseCase getVenuesUseCase
    ) {
        this.createVenueUseCase = createVenueUseCase;
        this.getVenueByIdUseCase = getVenueByIdUseCase;
        this.getVenuesUseCase = getVenuesUseCase;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<VenueResponse> createVenue(
            @RequestBody @Valid CreateVenueRequest request
    ) {
        return ResponseEntity.ok(createVenueUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<VenueResponse> getVenue(@PathVariable UUID id) {
        return ResponseEntity.ok(getVenueByIdUseCase.execute(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<List<VenueResponse>> getVenues() {
        return ResponseEntity.ok(getVenuesUseCase.execute());
    }
}