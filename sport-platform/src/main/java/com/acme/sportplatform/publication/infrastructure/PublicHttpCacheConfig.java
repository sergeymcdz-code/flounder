package com.acme.sportplatform.publication.infrastructure;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class PublicHttpCacheConfig {
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> publicEtagFilter() {
        var filter = new ShallowEtagHeaderFilter();
        filter.setWriteWeakETag(true);
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/public/*");
        return registration;
    }
}
