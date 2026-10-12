package com.acme.sportplatform.payments.infrastructure.manual;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.provider.PaymentProviderClient;
import com.acme.sportplatform.payments.provider.ProviderPaymentCommand;
import com.acme.sportplatform.payments.provider.ProviderPaymentResult;

@Component
public class ManualPaymentProviderClient
        implements PaymentProviderClient {

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MANUAL;
    }

    @Override
    public ProviderPaymentResult createPayment(
            ProviderPaymentCommand command
    ) {
        return new ProviderPaymentResult(
                null,
                null,
                PaymentStatus.CREATED,
                null,
                "{}"
        );
    }
}