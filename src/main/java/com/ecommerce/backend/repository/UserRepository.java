package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByShopIdAndEmail(Long shopId, String email);
    boolean existsByEmail(String email);
    boolean existsByShopIdAndEmail(Long shopId, String email);
    Optional<User> findFirstByShopIdAndRole(Long shopId, com.ecommerce.backend.entity.Role role);
    java.util.List<User> findAllByShopIdAndRole(Long shopId, com.ecommerce.backend.entity.Role role);
}
