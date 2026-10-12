package com.acme.sportplatform.payments.provider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.payments.PaymentProvider;

@Component
public class PaymentProviderClientResolver {

    private final Map<PaymentProvider, PaymentProviderClient> clients;

    public PaymentProviderClientResolver(
            List<PaymentProviderClient> clients
    ) {
        Map<PaymentProvider, PaymentProviderClient> resolvedClients =
                new EnumMap<>(PaymentProvider.class);

        for (PaymentProviderClient client : clients) {
            PaymentProvider provider = client.provider();

            if (resolvedClients.putIfAbsent(provider, client) != null) {
                throw new IllegalStateException(
                        "Multiple payment provider clients configured for provider: "
                                + provider
                );
            }
        }

        this.clients = Map.copyOf(resolvedClients);
    }

    public PaymentProviderClient resolve(PaymentProvider provider) {
        PaymentProviderClient client = clients.get(provider);

        if (client == null) {
            throw new BusinessException(
                    "payments.provider_not_supported",
                    "Payment provider is not supported: " + provider
            );
        }

        return client;
    }
}