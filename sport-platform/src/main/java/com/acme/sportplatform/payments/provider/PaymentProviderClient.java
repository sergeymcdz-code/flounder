package com.acme.sportplatform.payments.provider;

import com.acme.sportplatform.payments.PaymentProvider;

public interface PaymentProviderClient {

    PaymentProvider provider();

    ProviderPaymentResult createPayment(
            ProviderPaymentCommand command
    );
}