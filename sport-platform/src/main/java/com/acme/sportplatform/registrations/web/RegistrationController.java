package com.acme.sportplatform.registrations.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.registrations.RegistrationPaymentLookup;
import com.acme.sportplatform.registrations.api.CreateRegistrationRequest;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.api.ReviewRegistrationRequest;
import com.acme.sportplatform.registrations.application.CreateRegistrationUseCase;
import com.acme.sportplatform.registrations.application.GetRegistrationByIdUseCase;
import com.acme.sportplatform.registrations.application.ListMyRegistrationsUseCase;
import com.acme.sportplatform.registrations.application.ListRegistrationsForReviewUseCase;
import com.acme.sportplatform.registrations.application.ReviewRegistrationUseCase;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {
    private final com.acme.sportplatform.registrations.application.RegistrationPaymentService payments;
    private final CreateRegistrationUseCase createRegistrationUseCase;
    private final ReviewRegistrationUseCase reviewRegistrationUseCase;
    private final ListMyRegistrationsUseCase listMyRegistrationsUseCase;
    private final ListRegistrationsForReviewUseCase listRegistrationsForReviewUseCase;
    private final GetRegistrationByIdUseCase getRegistrationByIdUseCase;
    private final EventAccessService eventAccessService;
    private final RegistrationPaymentLookup registrationPaymentLookup;

    public RegistrationController(CreateRegistrationUseCase createRegistrationUseCase,
            ReviewRegistrationUseCase reviewRegistrationUseCase,
            ListMyRegistrationsUseCase listMyRegistrationsUseCase,
            ListRegistrationsForReviewUseCase listRegistrationsForReviewUseCase,
            GetRegistrationByIdUseCase getRegistrationByIdUseCase,
            EventAccessService eventAccessService, RegistrationPaymentLookup registrationPaymentLookup, com.acme.sportplatform.registrations.application.RegistrationPaymentService payments) {
        this.payments = payments;

        this.createRegistrationUseCase = createRegistrationUseCase;
        this.reviewRegistrationUseCase = reviewRegistrationUseCase;
        this.listMyRegistrationsUseCase = listMyRegistrationsUseCase;
        this.listRegistrationsForReviewUseCase = listRegistrationsForReviewUseCase;
        this.getRegistrationByIdUseCase = getRegistrationByIdUseCase;
        this.eventAccessService = eventAccessService;
        this.registrationPaymentLookup = registrationPaymentLookup;
    }

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<RegistrationResponse> create(
            @AuthenticationPrincipal(expression = "userId") UUID participantUserId,
            @RequestBody @Valid CreateRegistrationRequest request) {
        return ResponseEntity.ok(createRegistrationUseCase.execute(participantUserId, request));
    }

    @PostMapping("/{registrationId}/review")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<RegistrationResponse> review(@PathVariable UUID registrationId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestBody @Valid ReviewRegistrationRequest request) {
        eventAccessService.assertCanModify(registrationPaymentLookup.eventId(registrationId), principal);
        return ResponseEntity.ok(reviewRegistrationUseCase.execute(registrationId, principal.getUserId(), request));
    }

    @PostMapping("/{registrationId}/mark-paid")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    @Operation(summary = "Отметить заявку как оплаченную")
    public RegistrationResponse markPaid(@PathVariable UUID registrationId,
            @RequestBody(required=false) @Valid com.acme.sportplatform.registrations.api.PaymentRequests.MarkPaid request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanModify(registrationPaymentLookup.eventId(registrationId), principal);
        payments.markPaid(registrationId, principal.getUserId(), request);
        return getRegistrationByIdUseCase.execute(registrationId);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<List<RegistrationResponse>> listMine(
            @AuthenticationPrincipal(expression = "userId") UUID participantUserId) {
        return ResponseEntity.ok(listMyRegistrationsUseCase.execute(participantUserId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<List<RegistrationResponse>> list(@RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) RegistrationStatus status,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        if (eventId != null) {
            eventAccessService.assertCanManage(eventId, principal);
        }
        return ResponseEntity.ok(listRegistrationsForReviewUseCase.execute(eventId, status, principal));
    }

    @GetMapping("/{registrationId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<RegistrationResponse> getById(@PathVariable UUID registrationId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanManage(registrationPaymentLookup.eventId(registrationId), principal);
        return ResponseEntity.ok(getRegistrationByIdUseCase.execute(registrationId));
    }

    @PostMapping("/{id}/waive-payment")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public RegistrationResponse waive(@PathVariable UUID id, @RequestBody @Valid com.acme.sportplatform.registrations.api.PaymentRequests.Comment request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanModify(registrationPaymentLookup.eventId(id),principal);
        payments.change(id,principal.getUserId(),"WAIVE",null,request.comment());return getRegistrationByIdUseCase.execute(id);
    }
    @PostMapping("/{id}/refund-payment")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public RegistrationResponse refund(@PathVariable UUID id, @RequestBody @Valid com.acme.sportplatform.registrations.api.PaymentRequests.Refund request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanModify(registrationPaymentLookup.eventId(id),principal);
        payments.change(id,principal.getUserId(),"REFUND",request.amount(),request.comment());return getRegistrationByIdUseCase.execute(id);
    }
    @PostMapping("/{id}/reset-payment")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public RegistrationResponse reset(@PathVariable UUID id, @RequestBody @Valid com.acme.sportplatform.registrations.api.PaymentRequests.Comment request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanModify(registrationPaymentLookup.eventId(id),principal);
        payments.change(id,principal.getUserId(),"RESET",null,request.comment());return getRegistrationByIdUseCase.execute(id);
    }
    @GetMapping("/{id}/payment-log")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public java.util.List<com.acme.sportplatform.registrations.api.PaymentLogEntry> paymentLog(@PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanManage(registrationPaymentLookup.eventId(id),principal); return payments.history(id);
    }
}
