package com.ecommerce.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShopSignupRequest {
    @NotBlank(message = "Shop key is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Shop key can only contain lowercase letters, numbers, and hyphens")
    @Size(min = 3, max = 50)
    private String shopKey;

    @NotBlank(message = "Display name is required")
    private String displayName;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    private String contactPhone;

    @NotBlank(message = "Admin name is required")
    private String adminName;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Invalid admin email format")
    private String adminEmail;

    @NotBlank(message = "Admin password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String adminPassword;
}
