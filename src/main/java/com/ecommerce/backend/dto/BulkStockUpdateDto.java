package com.ecommerce.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class BulkStockUpdateDto {
    @NotEmpty(message = "Item IDs list cannot be empty")
    private List<Long> itemIds;
    
    @NotNull(message = "Stock to add must be provided")
    private Integer stockToAdd;
}
