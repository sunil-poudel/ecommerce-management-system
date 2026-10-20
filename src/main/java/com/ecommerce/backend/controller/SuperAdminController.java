package com.ecommerce.backend.controller;

import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.entity.ShopStatus;
import com.ecommerce.backend.repository.ShopRepository;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.entity.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/super-admin/shops")
@RequiredArgsConstructor
public class SuperAdminController {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<Shop>> getAllShops() {
        return ResponseEntity.ok(shopRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> createShop(@RequestBody Map<String, String> payload) {
        String shopKey = payload.get("shopKey");
        String displayName = payload.get("displayName");
        String adminEmail = payload.get("adminEmail");
        String adminPassword = payload.get("adminPassword");
        
        if (shopKey == null || shopKey.isBlank() || displayName == null || displayName.isBlank() ||
            adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Shop Key, Display Name, Admin Email, and Admin Password are required"));
        }
        
        if (shopRepository.findByShopKey(shopKey).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Shop Key already exists"));
        }

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Admin email is already in use by another account"));
        }
        
        Shop shop = new Shop();
        shop.setShopKey(shopKey);
        shop.setDisplayName(displayName);
        shop.setStatus(ShopStatus.ACTIVE);
        shop.setApprovedAt(LocalDateTime.now());
        shop = shopRepository.save(shop);

        User adminUser = new User();
        adminUser.setEmail(adminEmail);
        adminUser.setPasswordHash(passwordEncoder.encode(adminPassword));
        adminUser.setName(displayName + " Admin");
        adminUser.setRole(Role.SHOP_ADMIN);
        adminUser.setEmailVerified(true);
        adminUser.setCreatedAt(LocalDateTime.now());
        adminUser.setShop(shop);
        userRepository.save(adminUser);
        
        return ResponseEntity.ok(Map.of("message", "Shop and admin account created successfully."));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveShop(@PathVariable Long id) {
        Shop shop = shopRepository.findById(id).orElseThrow();
        shop.setStatus(ShopStatus.ACTIVE);
        shop.setApprovedAt(LocalDateTime.now());
        shopRepository.save(shop);
        // Super admin approval implicitly verifies the shop admin's email
        userRepository.findAllByShopIdAndRole(id, Role.SHOP_ADMIN).forEach(admin -> {
            admin.setEmailVerified(true);
            userRepository.save(admin);
        });
        return ResponseEntity.ok(Map.of("message", "Shop approved successfully."));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<?> suspendShop(@PathVariable Long id) {
        Shop shop = shopRepository.findById(id).orElseThrow();
        shop.setStatus(ShopStatus.SUSPENDED);
        shopRepository.save(shop);
        return ResponseEntity.ok(Map.of("message", "Shop suspended successfully."));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<?> reactivateShop(@PathVariable Long id) {
        Shop shop = shopRepository.findById(id).orElseThrow();
        shop.setStatus(ShopStatus.ACTIVE);
        shopRepository.save(shop);
        return ResponseEntity.ok(Map.of("message", "Shop reactivated successfully."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteShop(@PathVariable Long id) {
        Shop shop = shopRepository.findById(id).orElseThrow();
        shop.setStatus(ShopStatus.ARCHIVED);
        shopRepository.save(shop);
        return ResponseEntity.ok(Map.of("message", "Shop archived successfully."));
    }

    @PutMapping("/{id}/admin-password")
    public ResponseEntity<?> updateAdminPassword(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 6 characters"));
        }
        
        User adminUser = userRepository.findFirstByShopIdAndRole(id, Role.SHOP_ADMIN)
            .orElseThrow(() -> new RuntimeException("Shop admin not found"));
        
        adminUser.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(adminUser);
        return ResponseEntity.ok(Map.of("message", "Admin password updated successfully."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateShopDetails(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        Shop shop = shopRepository.findById(id).orElseThrow();
        String newShopKey = payload.get("shopKey");
        if (newShopKey != null && !newShopKey.isBlank() && !newShopKey.equals(shop.getShopKey())) {
            if (shopRepository.findByShopKey(newShopKey).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Shop Key already exists"));
            }
            shop.setShopKey(newShopKey);
        }
        if (payload.containsKey("displayName")) shop.setDisplayName(payload.get("displayName"));
        if (payload.containsKey("contactEmail")) shop.setContactEmail(payload.get("contactEmail"));
        if (payload.containsKey("contactPhone")) shop.setContactPhone(payload.get("contactPhone"));
        shopRepository.save(shop);
        return ResponseEntity.ok(Map.of("message", "Shop details updated successfully."));
    }
}
