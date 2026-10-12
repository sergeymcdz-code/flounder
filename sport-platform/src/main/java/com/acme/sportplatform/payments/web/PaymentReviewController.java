package com.acme.sportplatform.payments.web;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.application.ConfirmPaymentUseCase;
import com.acme.sportplatform.payments.application.GetPaymentForReviewUseCase;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.registrations.RegistrationPaymentLookup;
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

    private final EventAccessService eventAccessService;
    private final RegistrationPaymentLookup registrationPaymentLookup;
    private final PaymentRepository paymentRepository;

    public PaymentReviewController(
            ConfirmPaymentUseCase confirmPaymentUseCase,
            GetPaymentForReviewUseCase getPaymentForReviewUseCase,
            EventAccessService eventAccessService,
            RegistrationPaymentLookup registrationPaymentLookup,
            PaymentRepository paymentRepository
    ) {
        this.eventAccessService = eventAccessService;
        this.registrationPaymentLookup = registrationPaymentLookup;
        this.paymentRepository = paymentRepository;
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
        eventAccessService.assertCanManage(registrationPaymentLookup.eventId(registrationId), principal);

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
        var payment = paymentRepository.findById(paymentId).orElseThrow(() -> new BusinessException("payments.not_found", "Платёж не найден"));
        eventAccessService.assertCanManage(registrationPaymentLookup.eventId(payment.getRegistrationId()), principal);

        return ResponseEntity.ok(confirmPaymentUseCase.execute(paymentId));
    }
}