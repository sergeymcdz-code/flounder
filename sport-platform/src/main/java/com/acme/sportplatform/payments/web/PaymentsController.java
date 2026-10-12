package com.acme.sportplatform.payments.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.application.CreatePaymentForRegistrationUseCase;
import com.acme.sportplatform.payments.application.GetPaymentForRegistrationUseCase;

@RestController
@RequestMapping("/api/v1/registrations/{registrationId}/payments")
public class PaymentsController {

        private final CreatePaymentForRegistrationUseCase createPaymentForRegistrationUseCase;
        private final GetPaymentForRegistrationUseCase getPaymentForRegistrationUseCase;

        public PaymentsController(
                CreatePaymentForRegistrationUseCase createPaymentForRegistrationUseCase,
                GetPaymentForRegistrationUseCase getPaymentForRegistrationUseCase
        ) {
                this.createPaymentForRegistrationUseCase = createPaymentForRegistrationUseCase;
                this.getPaymentForRegistrationUseCase = getPaymentForRegistrationUseCase;
        }

        @GetMapping
        @PreAuthorize("hasRole('PARTICIPANT')")
        public ResponseEntity<PaymentResponse> getForRegistration(
                @PathVariable UUID registrationId,
                @AuthenticationPrincipal AuthenticatedUserPrincipal principal
        ) {
        return ResponseEntity.ok(
                getPaymentForRegistrationUseCase.execute(
                        registrationId,
                        principal.getUserId()
                )
        );
        }

        @PostMapping
        @PreAuthorize("hasRole('PARTICIPANT')")
        public ResponseEntity<PaymentResponse> createForRegistration(
                @PathVariable UUID registrationId,
                @AuthenticationPrincipal AuthenticatedUserPrincipal principal
        ) {
                return ResponseEntity.ok(
                        createPaymentForRegistrationUseCase.execute(
                                registrationId,
                                principal.getUserId()
                        )
                );
        }
}