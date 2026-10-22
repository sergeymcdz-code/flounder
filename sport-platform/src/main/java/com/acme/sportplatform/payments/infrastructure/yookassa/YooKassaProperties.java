package com.acme.sportplatform.payments.infrastructure.yookassa;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments.yookassa")
public class YooKassaProperties {

    // https://yookassa.ru/developers/using-api/webhooks (verified 2026-10-17)
    private List<String> allowedCidrs = List.of(
            "185.71.76.0/27", "185.71.77.0/27", "77.75.153.0/25",
            "77.75.156.11/32", "77.75.156.35/32", "77.75.154.128/25", "2a02:5180::/32");

    public List<String> getAllowedCidrs() {
        return allowedCidrs;
    }

    public void setAllowedCidrs(List<String> allowedCidrs) {
        this.allowedCidrs = allowedCidrs;
    }

    private String baseUrl = "https://api.yookassa.ru/v3";
    @NotBlank
    private String shopId;
    @NotBlank
    private String secretKey;
    @NotBlank
    private String returnUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }
}