package com.acme.sportplatform.payments.web;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.provider.PaymentProviderProperties;
import com.acme.sportplatform.payments.infrastructure.yookassa.YooKassaProperties;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class YooKassaWebhookRequestFilterTest {
    private YooKassaWebhookRequestFilter filter(PaymentProvider provider) {
        var properties = new PaymentProviderProperties();
        properties.setProvider(provider);
        return new YooKassaWebhookRequestFilter(properties, new YooKassaProperties());
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/v1/payments/webhooks/yookassa");
        request.setRemoteAddr("185.71.76.1");
        return request;
    }

    @Test
    void knownOversizeIsRejectedBeforeOpeningBody() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/payments/webhooks/yookassa") {
            @Override public long getContentLengthLong() { return 16385; }
            @Override public ServletInputStream getInputStream() { throw new AssertionError("Body was opened"); }
        };
        var response = new MockHttpServletResponse();
        filter(PaymentProvider.YOOKASSA).doFilter(request, response, (req, res) -> {throw new AssertionError();});
        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void unknownLengthIsBoundedAndExactLimitAccepted() throws Exception {
        for (int size : new int[]{16384, 16385}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/payments/webhooks/yookassa") {
                @Override public long getContentLengthLong() { return -1; }
            };
            request.setRemoteAddr("185.71.76.1");
            request.setContent(new byte[size]);
            var response = new MockHttpServletResponse();
            AtomicBoolean called = new AtomicBoolean();
            filter(PaymentProvider.YOOKASSA).doFilter(request, response, (req, res) -> called.set(true));
            assertThat(called.get()).isEqualTo(size == 16384);
            assertThat(response.getStatus()).isEqualTo(size == 16384 ? 200 : 413);
        }
    }

    @Test
    void manualProviderDoesNotOpenBody() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/payments/webhooks/yookassa") {
            @Override public ServletInputStream getInputStream() { throw new AssertionError("Body was opened"); }
        };
        var response = new MockHttpServletResponse();
        filter(PaymentProvider.MANUAL).doFilter(request, response, (req, res) -> {throw new AssertionError();});
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void acceptsOfficialNetworksAndRejectsBoundaryAddresses() throws Exception {
        for (String ip : new String[]{"185.71.76.0", "185.71.77.31", "77.75.153.127", "77.75.156.11",
                "77.75.156.35", "77.75.154.128", "2a02:5180:ffff::1", "185.71.76.32", "77.75.156.12", "2a02:5181::1"}) {
            var request = request();
            request.setRemoteAddr(ip);
            request.setContent(new byte[0]);
            var response = new MockHttpServletResponse();
            AtomicBoolean called = new AtomicBoolean();
            filter(PaymentProvider.YOOKASSA).doFilter(request, response, (req, res) -> called.set(true));
            assertThat(called.get()).as(ip).isEqualTo(!java.util.Set.of("185.71.76.32", "77.75.156.12", "2a02:5181::1").contains(ip));
        }
    }
}
