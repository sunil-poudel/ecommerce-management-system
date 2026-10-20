package com.ecommerce.backend.dto;

import com.ecommerce.backend.entity.DeliveryOption;
import com.ecommerce.backend.entity.OrderStatus;
import com.ecommerce.backend.entity.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDto {
    private Long id;
    private OrderStatus status;
    private AddressDto shippingAddress;
    private DeliveryOption deliveryOption;
    private PaymentMethod paymentMethod;
    private BigDecimal subtotal;

    private BigDecimal total;
    private LocalDateTime createdAt;
    private String maskedCardNumber;
    private String paymentProofUrl;
    private String transactionCode;
    private List<OrderItemDto> items;
}
