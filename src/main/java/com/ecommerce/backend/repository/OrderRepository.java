package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByIdAndShopId(Long id, Long shopId);
    List<Order> findByShopIdAndSessionIdOrderByCreatedAtDesc(Long shopId, String sessionId);
    List<Order> findByShopIdAndUserIdOrderByCreatedAtDesc(Long shopId, Long userId);
    Page<Order> findByShopId(Long shopId, Pageable pageable);
    
    @org.springframework.data.jpa.repository.Query("SELECT o FROM Order o WHERE o.shop.id = :shopId " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:search IS NULL OR CAST(o.id AS string) LIKE CONCAT('%', :search, '%') OR LOWER(o.shippingAddress.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Order> findByShopIdAndStatusAndSearch(
            @org.springframework.data.repository.query.Param("shopId") Long shopId, 
            @org.springframework.data.repository.query.Param("status") com.ecommerce.backend.entity.OrderStatus status, 
            @org.springframework.data.repository.query.Param("search") String search, 
            Pageable pageable);
}
