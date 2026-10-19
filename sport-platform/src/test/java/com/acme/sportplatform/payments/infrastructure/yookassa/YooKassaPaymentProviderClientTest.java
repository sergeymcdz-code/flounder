package com.acme.sportplatform.payments.infrastructure.yookassa;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.acme.sportplatform.common.exception.BusinessException;

class YooKassaPaymentProviderClientTest {
    @Test
    void getsPaymentUsingCredentialsAndParsesAmountWithoutConfirmation() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new YooKassaProperties();
        properties.setShopId("test-shop");
        properties.setSecretKey("test-key");
        var client = new YooKassaPaymentProviderClient(properties, builder);
        server.expect(requestTo("https://api.yookassa.ru/v3/payments/test-id"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic dGVzdC1zaG9wOnRlc3Qta2V5"))
                .andRespond(withSuccess("""
                    {"id":"test-id","status":"succeeded","amount":{"value":"1500.00","currency":"RUB"}}
                    """, MediaType.APPLICATION_JSON));
        var payment = client.getPayment("test-id");
        assertThat(payment.id()).isEqualTo("test-id");
        assertThat(payment.status()).isEqualTo("succeeded");
        assertThat(payment.amount().value()).isEqualTo("1500.00");
        assertThat(payment.amount().currency()).isEqualTo("RUB");
        server.verify();
    }

    @Test
    void distinguishesMissingPaymentFromUnavailableApi() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new YooKassaProperties();
        properties.setShopId("test-shop");
        properties.setSecretKey("test-key");
        var client = new YooKassaPaymentProviderClient(properties, builder);
        server.expect(requestTo("https://api.yookassa.ru/v3/payments/missing")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://api.yookassa.ru/v3/payments/unavailable")).andRespond(withServerError());
        assertThat(client.getPayment("missing")).isNull();
        assertThatThrownBy(() -> client.getPayment("unavailable"))
                .isInstanceOf(BusinessException.class).hasMessage("Платёжный сервис временно недоступен");
        server.verify();
    }
}
