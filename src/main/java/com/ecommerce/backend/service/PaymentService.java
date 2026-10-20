package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.PaymentMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentService {

    /**
     * SIMULATED PAYMENT GATEWAY
     * This method NEVER contacts a real payment processor.
     * It simulates basic validation and a brief delay for realism.
     */
    public boolean processPayment(PaymentMethod method, String cardNumber, String expiry, String cvv) {
        if (method != PaymentMethod.CARD) {
            return true; // No gateway needed for COD/WALLET in this simulation
        }

        // Basic sanity check to mimic validation
        if (!StringUtils.hasText(cardNumber) || cardNumber.replaceAll("\\s+", "").length() < 15) {
            throw new IllegalArgumentException("Invalid card number format");
        }
        if (!StringUtils.hasText(expiry) || !expiry.matches("(0[1-9]|1[0-2])/([0-9]{2})")) {
            throw new IllegalArgumentException("Invalid expiry format (MM/YY required)");
        }
        if (!StringUtils.hasText(cvv) || cvv.length() < 3) {
            throw new IllegalArgumentException("Invalid CVV");
        }

        // Simulate network delay for payment processing
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Payment processing interrupted");
        }

        return true; // Always succeed in simulation
    }

    public String maskCardNumber(String cardNumber) {
        if (!StringUtils.hasText(cardNumber)) return null;
        String clean = cardNumber.replaceAll("\\s+", "");
        if (clean.length() < 4) return clean;
        return "****-****-****-" + clean.substring(clean.length() - 4);
    }
}
