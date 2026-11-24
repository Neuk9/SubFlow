package com.subflow.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("subflow.security")
public record SecurityProperties(
        List<String> allowedOrigins,
        boolean trustCloudflareHeaders,
        String auditHashPepper,
        RateLimit rateLimit
) {

    public SecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
        rateLimit = rateLimit == null ? new RateLimit(false) : rateLimit;
    }

    public record RateLimit(boolean enabled) {
    }
}
