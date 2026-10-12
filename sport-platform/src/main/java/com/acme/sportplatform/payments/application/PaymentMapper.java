package com.acme.sportplatform.payments.application;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(PaymentEntity entity) {
        return new PaymentResponse(
                entity.getId(),
                entity.getRegistrationId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getProvider(),
                entity.getConfirmationUrl(),
                entity.getExpiresAt(),
                entity.getPaidAt(),
                entity.getCanceledAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}