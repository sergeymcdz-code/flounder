package com.acme.sportplatform.registrations.web;

import com.acme.sportplatform.events.*;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.registrations.api.PaymentRequests.*;
import com.acme.sportplatform.registrations.api.PaymentSummary;
import com.acme.sportplatform.registrations.application.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}")
public class EventPaymentsController {
    private final PaymentSummaryExport export;
    private final EventAccessService access;
    private final RegistrationPaymentService payments;
    private final RegistrationFeeService fees;

    public EventPaymentsController(
            EventAccessService access,
            RegistrationPaymentService payments,
            RegistrationFeeService fees,
            PaymentSummaryExport export) {
        this.export = export;

        this.access = access;
        this.payments = payments;
        this.fees = fees;
    }

    @PostMapping("/payments/mark-paid-bulk")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public BulkResult bulk(
            @PathVariable UUID eventId,
            @RequestBody @Valid Bulk request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanModify(eventId, principal);
        return payments.bulk(eventId, principal.getUserId(), request);
    }

    @PostMapping("/payments/recalculate-fees")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public Map<String, Integer> recalculate(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanModify(eventId, principal);
        return Map.of("updated", payments.recalculate(eventId));
    }

    @GetMapping("/payments/summary")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public PaymentSummary summary(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return payments.summary(eventId);
    }

    @GetMapping("/payment-instructions")
    @PreAuthorize("isAuthenticated()")
    public EventPaymentSettings instructions(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        var settings = fees.settings(eventId);
        return settings.enabled()
                ? settings
                : new EventPaymentSettings(false, "", null, null, null, null);
    }

    @GetMapping("/payments/summary.xlsx")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return org.springframework.http.ResponseEntity.ok()
                .header(
                        "Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=payments.xlsx")
                .body(export.export(payments.summary(eventId)));
    }
}
