package com.ecommerce.backend.dto;

import lombok.Data;

@Data
public class ShopBrandingDto {
    private String displayName;
    private String logoUrl;
    private String primaryColorHex;
    private String termsAndConditions;
    private String privacyPolicy;
    private String contactEmail;
    private String contactPhone;
    private java.util.List<PaymentMethodDto> paymentMethods;
}
