package com.ecommerce.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ItemDto {
    private Long id;

    @NotBlank(message = "Item name is required")
    private String name;

    @NotBlank(message = "SKU is required")
    private String sku;

    private String description;

    @jakarta.validation.constraints.NotEmpty(message = "At least one unit is required")
    private java.util.List<@jakarta.validation.Valid ItemUnitDto> units = new java.util.ArrayList<>();

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;

    private String imageUrl;
    private java.util.List<String> additionalImageUrls = new java.util.ArrayList<>();

    private java.util.List<String> phoneticSpellings = new java.util.ArrayList<>();
    private java.util.List<ItemLocationDto> locations;

    @NotNull(message = "Category ID is required")
    private Long categoryId;
    private String categoryName;

    // Computed at read-time for convenience in UI lists/cards
    private ItemUnitDto primaryUnit;

    // Legacy getters and setters for frontend backward compatibility
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public BigDecimal getPrice() {
        return primaryUnit != null ? primaryUnit.getPrice() : null;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer getStockQuantity() {
        return primaryUnit != null ? primaryUnit.getStockQuantity() : null;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public BigDecimal getEffectivePrice() {
        return primaryUnit != null ? primaryUnit.getEffectivePrice() : null;
    }

    private ItemUnitDto ensurePrimaryUnit() {
        if (primaryUnit == null) {
            primaryUnit = new ItemUnitDto();
            primaryUnit.setPrimary(true);
            primaryUnit.setLabel("Standard");
            units.add(primaryUnit);
        }
        return primaryUnit;
    }
}
