package com.acme.sportplatform.payments.web;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.provider.PaymentProviderProperties;
import com.acme.sportplatform.payments.infrastructure.yookassa.YooKassaProperties;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class YooKassaWebhookRequestFilter extends OncePerRequestFilter {
    public static final int MAX_BODY_BYTES = 16 * 1024;
    public static final String BODY_ATTRIBUTE = YooKassaWebhookRequestFilter.class.getName() + ".body";
    private final PaymentProviderProperties properties;
    private final List<IpAddressMatcher> allowedNetworks;

    public YooKassaWebhookRequestFilter(PaymentProviderProperties properties, YooKassaProperties yookassa) {
        this.properties = properties;
        this.allowedNetworks = yookassa.getAllowedCidrs().stream().map(IpAddressMatcher::new).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/v1/payments/webhooks/yookassa".equals(
                        request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (properties.getProvider() != PaymentProvider.YOOKASSA) {
            response.setStatus(404);
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.setStatus(413);
            return;
        }
        if (allowedNetworks.stream().noneMatch(network -> network.matches(request.getRemoteAddr()))) {
            response.setStatus(403);
            return;
        }
        // With chunked encoding the length is unknown until we read; never buffer beyond limit + 1.
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            response.setStatus(413);
            return;
        }
        request.setAttribute(BODY_ATTRIBUTE, body);
        chain.doFilter(request, response);
    }
}
