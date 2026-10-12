package com.acme.sportplatform.payments.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.application.ConfirmPaymentUseCase;
import com.acme.sportplatform.payments.application.GetPaymentForReviewUseCase;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentReviewController {

    private final ConfirmPaymentUseCase confirmPaymentUseCase;
    private final GetPaymentForReviewUseCase getPaymentForReviewUseCase;

    public PaymentReviewController(
            ConfirmPaymentUseCase confirmPaymentUseCase,
            GetPaymentForReviewUseCase getPaymentForReviewUseCase
    ) {
        this.confirmPaymentUseCase = confirmPaymentUseCase;
        this.getPaymentForReviewUseCase = getPaymentForReviewUseCase;
    }

    @GetMapping("/review/registrations/{registrationId}")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') "
                    + "or hasRole('ORGANIZER') "
                    + "or hasRole('OPERATOR')"
    )
    public ResponseEntity<PaymentResponse> getForReview(
            @PathVariable UUID registrationId
    ) {
        return ResponseEntity.ok(
                getPaymentForReviewUseCase.execute(registrationId)
        );
    }

    @PostMapping("/{paymentId}/confirm")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or hasRole('OPERATOR')"
    )
    public ResponseEntity<PaymentResponse> confirm(
            @PathVariable UUID paymentId
    ) {
        return ResponseEntity.ok(confirmPaymentUseCase.execute(paymentId));
    }
}