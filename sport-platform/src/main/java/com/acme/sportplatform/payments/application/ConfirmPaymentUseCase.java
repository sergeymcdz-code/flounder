package com.acme.sportplatform.payments.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;

@Service
public class ConfirmPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    public ConfirmPaymentUseCase(
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }

    @Transactional
    public PaymentResponse execute(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(
                        "payments.not_found",
                        "Payment not found"
                ));

        if (!PaymentProvider.MANUAL.name().equals(payment.getProvider())) {
            throw new BusinessException(
                    "payments.manual_confirmation_not_allowed",
                    "Only manual payments can be confirmed by an operator"
            );
        }

        boolean confirmableStatus =
                PaymentStatus.CREATED.name().equals(payment.getStatus())
                        || PaymentStatus.PENDING.name().equals(payment.getStatus());

        if (!confirmableStatus) {
            throw new BusinessException(
                    "payments.not_confirmable",
                    "Only a created or pending manual payment can be confirmed"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        payment.setStatus(PaymentStatus.SUCCEEDED.name());
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);

        return paymentMapper.toResponse(paymentRepository.save(payment));
    }
}