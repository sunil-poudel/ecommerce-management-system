package com.ecommerce.backend.dto;

import lombok.Data;

@Data
public class ShopUpdateRequest {
    private String displayName;
    private String logoUrl;
    private String primaryColorHex;
    private String contactEmail;
    private String contactPhone;
    private String termsAndConditions;
    private String privacyPolicy;
    private java.util.List<PaymentMethodDto> paymentMethods;
}
