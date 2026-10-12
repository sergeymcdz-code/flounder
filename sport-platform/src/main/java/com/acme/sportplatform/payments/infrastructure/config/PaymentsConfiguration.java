package com.acme.sportplatform.payments.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.acme.sportplatform.payments.infrastructure.yookassa.YooKassaProperties;
import com.acme.sportplatform.payments.provider.PaymentProviderProperties;

@Configuration
@EnableConfigurationProperties({
        PaymentProviderProperties.class,
        YooKassaProperties.class
})
public class PaymentsConfiguration {
}