package com.ecommerce.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Returned by POST /auth/register instead of a JWT.
 * Indicates the user was created successfully and a verification email was sent.
 * The JWT is only issued after POST /auth/verify-email succeeds.
 */
@Getter
@AllArgsConstructor
public class RegisterPendingResponse {
    private String message;
    private String email;
}
