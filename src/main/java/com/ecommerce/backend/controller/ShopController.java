package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.ShopSignupRequest;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.entity.ShopStatus;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.repository.ShopRepository;
import com.ecommerce.backend.repository.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.ecommerce.backend.security.UserDetailsImpl;
import com.ecommerce.backend.dto.ShopBrandingDto;
import com.ecommerce.backend.dto.ShopUpdateRequest;
import com.ecommerce.backend.dto.PaymentMethodDto;


@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor
public class ShopController {

    @GetMapping("/{shopKey}/branding")
    public ResponseEntity<ShopBrandingDto> getShopBranding(@PathVariable String shopKey) {
        return shopRepository.findByShopKey(shopKey).map(shop -> {
            ShopBrandingDto dto = new ShopBrandingDto();
            dto.setDisplayName(shop.getDisplayName());
            dto.setLogoUrl(shop.getLogoUrl());
            dto.setPrimaryColorHex(shop.getPrimaryColorHex());
            dto.setTermsAndConditions(shop.getTermsAndConditions());
            dto.setPrivacyPolicy(shop.getPrivacyPolicy());
            dto.setContactEmail(shop.getContactEmail());
            dto.setContactPhone(shop.getContactPhone());
            
            try {
                if (shop.getPaymentMethods() != null && !shop.getPaymentMethods().isEmpty()) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.List<PaymentMethodDto> methods = mapper.readValue(shop.getPaymentMethods(), 
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<PaymentMethodDto>>(){});
                    dto.setPaymentMethods(methods);
                } else {
                    dto.setPaymentMethods(new java.util.ArrayList<>());
                }
            } catch (Exception e) {
                dto.setPaymentMethods(new java.util.ArrayList<>());
            }
            
            return ResponseEntity.ok(dto);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me")
    public ResponseEntity<ShopBrandingDto> getMyShop(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getShopId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return shopRepository.findById(userDetails.getShopId()).map(shop -> {
            ShopBrandingDto dto = new ShopBrandingDto();
            dto.setDisplayName(shop.getDisplayName());
            dto.setLogoUrl(shop.getLogoUrl());
            dto.setPrimaryColorHex(shop.getPrimaryColorHex());
            dto.setTermsAndConditions(shop.getTermsAndConditions());
            dto.setPrivacyPolicy(shop.getPrivacyPolicy());
            dto.setContactEmail(shop.getContactEmail());
            dto.setContactPhone(shop.getContactPhone());
            return ResponseEntity.ok(dto);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyShop(@RequestBody ShopUpdateRequest request, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getShopId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return shopRepository.findById(userDetails.getShopId()).map(shop -> {
            shop.setDisplayName(request.getDisplayName());
            shop.setLogoUrl(request.getLogoUrl());
            shop.setPrimaryColorHex(request.getPrimaryColorHex());
            shop.setContactEmail(request.getContactEmail());
            shop.setContactPhone(request.getContactPhone());
            shop.setTermsAndConditions(request.getTermsAndConditions());
            if (request.getPrivacyPolicy() != null) shop.setPrivacyPolicy(request.getPrivacyPolicy());
            
            if (request.getPaymentMethods() != null) {
                try {
                    java.util.List<PaymentMethodDto> methods = request.getPaymentMethods();
                    methods.sort(java.util.Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 999));
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    shop.setPaymentMethods(mapper.writeValueAsString(methods));
                } catch (Exception e) {
                    // skip
                }
            }

            shopRepository.save(shop);
            return ResponseEntity.ok(Map.of("message", "Shop updated successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }


    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.ecommerce.backend.service.OtpService otpService;

    // IP-based rate limiting (5 attempts per minute)
    private final Map<String, Bucket> signupBuckets = new ConcurrentHashMap<>();

    private Bucket getSignupBucket(String ipAddress) {
        return signupBuckets.computeIfAbsent(ipAddress, k -> {
            Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(limit).build();
        });
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signupShop(@Valid @RequestBody ShopSignupRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        Bucket bucket = getSignupBucket(ip);
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many requests. Please try again later."));
        }

        if (shopRepository.findByShopKey(request.getShopKey()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Shop key is already taken."));
        }

        if (userRepository.existsByEmail(request.getAdminEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Email is already in use."));
        }

        Shop shop = new Shop();
        shop.setShopKey(request.getShopKey());
        shop.setDisplayName(request.getDisplayName());
        shop.setContactEmail(request.getContactEmail());
        shop.setContactPhone(request.getContactPhone());
        shop.setStatus(ShopStatus.PENDING);
        shop.setCreatedAt(LocalDateTime.now());
        
        Shop savedShop = shopRepository.save(shop);

        User adminUser = new User();
        adminUser.setName(request.getAdminName());
        adminUser.setEmail(request.getAdminEmail());
        adminUser.setPasswordHash(passwordEncoder.encode(request.getAdminPassword()));
        adminUser.setRole(Role.SHOP_ADMIN);
        adminUser.setShop(savedShop);
        adminUser.setCreatedAt(LocalDateTime.now());
        adminUser.setEmailVerified(false);
        userRepository.save(adminUser);

        try {
            otpService.generateAndSendOtp(
                    savedShop.getId(),
                    request.getAdminEmail(), 
                    com.ecommerce.backend.entity.OtpPurpose.EMAIL_VERIFICATION, 
                    request.getAdminName()
            );
        } catch (Exception e) {
            // Ignore OTP send errors here to allow resend later
        }

        return ResponseEntity.ok(Map.of(
            "message", "Shop signup successful. Please verify your email.",
            "shopId", savedShop.getId(),
            "email", adminUser.getEmail()
        ));
    }
}
