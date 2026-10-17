package com.acme.sportplatform.publication.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class PublicHttpCacheConfig {
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> publicEtagFilter() {
        var filter = new ShallowEtagHeaderFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                return path.endsWith(".pdf") || path.startsWith("/api/v1/public/documents/");
            }
        };
        filter.setWriteWeakETag(true);
        var registration = new FilterRegistrationBean<ShallowEtagHeaderFilter>(filter);
        registration.addUrlPatterns("/api/v1/public/*");
        return registration;
    }
}
