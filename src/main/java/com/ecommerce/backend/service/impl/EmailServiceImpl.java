package com.ecommerce.backend.service.impl;

import com.ecommerce.backend.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Real SMTP email service, active in all profiles EXCEPT 'test'.
 * In the 'test' profile, TestEmailServiceConfig registers a no-op mock instead.
 *
 * IMPORTANT: The OTP code is NEVER logged here. Only a confirmation of dispatch is logged.
 */
@Service
@Profile("!test")
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${mail.from:noreply@ecommerce.com}")
    private String fromAddress;

    @Override
    public void sendVerificationEmail(String to, String name, String code, String shopName) {
        String html = buildEmailHtml(name, code,
                "Verify Your Email Address",
                "You're almost there! Enter the 6-digit code below to verify your email address and activate your account.",
                shopName);
        sendHtml(to, "Verify your email — " + shopName, html);
        // SECURITY: Never log the code — only log masked address and purpose
        logger.info("Verification email dispatched to address ending in {}", maskEmail(to));
    }

    @Override
    public void sendPasswordResetEmail(String to, String name, String code, String shopName) {
        String html = buildEmailHtml(name, code,
                "Reset Your Password",
                "We received a request to reset your password. Enter the code below to proceed. If you did not make this request, you can safely ignore this email.",
                shopName);
        sendHtml(to, "Reset your password — " + shopName, html);
        logger.info("Password reset email dispatched to address ending in {}", maskEmail(to));
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            logger.error("Failed to dispatch email to address ending in {}: {}", maskEmail(to), e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private String buildEmailHtml(String name, String code, String heading, String message, String shopName) {
        return "<!DOCTYPE html>" +
            "<html><head><meta charset='UTF-8'></head>" +
            "<body style='margin:0;padding:0;background:#f0f2f5;font-family:Arial,Helvetica,sans-serif'>" +
            "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:40px 20px'>" +
            "<div style='max-width:500px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)'>" +
            "  <div style='background:linear-gradient(135deg,#6c63ff,#4ecdc4);padding:32px;text-align:center'>" +
            "    <h1 style='color:#fff;margin:0;font-size:24px;font-weight:700'>🛍️ " + escapeHtml(shopName) + "</h1>" +
            "  </div>" +
            "  <div style='padding:40px'>" +
            "    <h2 style='color:#1a1a2e;font-size:20px;margin:0 0 12px'>" + heading + "</h2>" +
            "    <p style='color:#555;margin:0 0 24px;line-height:1.6'>Hi " + escapeHtml(name) + ",</p>" +
            "    <p style='color:#555;margin:0 0 28px;line-height:1.6'>" + message + "</p>" +
            "    <div style='background:#f8f8ff;border:2px solid #6c63ff;border-radius:12px;padding:28px;text-align:center;margin:0 0 24px'>" +
            "      <div style='font-size:38px;font-weight:800;letter-spacing:12px;color:#1a1a2e;font-family:monospace'>" + code + "</div>" +
            "    </div>" +
            "    <p style='color:#888;font-size:14px;margin:0 0 8px'>⏱ This code expires in <strong>10 minutes</strong>.</p>" +
            "    <p style='color:#aaa;font-size:12px;margin:0;line-height:1.5'>If you didn't request this, please ignore this email. No action is needed.</p>" +
            "  </div>" +
            "  <div style='background:#f8f8ff;padding:20px;text-align:center'>" +
            "    <p style='color:#aaa;font-size:11px;margin:0'>© 2025 " + escapeHtml(shopName) + ". All rights reserved.</p>" +
            "  </div>" +
            "</div>" +
            "</td></tr></table>" +
            "</body></html>";
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "****";
        if (at <= 4) return "****" + email.substring(at);
        return email.substring(at - 4, at) + email.substring(at);
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
