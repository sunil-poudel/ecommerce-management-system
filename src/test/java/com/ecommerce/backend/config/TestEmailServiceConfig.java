package com.ecommerce.backend.config;

import com.ecommerce.backend.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Provides a no-op EmailService for the 'test' Spring profile.
 * This prevents the real SMTP EmailServiceImpl from being loaded during tests,
 * ensuring no real emails are sent and no SMTP connection is required.
 *
 * Import this in integration test classes with: @Import(TestEmailServiceConfig.class)
 */
@TestConfiguration
@Profile("test")
public class TestEmailServiceConfig {

    @Bean
    @Primary
    public EmailService mockEmailService() {
        return new EmailService() {
            @Override
            public void sendVerificationEmail(String to, String name, String code, String shopName) {
                // No-op in tests — OTP code is accessible via OtpCodeRepository
            }

            @Override
            public void sendPasswordResetEmail(String to, String name, String code, String shopName) {
                // No-op in tests — OTP code is accessible via OtpCodeRepository
            }
        };
    }
}
