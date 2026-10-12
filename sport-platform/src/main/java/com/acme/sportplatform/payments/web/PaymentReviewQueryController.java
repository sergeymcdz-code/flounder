package com.acme.sportplatform.payments.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.application.GetPaymentForReviewUseCase;

@RestController
@RequestMapping("/api/v1/payment-review")
public class PaymentReviewQueryController {

    private final GetPaymentForReviewUseCase getPaymentForReviewUseCase;

    public PaymentReviewQueryController(
            GetPaymentForReviewUseCase getPaymentForReviewUseCase
    ) {
        this.getPaymentForReviewUseCase = getPaymentForReviewUseCase;
    }

    @GetMapping("/registrations/{registrationId}")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')"
    )
    public ResponseEntity<PaymentResponse> getByRegistrationId(
            @PathVariable UUID registrationId
    ) {
        return ResponseEntity.ok(
                getPaymentForReviewUseCase.execute(registrationId)
        );
    }
}