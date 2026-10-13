package com.acme.sportplatform.payments.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.registrations.RegistrationPaymentLookup;

@Service
@Transactional(readOnly = true)
public class PaymentReviewAccess {
    private final EventAccessService events;
    private final RegistrationPaymentLookup registrations;
    private final PaymentRepository payments;

    public PaymentReviewAccess(EventAccessService events, RegistrationPaymentLookup registrations, PaymentRepository payments) {
        this.events = events;
        this.registrations = registrations;
        this.payments = payments;
    }

    public void assertRegistrationAccess(UUID registrationId, AuthenticatedUserPrincipal principal) {
        events.assertCanManage(registrations.eventId(registrationId), principal);
    }

    public void assertPaymentAccess(UUID paymentId, AuthenticatedUserPrincipal principal) {
        var payment = payments.findById(paymentId)
                .orElseThrow(() -> new BusinessException("payments.not_found", "Платёж не найден"));
        assertRegistrationAccess(payment.getRegistrationId(), principal);
    }
}
