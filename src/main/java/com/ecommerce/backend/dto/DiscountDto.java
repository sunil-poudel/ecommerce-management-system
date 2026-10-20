package com.ecommerce.backend.dto;

import com.ecommerce.backend.entity.DiscountType;
import com.ecommerce.backend.entity.DiscountValueType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DiscountDto {
    private Long id;

    @NotNull(message = "Discount type is required")
    private DiscountType type;

    @NotNull(message = "Discount value type is required")
    private DiscountValueType valueType;

    @NotNull(message = "Discount value is required")
    @Min(value = 0, message = "Discount value cannot be negative")
    private BigDecimal discountValue;

    @NotNull(message = "Start date is required")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startDate;

    @NotNull(message = "End date is required")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endDate;

    private Long itemUnitId;
    
    // Fallback for legacy frontend
    private Long itemId;
    private String itemUnitLabel;
    private String itemName;

    private Long categoryId;
    private String categoryName;
}
