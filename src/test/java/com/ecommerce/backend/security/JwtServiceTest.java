package com.ecommerce.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    // A valid 256-bit secret key encoded in base64
    private final String jwtSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final int jwtExpirationMs = 3600000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", jwtExpirationMs);
    }

    @Test
    void generateJwtTokenFromUser_ValidEmail_ReturnsValidToken() {
        String token = jwtService.generateJwtTokenFromUser(new com.ecommerce.backend.entity.User() {{ setEmail("test@example.com"); }});

        assertNotNull(token);
        assertTrue(jwtService.validateJwtToken(token));
        assertEquals("test@example.com", jwtService.getUserNameFromJwtToken(token));
    }

    @Test
    void validateJwtToken_InvalidToken_ReturnsFalse() {
        assertFalse(jwtService.validateJwtToken("invalid.token.here"));
    }
    
    @Test
    void validateJwtToken_ExpiredToken_ReturnsFalse() throws InterruptedException {
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 1); // 1 ms expiration
        String token = jwtService.generateJwtTokenFromUser(new com.ecommerce.backend.entity.User() {{ setEmail("test@example.com"); }});
        
        Thread.sleep(10); // Wait for expiration
        
        assertFalse(jwtService.validateJwtToken(token));
    }
}
