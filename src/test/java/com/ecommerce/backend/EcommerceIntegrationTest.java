package com.ecommerce.backend;

import com.ecommerce.backend.config.TestEmailServiceConfig;
import com.ecommerce.backend.dto.auth.LoginRequest;
import com.ecommerce.backend.dto.auth.RegisterRequest;
import com.ecommerce.backend.dto.CartDto;
import com.ecommerce.backend.dto.CheckoutRequest;
import com.ecommerce.backend.dto.AddToCartRequest;
import com.ecommerce.backend.dto.AddressDto;
import com.ecommerce.backend.entity.Category;
import com.ecommerce.backend.entity.DeliveryOption;
import com.ecommerce.backend.entity.Item;
import com.ecommerce.backend.entity.ItemUnit;
import com.ecommerce.backend.entity.OtpPurpose;
import com.ecommerce.backend.entity.PaymentMethod;
import com.ecommerce.backend.repository.CategoryRepository;
import com.ecommerce.backend.repository.ItemRepository;
import com.ecommerce.backend.repository.ItemUnitRepository;
import com.ecommerce.backend.repository.OtpCodeRepository;
import com.ecommerce.backend.repository.RefreshTokenRepository;
import com.ecommerce.backend.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestEmailServiceConfig.class)
class EcommerceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemUnitRepository itemUnitRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Autowired
    private com.ecommerce.backend.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.ecommerce.backend.repository.ShopRepository shopRepository;

    private com.ecommerce.backend.entity.Shop savedShop;
    private Item savedItem;
    private ItemUnit savedUnit;

    @Autowired
    private com.ecommerce.backend.controller.AuthController authController;

    @BeforeEach
    void setup() {
        // Clear rate limiter buckets to prevent 429 Too Many Requests in tests
        org.springframework.test.util.ReflectionTestUtils.setField(authController, "loginBuckets", new java.util.concurrent.ConcurrentHashMap<>());

        // Delete in FK-safe order (refresh tokens reference users)
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        com.ecommerce.backend.entity.Shop shop = new com.ecommerce.backend.entity.Shop();
        shop.setShopKey("test-shop");
        shop.setDisplayName("Test Shop");
        shop.setStatus(com.ecommerce.backend.entity.ShopStatus.ACTIVE);
        savedShop = shopRepository.save(shop);

        Category category = new Category();
        category.setName("Integration Category");
        category.setShop(savedShop);
        Category savedCategory = categoryRepository.save(category);

        Item item = new Item();
        item.setName("Integration Item");
        item.setSku("INT-ITEM-01");
        item.setCategory(savedCategory);
        item.setShop(savedShop);

        ItemUnit unit = new ItemUnit();
        unit.setLabel("Unit");
        unit.setPrice(new BigDecimal("100.00"));
        unit.setStockQuantity(10);
        unit.setPrimary(true);
        unit.setItem(item);
        unit.setShop(savedShop);
        item.getUnits().add(unit);
        
        savedItem = itemRepository.save(item);
        savedUnit = savedItem.getUnits().iterator().next();
    }

    @Test
    void catalogListing_Public_ReturnsOk() throws Exception {
        mockMvc.perform(get("/items")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON).header("X-Shop-Key", "test-shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Integration Item"));
    }

    @Test
    void authFlow_RegisterLoginAccessProtected() throws Exception {
        final String testEmail = "testuser@example.com";
        final String testPassword = "Password123!";

        // ----------------------------------------------------------------
        // 1. Register — should now return pending verification (NOT a JWT)
        // ----------------------------------------------------------------
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setName("Test User");
        registerReq.setEmail(testEmail);
        registerReq.setPassword(testPassword);

        mockMvc.perform(post("/auth/register")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.email").value(testEmail));
        // NOTE: no $.token expected here — registration no longer auto-logins

        // ----------------------------------------------------------------
        // 2. Confirm login BEFORE verification is blocked (403)
        // ----------------------------------------------------------------
        LoginRequest loginReq = new LoginRequest(testEmail, testPassword);
        mockMvc.perform(post("/auth/login")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden());

        // ----------------------------------------------------------------
        // 3. Retrieve OTP from repository (test profile — no real email sent)
        //    and verify the email via POST /auth/verify-email
        // ----------------------------------------------------------------
        String otpCode = otpCodeRepository
                .findFirstByShopIdAndEmailAndPurposeOrderByCreatedAtDesc(savedShop.getId(), testEmail, OtpPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new AssertionError("No OTP found for " + testEmail))
                .getCode();

        // Attempt with WRONG code first — should return 400
        mockMvc.perform(post("/auth/verify-email")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", testEmail, "code", "000000"))))
                .andExpect(status().isBadRequest());

        // Now verify with the CORRECT code — should return JWT
        MvcResult verifyResult = mockMvc.perform(post("/auth/verify-email")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", testEmail, "code", otpCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String verifyResponseStr = verifyResult.getResponse().getContentAsString();
        Map<String, Object> verifyMap = objectMapper.readValue(verifyResponseStr, new TypeReference<>() {});
        String token = (String) verifyMap.get("token");
        assertNotNull(token, "Token must be present after verify-email");

        // ----------------------------------------------------------------
        // 4. Login should now succeed (email is verified)
        // ----------------------------------------------------------------
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        // ----------------------------------------------------------------
        // 5. Access Protected Customer Endpoint using verify-email token
        // ----------------------------------------------------------------
        mockMvc.perform(get("/orders/my-orders")
                .header("Authorization", "Bearer " + token)
                .header("X-Session-ID", "test-session-123"))
                .andExpect(status().isOk());

        // ----------------------------------------------------------------
        // 6. Access Admin Endpoint — should be Forbidden for a CUSTOMER
        // ----------------------------------------------------------------
        mockMvc.perform(get("/orders/admin")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // ----------------------------------------------------------------
        // 7. Test Malformed Token → 401
        // ----------------------------------------------------------------
        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token + "malformed"))
                .andExpect(status().isUnauthorized());

        // ----------------------------------------------------------------
        // 8. Test Tampered Token → 401
        // ----------------------------------------------------------------
        String[] parts = token.split("\\.");
        if (parts.length == 3) {
            String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"admin@ecommerce.com\"}".getBytes());
            String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];
            mockMvc.perform(get("/notifications")
                    .header("Authorization", "Bearer " + tamperedToken))
                    .andExpect(status().isUnauthorized());
        }

        // ----------------------------------------------------------------
        // 9. Test Wrong Secret Signed Token → 401
        // ----------------------------------------------------------------
        com.ecommerce.backend.security.JwtService wrongSecretJwtService =
                new com.ecommerce.backend.security.JwtService();
        org.springframework.test.util.ReflectionTestUtils.setField(wrongSecretJwtService, "jwtSecret",
                "505E635266556A586E3272357538782F413F4428472B4B6250645367566B5971");
        org.springframework.test.util.ReflectionTestUtils.setField(wrongSecretJwtService, "jwtExpirationMs", 3600000);
        com.ecommerce.backend.entity.User dummy = new com.ecommerce.backend.entity.User();
        dummy.setEmail(testEmail);
        String wrongSecretToken = wrongSecretJwtService.generateJwtTokenFromUser(dummy);
        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + wrongSecretToken))
                .andExpect(status().isUnauthorized());

        // ----------------------------------------------------------------
        // 10. IDOR Test — non-existent order returns 404
        // ----------------------------------------------------------------
        mockMvc.perform(get("/orders/9999")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // ----------------------------------------------------------------
        // 11. IDOR Test — guest order not accessible by authenticated user
        // ----------------------------------------------------------------
        AddToCartRequest addReq = new AddToCartRequest();
        addReq.setItemUnitId(savedUnit.getId());
        addReq.setQuantity(1);
        mockMvc.perform(post("/cart/items")
                .header("X-Session-ID", "guest-session-123")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutReq = new CheckoutRequest();
        checkoutReq.setPaymentMethod(PaymentMethod.CARD);
        checkoutReq.setDeliveryOption(DeliveryOption.STANDARD);
        checkoutReq.setCardNumber("1111222233334444");
        checkoutReq.setCardExpiry("12/26");
        checkoutReq.setCardCvv("123");
        AddressDto address = new AddressDto();
        address.setFullName("Test Person");
        address.setPhone("1234567890");
        address.setAddressLine1("123 Main St");
        address.setCity("Metropolis");
        address.setPostalCode("10001");
        checkoutReq.setShippingAddress(address);

        MvcResult checkoutResult = mockMvc.perform(post("/orders/checkout")
                .header("X-Session-ID", "guest-session-123")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> checkoutMap = objectMapper.readValue(
                checkoutResult.getResponse().getContentAsString(), new TypeReference<>() {});
        Integer orderId = (Integer) checkoutMap.get("id");

        mockMvc.perform(get("/orders/" + orderId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // ----------------------------------------------------------------
        // 12. Forgot Password flow
        // ----------------------------------------------------------------
        mockMvc.perform(post("/auth/forgot-password")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", testEmail))))
                .andExpect(status().isOk());

        // ----------------------------------------------------------------
        // 13. Reset Password — get OTP from repo, reset, confirm login works
        // ----------------------------------------------------------------
        String resetOtpCode = otpCodeRepository
                .findFirstByShopIdAndEmailAndPurposeOrderByCreatedAtDesc(savedShop.getId(), testEmail, OtpPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new AssertionError("No PASSWORD_RESET OTP found"))
                .getCode();

        mockMvc.perform(post("/auth/reset-password")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", testEmail,
                        "code", resetOtpCode,
                        "newPassword", "NewPassword456!"))))
                .andExpect(status().isOk());

        // Login with new password should work
        mockMvc.perform(post("/auth/login")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(testEmail, "NewPassword456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // Login with old password should fail
        mockMvc.perform(post("/auth/login")
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(testEmail, testPassword))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cartToCheckoutFlow() throws Exception {
        String sessionId = "test-session-123";

        // 1. Add to Cart
        AddToCartRequest addReq = new AddToCartRequest();
        addReq.setItemUnitId(savedUnit.getId());
        addReq.setQuantity(2);

        mockMvc.perform(post("/cart/items")
                .header("X-Session-ID", sessionId)
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        // 2. Checkout
        CheckoutRequest checkoutReq = new CheckoutRequest();
        checkoutReq.setPaymentMethod(PaymentMethod.CARD);
        checkoutReq.setDeliveryOption(DeliveryOption.STANDARD);
        checkoutReq.setCardNumber("1111222233334444");
        checkoutReq.setCardExpiry("12/26");
        checkoutReq.setCardCvv("123");

        AddressDto address = new AddressDto();
        address.setFullName("Test Person");
        address.setPhone("1234567890");
        address.setAddressLine1("123 Main St");
        address.setCity("Metropolis");
        address.setPostalCode("10001");
        checkoutReq.setShippingAddress(address);

        mockMvc.perform(post("/orders/checkout")
                .header("X-Session-ID", sessionId)
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.items[0].itemName").value("Integration Item"));

        // 3. Verify Stock Deducted
        ItemUnit updatedUnit = itemUnitRepository.findById(savedUnit.getId()).orElseThrow();
        assert (updatedUnit.getStockQuantity() == 8);
    }

    @Test
    void catalogListing_RecursiveCategory_ReturnsDescendants() throws Exception {
        // Build 3-level category tree
        Category parent = new Category();
        parent.setName("Parent");
        parent.setShop(savedShop);
        parent = categoryRepository.save(parent);

        Category child = new Category();
        child.setName("Child");
        child.setParent(parent);
        child.setShop(savedShop);
        child = categoryRepository.save(child);

        Category grandchild = new Category();
        grandchild.setName("Grandchild");
        grandchild.setParent(child);
        grandchild.setShop(savedShop);
        grandchild = categoryRepository.save(grandchild);

        Category sibling = new Category();
        sibling.setName("Sibling");
        sibling.setParent(parent);
        sibling.setShop(savedShop);
        sibling = categoryRepository.save(sibling);

        // Add items to each category
        Item pItem = new Item();
        pItem.setName("Parent Item");
        pItem.setSku("SKU-P");
        pItem.setCategory(parent);
        pItem.setShop(savedShop);
        ItemUnit pUnit = new ItemUnit();
        pUnit.setLabel("P Unit");
        pUnit.setPrice(new BigDecimal("10.00"));
        pUnit.setStockQuantity(10);
        pUnit.setPrimary(true);
        pUnit.setShop(savedShop);
        pUnit.setItem(pItem);
        pItem.getUnits().add(pUnit);
        itemRepository.save(pItem);

        Item cItem = new Item();
        cItem.setName("Child Item");
        cItem.setSku("SKU-C");
        cItem.setCategory(child);
        cItem.setShop(savedShop);
        ItemUnit cUnit = new ItemUnit();
        cUnit.setLabel("C Unit");
        cUnit.setPrice(new BigDecimal("10.00"));
        cUnit.setStockQuantity(10);
        cUnit.setPrimary(true);
        cUnit.setShop(savedShop);
        cUnit.setItem(cItem);
        cItem.getUnits().add(cUnit);
        itemRepository.save(cItem);

        Item gcItem = new Item();
        gcItem.setName("Grandchild Item");
        gcItem.setSku("SKU-GC");
        gcItem.setCategory(grandchild);
        gcItem.setShop(savedShop);
        ItemUnit gcUnit = new ItemUnit();
        gcUnit.setLabel("GC Unit");
        gcUnit.setPrice(new BigDecimal("10.00"));
        gcUnit.setStockQuantity(10);
        gcUnit.setPrimary(true);
        gcUnit.setShop(savedShop);
        gcUnit.setItem(gcItem);
        gcItem.getUnits().add(gcUnit);
        itemRepository.save(gcItem);

        Item sItem = new Item();
        sItem.setName("Sibling Item");
        sItem.setSku("SKU-S");
        sItem.setCategory(sibling);
        sItem.setShop(savedShop);
        ItemUnit sUnit = new ItemUnit();
        sUnit.setLabel("S Unit");
        sUnit.setPrice(new BigDecimal("10.00"));
        sUnit.setStockQuantity(10);
        sUnit.setPrimary(true);
        sUnit.setShop(savedShop);
        sUnit.setItem(sItem);
        sItem.getUnits().add(sUnit);
        itemRepository.save(sItem);

        // Test querying Parent
        mockMvc.perform(get("/items?categoryId=" + parent.getId())
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON).header("X-Shop-Key", "test-shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        // Test querying Child
        mockMvc.perform(get("/items?categoryId=" + child.getId())
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON).header("X-Shop-Key", "test-shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Test querying Grandchild
        mockMvc.perform(get("/items?categoryId=" + grandchild.getId())
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON).header("X-Shop-Key", "test-shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Test querying Sibling
        mockMvc.perform(get("/items?categoryId=" + sibling.getId())
                .header("X-Shop-Key", "test-shop").contentType(MediaType.APPLICATION_JSON).header("X-Shop-Key", "test-shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
