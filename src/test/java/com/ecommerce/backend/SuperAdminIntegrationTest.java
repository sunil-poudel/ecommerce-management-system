package com.ecommerce.backend;

import com.ecommerce.backend.dto.auth.LoginRequest;
import com.ecommerce.backend.dto.ShopSignupRequest;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.entity.ShopStatus;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.repository.ShopRepository;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.repository.RefreshTokenRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.ecommerce.backend.config.TestEmailServiceConfig.class)
public class SuperAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User superAdmin;

    @BeforeEach
    void setup() {
        // Delete in FK-safe order (refresh tokens reference users)
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        shopRepository.deleteAll();

        superAdmin = new User();
        superAdmin.setName("Super Admin");
        superAdmin.setEmail("superadmin@ecommerce.com");
        superAdmin.setPasswordHash(passwordEncoder.encode("superpass"));
        superAdmin.setRole(Role.SUPER_ADMIN);
        superAdmin.setCreatedAt(java.time.LocalDateTime.now());
        superAdmin.setEmailVerified(true);
        userRepository.save(superAdmin);
    }

    private String getSuperAdminToken() throws Exception {
        LoginRequest loginReq = new LoginRequest("superadmin@ecommerce.com", "superpass");
        MvcResult res = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        
        Map<String, Object> map = objectMapper.readValue(res.getResponse().getContentAsString(), new TypeReference<>() {});
        return (String) map.get("token");
    }

    @Test
    void shopSignupAndApprovalFlow() throws Exception {
        // 1. Signup a new shop
        ShopSignupRequest req = new ShopSignupRequest();
        req.setShopKey("new-shop");
        req.setDisplayName("New Shop");
        req.setContactEmail("contact@newshop.com");
        req.setAdminName("Admin Guy");
        req.setAdminEmail("admin@newshop.com");
        req.setAdminPassword("adminpass");

        mockMvc.perform(post("/shops/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verify shop is created and PENDING
        Shop shop = shopRepository.findByShopKey("new-shop").orElseThrow();
        assertEquals(ShopStatus.PENDING, shop.getStatus());

        // 2. Admin cannot login because shop is PENDING
        LoginRequest adminLogin = new LoginRequest("admin@newshop.com", "adminpass");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isForbidden()); // Account disabled

        // 3. Super Admin logs in
        String token = getSuperAdminToken();

        // 4. Super Admin approves shop
        mockMvc.perform(post("/super-admin/shops/" + shop.getId() + "/approve")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        shop = shopRepository.findById(shop.getId()).orElseThrow();
        assertEquals(ShopStatus.ACTIVE, shop.getStatus());
        assertNotNull(shop.getApprovedAt());

        // 5. Admin can now login
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk());

        // 6. Super Admin suspends shop
        mockMvc.perform(post("/super-admin/shops/" + shop.getId() + "/suspend")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 7. Admin cannot login anymore
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isForbidden());
                
        // 8. Super Admin archives shop
        mockMvc.perform(delete("/super-admin/shops/" + shop.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
                
        shop = shopRepository.findById(shop.getId()).orElseThrow();
        assertEquals(ShopStatus.ARCHIVED, shop.getStatus());
    }
}
