package com.ecommerce.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryDto {
    private Long id;
    
    @NotBlank(message = "Category name is required")
    private String name;
    
    private Long parentId;
    private String parentName;
    private String imageUrl;
}
