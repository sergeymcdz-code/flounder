package com.acme.sportplatform.config;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Request-Id";
    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() { return false; }

    @Override
    protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = (String) request.getAttribute(ATTRIBUTE);
        if (requestId == null) {
            String supplied = request.getHeader(HEADER);
            // Bound untrusted input and prevent log/header injection.
            requestId = supplied != null && supplied.matches("[A-Za-z0-9._-]{1,128}")
                    ? supplied : UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, requestId);
        }
        String previous = MDC.get("correlation-id");
        MDC.put("correlation-id", requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove("correlation-id");
            else MDC.put("correlation-id", previous);
        }
    }
}
