package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByShopIdAndUserIdOrderByCreatedAtDesc(Long shopId, Long userId);
}
