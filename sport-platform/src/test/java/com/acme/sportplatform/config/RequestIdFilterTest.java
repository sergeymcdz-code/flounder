package com.acme.sportplatform.config;

import java.util.UUID;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesSafeHeaderAndRestoresMdcEvenOnError() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", "demo-request-42");
        MDC.put("correlation-id", "outer");
        try {
            assertThrows(ServletException.class, () -> filter.doFilter(request, response, (req, res) -> {
                assertEquals("demo-request-42", MDC.get("correlation-id"));
                throw new ServletException("test");
            }));
            assertEquals("demo-request-42", response.getHeader("X-Request-Id"));
            assertEquals("outer", MDC.get("correlation-id"));
        } finally { MDC.clear(); }
    }

    @Test
    void generatesIdForMissingOrUnsafeHeaderAndClearsMdc() throws Exception {
        for (String header : new String[] {null, "bad\nvalue", "x".repeat(129)}) {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            if (header != null) request.addHeader("X-Request-Id", header);
            filter.doFilter(request, response, (req, res) ->
                    assertEquals(response.getHeader("X-Request-Id"), MDC.get("correlation-id")));
            assertNotNull(UUID.fromString(response.getHeader("X-Request-Id")));
            assertNull(MDC.get("correlation-id"));
        }
    }
}
