package com.ecommerce.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS Configuration.
 * Restricts cross-origin requests to known frontend origins only.
 * NEVER use allowedOrigins("*") combined with allowCredentials(true) — that is
 * a known misconfiguration (CORS credential bypass) forbidden by the browser
 * spec and a real security risk.
 *
 * Allowed origins are injected via the CORS_ALLOWED_ORIGINS environment variable
 * (comma-separated list). For local development, defaults to localhost:4200.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:4200,http://localhost}")
    private String allowedOriginsRaw;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.asList(allowedOriginsRaw.split(","));
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Session-ID", "X-Requested-With", "X-Shop-Key"
        ));
        // Do NOT set allowCredentials(true) unless using cookie-based auth.
        // Our app uses Authorization: Bearer header, so credentials mode is not required.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
