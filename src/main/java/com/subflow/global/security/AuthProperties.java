package com.subflow.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("subflow.auth")
public record AuthProperties(AuthMode mode) {
}
