package com.ecommerce.backend;

import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.entity.ShopStatus;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.repository.ShopRepository;
import com.ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Optional database seeder that provisions default administrative accounts and a starter tenant shop.
 * Activate this runner by running with the 'seed' Spring profile:
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("seed")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Running DataSeeder (profile 'seed' active)...");
        Shop defaultShop = seedDefaultShop();
        seedAdminUsers(defaultShop);
        log.info("Data seeding completed successfully.");
    }

    private Shop seedDefaultShop() {
        return shopRepository.findByShopKey("sunil-stationery").orElseGet(() -> {
            Shop shop = new Shop();
            shop.setShopKey("sunil-stationery");
            shop.setDisplayName("Sunil Stationery and Sports Shop");
            shop.setStatus(ShopStatus.ACTIVE);
            shop.setCreatedAt(LocalDateTime.now());
            shop.setApprovedAt(LocalDateTime.now());
            shop.setContactEmail("contact@ecommerce.com");
            shop.setContactPhone("+977-9800000000");
            shop.setPrimaryColorHex("#007bff");
            return shopRepository.save(shop);
        });
    }

    private void seedAdminUsers(Shop shop) {
        if (!userRepository.existsByEmail("superadmin@ecommerce.com")) {
            User superAdmin = new User();
            superAdmin.setName("System Super Admin");
            superAdmin.setEmail("superadmin@ecommerce.com");
            superAdmin.setPasswordHash(passwordEncoder.encode("SuperPassword123!"));
            superAdmin.setRole(Role.SUPER_ADMIN);
            superAdmin.setCreatedAt(LocalDateTime.now());
            superAdmin.setEmailVerified(true);
            userRepository.save(superAdmin);
            log.info("Created default Super Admin: superadmin@ecommerce.com");
        }

        if (!userRepository.existsByEmail("admin@ecommerce.com")) {
            User shopAdmin = new User();
            shopAdmin.setName("Shop Admin");
            shopAdmin.setEmail("admin@ecommerce.com");
            shopAdmin.setPasswordHash(passwordEncoder.encode("AdminPassword123!"));
            shopAdmin.setRole(Role.SHOP_ADMIN);
            shopAdmin.setShop(shop);
            shopAdmin.setCreatedAt(LocalDateTime.now());
            shopAdmin.setEmailVerified(true);
            userRepository.save(shopAdmin);
            log.info("Created default Shop Admin: admin@ecommerce.com");
        }
    }
}
