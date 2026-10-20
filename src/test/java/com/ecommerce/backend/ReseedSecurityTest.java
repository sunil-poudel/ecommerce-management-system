package com.ecommerce.backend;

import com.ecommerce.backend.config.TestEmailServiceConfig;
import com.ecommerce.backend.dto.auth.LoginRequest;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.entity.ShopStatus;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.repository.CategoryRepository;
import com.ecommerce.backend.repository.DiscountRepository;
import com.ecommerce.backend.repository.ItemRepository;
import com.ecommerce.backend.repository.RefreshTokenRepository;
import com.ecommerce.backend.repository.ShopRepository;
import com.ecommerce.backend.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regression tests for the /super-admin/reseed security fix (CVE-equivalent: public DB-wipe endpoint).
 *
 * <p>Prior to the fix, the endpoint was mapped at /public/reseed with no authentication rule,
 * falling through to SecurityConfig's anyRequest().permitAll() catch-all. Any anonymous HTTP
 * client could wipe and reseed the database.
 *
 * <p>After the fix:
 * - The endpoint is at /super-admin/reseed, covered by the existing .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN") rule.
 * - Unauthenticated requests → 401 Unauthorized
 * - Authenticated CUSTOMER or SHOP_ADMIN → 403 Forbidden
 * - Authenticated SUPER_ADMIN → 200 OK (endpoint still functions as an admin tool)
 *
 * <p>Also covers the additional permitAll-fallthrough fixes added in the same session:
 * - POST /upload (anonymous file upload) → now 401
 * - GET /notifications (anonymous notification read) → now 401
 * - GET|PUT /shops/me (shop settings access) → now 401
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEmailServiceConfig.class)
public class ReseedSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private DiscountRepository discountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.ecommerce.backend.controller.AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Shop demoShop;

    @BeforeEach
    void setup() {
        // Reset rate limiter to prevent 429 Too Many Requests across tests
        ReflectionTestUtils.setField(authController, "loginBuckets", new java.util.concurrent.ConcurrentHashMap<>());

        // Clean slate — delete in FK-safe order (refresh tokens reference users)
        refreshTokenRepository.deleteAll();
        discountRepository.deleteAll();
        itemRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        shopRepository.deleteAll();

        // Create the demo shop that ReseedController hard-codes ("demo-shop-1")
        demoShop = new Shop();
        demoShop.setShopKey("demo-shop-1");
        demoShop.setDisplayName("Demo Shop");
        demoShop.setStatus(ShopStatus.ACTIVE);
        demoShop.setCreatedAt(LocalDateTime.now());
        demoShop = shopRepository.save(demoShop);

        // Create a separate shop for the shop admin
        Shop adminShop = new Shop();
        adminShop.setShopKey("admin-shop");
        adminShop.setDisplayName("Admin Shop");
        adminShop.setStatus(ShopStatus.ACTIVE);
        adminShop.setCreatedAt(LocalDateTime.now());
        adminShop = shopRepository.save(adminShop);

        // Super admin (no shop affiliation)
        User superAdmin = new User();
        superAdmin.setName("Super Admin");
        superAdmin.setEmail("superadmin@test.com");
        superAdmin.setPasswordHash(passwordEncoder.encode("superpass"));
        superAdmin.setRole(Role.SUPER_ADMIN);
        superAdmin.setCreatedAt(LocalDateTime.now());
        superAdmin.setEmailVerified(true);
        userRepository.save(superAdmin);

        // Shop admin
        User shopAdmin = new User();
        shopAdmin.setName("Shop Admin");
        shopAdmin.setEmail("shopadmin@test.com");
        shopAdmin.setPasswordHash(passwordEncoder.encode("adminpass"));
        shopAdmin.setRole(Role.SHOP_ADMIN);
        shopAdmin.setShop(adminShop);
        shopAdmin.setCreatedAt(LocalDateTime.now());
        shopAdmin.setEmailVerified(true);
        userRepository.save(shopAdmin);

        // Customer
        User customer = new User();
        customer.setName("Customer");
        customer.setEmail("customer@test.com");
        customer.setPasswordHash(passwordEncoder.encode("custpass"));
        customer.setRole(Role.CUSTOMER);
        customer.setShop(demoShop);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setEmailVerified(true);
        userRepository.save(customer);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> map = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});
        return (String) map.get("token");
    }

    // -----------------------------------------------------------------------
    // CORE REGRESSION: /super-admin/reseed authorization tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SECURITY: GET /super-admin/reseed without token → 401 Unauthorized")
    void reseed_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/super-admin/reseed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SECURITY: GET /super-admin/reseed with CUSTOMER token → 403 Forbidden")
    void reseed_asCustomer_returns403() throws Exception {
        String token = loginAndGetToken("customer@test.com", "custpass");
        mockMvc.perform(get("/super-admin/reseed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SECURITY: GET /super-admin/reseed with SHOP_ADMIN token → 403 Forbidden")
    void reseed_asShopAdmin_returns403() throws Exception {
        String token = loginAndGetToken("shopadmin@test.com", "adminpass");
        mockMvc.perform(get("/super-admin/reseed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SECURITY: GET /super-admin/reseed with SUPER_ADMIN token → not 401/403 (security gate passes)")
    void reseed_asSuperAdmin_securityGatePasses() throws Exception {
        String token = loginAndGetToken("superadmin@test.com", "superpass");
        // The security gate must pass (not 401 or 403). The controller itself may return
        // 200 (MySQL production) or 500 (H2 test — SET FOREIGN_KEY_CHECKS not supported).
        // What matters here is that SUPER_ADMIN is NOT rejected by Spring Security.
        int status = mockMvc.perform(get("/super-admin/reseed")
                        .header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getStatus();
        assertNotEquals(401, status, "SUPER_ADMIN must not get 401 Unauthorized");
        assertNotEquals(403, status, "SUPER_ADMIN must not get 403 Forbidden");
    }

    @Test
    @DisplayName("SECURITY: GET /public/reseed (old path) → not HTTP 200 (no longer accessible)")
    void reseed_oldPublicPath_notSuccessful() throws Exception {
        // The controller no longer maps to /public/reseed. The response must never be 2xx.
        // Spring returns 500 here (ShopResolutionFilter throws without X-Shop-Key) rather
        // than a clean 404 because the filter chain runs before route resolution.
        // The critical assertion is: the old path does NOT return 200 OK.
        int status = mockMvc.perform(get("/public/reseed"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertFalse(status >= 200 && status < 300,
                "Old /public/reseed path must not return any 2xx response, got: " + status);
    }

    // -----------------------------------------------------------------------
    // ADDITIONAL FIXES: other permitAll-fallthrough endpoints
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SECURITY: POST /upload without token → 401 Unauthorized (anonymous file upload blocked)")
    void upload_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/upload")
                        .file("file", "test content".getBytes()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SECURITY: GET /notifications without token → 401 Unauthorized")
    void notifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SECURITY: GET /shops/me without token → 401 Unauthorized")
    void shopMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/shops/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SECURITY: PUT /shops/me without token → 401 Unauthorized")
    void shopMePut_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/shops/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
