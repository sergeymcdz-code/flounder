package com.acme.sportplatform.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.acme.sportplatform.common.web.RestAccessDeniedHandler;
import com.acme.sportplatform.common.web.RestAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")
                    .requestMatchers("/api/ping").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/*").hasRole("PLATFORM_ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*").hasRole("PLATFORM_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*").hasRole("PLATFORM_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/users/*/roles").hasRole("PLATFORM_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*/roles/*").hasRole("PLATFORM_ADMIN")
                    .requestMatchers(HttpMethod.POST,"/api/v1/payments/webhooks/yookassa").permitAll()
                    .requestMatchers(HttpMethod.GET,
                                    "/api/v1/events",
                                    "/api/v1/events/*",
                                    "/api/v1/events/public/*",
                                    "/api/v1/events/*/disciplines/*/results",
                                    "/api/v1/public/events",
                                    "/api/v1/public/events/**").permitAll()
                    .anyRequest().authenticated()
            )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(PlatformUserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}