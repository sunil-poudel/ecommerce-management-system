package com.ecommerce.backend.service;

public interface EmailService {
    void sendVerificationEmail(String to, String name, String code, String shopName);
    void sendPasswordResetEmail(String to, String name, String code, String shopName);
}
