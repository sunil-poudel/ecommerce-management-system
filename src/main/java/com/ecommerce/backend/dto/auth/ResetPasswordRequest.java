package com.ecommerce.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {
    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, max = 6)
    private String code;

    @NotBlank @Size(min = 6)
    private String newPassword;
}
