package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.NotificationDto;
import com.ecommerce.backend.entity.Notification;
import com.ecommerce.backend.repository.NotificationRepository;
import com.ecommerce.backend.security.ShopContext;
import com.ecommerce.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public List<NotificationDto> getNotifications() {
        Long shopId = ShopContext.getShopId();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl)) {
            throw new IllegalArgumentException("User not authenticated");
        }
        Long userId = ((UserDetailsImpl) auth.getPrincipal()).getId();

        return notificationRepository.findByShopIdAndUserIdOrderByCreatedAtDesc(shopId, userId).stream().map(n -> {
            NotificationDto dto = new NotificationDto();
            dto.setId(n.getId());
            dto.setTitle(n.getTitle());
            dto.setMessage(n.getMessage());
            dto.setRead(n.isRead());
            dto.setCreatedAt(n.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            return ((UserDetailsImpl) auth.getPrincipal()).getId();
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "User not authenticated");
    }

    @PostMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id) {
        Long shopId = ShopContext.getShopId();
        Long userId = getAuthenticatedUserId();
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId) && n.getShop().getId().equals(shopId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    @DeleteMapping("/{id}")
    public void deleteNotification(@PathVariable Long id) {
        Long shopId = ShopContext.getShopId();
        Long userId = getAuthenticatedUserId();
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId) && n.getShop().getId().equals(shopId)) {
                notificationRepository.delete(n);
            }
        });
    }

    @DeleteMapping
    public void deleteAllNotifications() {
        Long shopId = ShopContext.getShopId();
        Long userId = getAuthenticatedUserId();
        
        List<Notification> notifs = notificationRepository.findByShopIdAndUserIdOrderByCreatedAtDesc(shopId, userId);
        notificationRepository.deleteAll(notifs);
    }
}
