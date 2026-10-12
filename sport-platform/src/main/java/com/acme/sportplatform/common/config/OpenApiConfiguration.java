package com.acme.sportplatform.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI flounderApi() {
        return new OpenAPI()
                .info(new Info().title("Flounder API").version("v1"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    OpenApiCustomizer publicOperations() {
        return api ->
                api.getPaths()
                        .forEach(
                                (path, item) -> {
                                    if (path.startsWith("/api/v1/public/") && item.getGet() != null)
                                        item.getGet().setSecurity(List.of());
                                    if (path.equals("/api/v1/age-group-presets")
                                            && item.getGet() != null)
                                        item.getGet().setSecurity(List.of());
                                    if (List.of(
                                                            "/api/v1/users",
                                                            "/api/v1/auth/login",
                                                            "/api/v1/auth/register",
                                                            "/api/v1/payments/webhooks/yookassa")
                                                    .contains(path)
                                            && item.getPost() != null)
                                        item.getPost().setSecurity(List.of());
                                });
    }
}
