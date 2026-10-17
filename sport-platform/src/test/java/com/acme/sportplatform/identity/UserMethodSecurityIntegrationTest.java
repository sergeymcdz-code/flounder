package com.acme.sportplatform.identity;

import com.acme.sportplatform.identity.api.UpdateUserRequest;
import com.acme.sportplatform.identity.web.UserController;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMethodSecurityIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired UserController users;

    @WithMockUser(roles = "PARTICIPANT")
    @ParameterizedTest
    @ValueSource(strings = {"get", "patch", "delete"})
    void controllerProxyRequiresPlatformAdminIndependentlyOfHttpRules(String operation) {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> {
            switch (operation) {
                case "get" -> users.getById(id);
                case "patch" -> users.update(id, new UpdateUserRequest());
                case "delete" -> users.delete(id);
                default -> throw new IllegalArgumentException(operation);
            }
        }).isInstanceOf(AccessDeniedException.class);
    }
}
