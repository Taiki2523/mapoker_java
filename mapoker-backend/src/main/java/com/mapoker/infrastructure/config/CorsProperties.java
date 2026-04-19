package com.mapoker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("cors")
public record CorsProperties(
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials
) {
    public CorsProperties {
        if (allowedOriginPatterns == null || allowedOriginPatterns.isEmpty())
            allowedOriginPatterns = List.of("*");
        if (allowedMethods == null || allowedMethods.isEmpty())
            allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
        if (allowedHeaders == null || allowedHeaders.isEmpty())
            allowedHeaders = List.of("*");
    }
}
