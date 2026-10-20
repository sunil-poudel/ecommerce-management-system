package com.ecommerce.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderItemDto {
    private Long id;
    private Long itemId;
    private Long itemUnitId;
    private String itemName;
    private String unitLabel;
    private String unitDescription;
    private String imageUrl;
    private Integer quantity;
    private BigDecimal unitPrice;
}
