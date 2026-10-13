package com.acme.sportplatform.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(mapper);

    @Test
    void usesBlockedExceptionTypeForTheBlockedError() throws Exception {
        var response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new UserBlockedException());
        var body = mapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("code").asText()).isEqualTo("identity.user_blocked");
        assertThat(body.get("message").asText()).isEqualTo("Пользователь заблокирован. Обратитесь к администратору");
    }

    @Test
    void doesNotInferBlockedStateFromAnotherExceptionsMessage() throws Exception {
        var response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("identity.user_blocked"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(mapper.readTree(response.getContentAsByteArray()).get("code").asText()).isEqualTo("authentication_required");
    }
}
