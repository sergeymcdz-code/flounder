package com.acme.sportplatform.payments.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.acme.sportplatform.payments.PaymentProvider;

@ConfigurationProperties(prefix = "app.payments")
public class PaymentProviderProperties {

    private PaymentProvider provider = PaymentProvider.MANUAL;

    public PaymentProvider getProvider() {
        return provider;
    }

    public void setProvider(PaymentProvider provider) {
        this.provider = provider;
    }
}