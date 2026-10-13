package com.acme.sportplatform.payments.web;

import com.acme.sportplatform.payments.application.PaymentReviewAccess;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.application.ConfirmPaymentUseCase;
import com.acme.sportplatform.payments.application.GetPaymentForReviewUseCase;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentReviewController {

    private final ConfirmPaymentUseCase confirmPaymentUseCase;
    private final GetPaymentForReviewUseCase getPaymentForReviewUseCase;

    private final PaymentReviewAccess paymentReviewAccess;

    public PaymentReviewController(
            ConfirmPaymentUseCase confirmPaymentUseCase,
            GetPaymentForReviewUseCase getPaymentForReviewUseCase,
            PaymentReviewAccess paymentReviewAccess
    ) {
        this.paymentReviewAccess = paymentReviewAccess;
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
            @PathVariable UUID registrationId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        paymentReviewAccess.assertRegistrationAccess(registrationId, principal);

        return ResponseEntity.ok(
                getPaymentForReviewUseCase.execute(registrationId)
        );
    }

    @PostMapping("/{paymentId}/confirm")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')"
    )
    public ResponseEntity<PaymentResponse> confirm(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        paymentReviewAccess.assertPaymentAccess(paymentId, principal);

        return ResponseEntity.ok(confirmPaymentUseCase.execute(paymentId));
    }
}