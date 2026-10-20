package com.ecommerce.backend.dto.auth;

import com.ecommerce.backend.entity.OtpPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendOtpRequest {
    @NotBlank @Email
    private String email;

    @NotNull
    private OtpPurpose purpose;
}
