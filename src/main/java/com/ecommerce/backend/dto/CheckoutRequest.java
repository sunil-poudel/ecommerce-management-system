package com.ecommerce.backend.dto;

import com.ecommerce.backend.entity.DeliveryOption;
import com.ecommerce.backend.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutRequest {
    @Valid
    @NotNull(message = "Shipping address is required")
    private AddressDto shippingAddress;

    @NotNull(message = "Delivery option is required")
    private DeliveryOption deliveryOption;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    // Optional card details for mock payment
    private String cardNumber;
    private String cardExpiry;
    private String cardCvv;
    
    // eSewa payment proof
    private String paymentProofUrl;
    private String transactionCode;
}
