package com.ecommerce.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import com.ecommerce.backend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Autowired
    private com.ecommerce.backend.security.ShopResolutionFilter shopResolutionFilter;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    /**
     * Returns 401 Unauthorized (not 403 Forbidden) for unauthenticated requests.
     * This is the correct HTTP semantic: 401 = "Who are you?", 403 = "I know who you are but you can't do this."
     */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized: valid authentication credentials required");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // OpenAPI & Swagger UI docs
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Platform super-admin: full control
                .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                // Shop admin operations
                .requestMatchers("/api/admin/**").hasAnyRole("SHOP_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/admin/**").hasAnyRole("SHOP_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/orders/admin/**").hasAnyRole("SHOP_ADMIN", "SUPER_ADMIN")
                // Customer endpoints (handled internally via session id for guests or user id for logged in users)
                .requestMatchers("/orders/my-orders").permitAll()
                // Notifications are user-specific — require authentication
                .requestMatchers("/notifications/**").authenticated()
                .requestMatchers("/notifications").authenticated()
                // Shop settings (GET/PUT /shops/me) require authentication
                .requestMatchers("/shops/me").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/reviews").authenticated()
                // File uploads require authentication (admin only)
                .requestMatchers("/upload").authenticated()
                // Everything else is public (catalog, auth, shop branding, static files, shop signup)
                .anyRequest().permitAll()
            );

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(shopResolutionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
