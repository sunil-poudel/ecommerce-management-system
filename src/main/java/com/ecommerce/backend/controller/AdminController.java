package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.AdminDashboardDto;
import com.ecommerce.backend.repository.DiscountRepository;
import com.ecommerce.backend.repository.ItemRepository;
import com.ecommerce.backend.security.ShopContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminController {

    private final ItemRepository itemRepository;
    private final DiscountRepository discountRepository;
    private final com.ecommerce.backend.repository.OrderRepository orderRepository;

    private Long getShopId() {
        Long shopId = ShopContext.getShopId();
        if (shopId == null) {
            throw new IllegalArgumentException("Shop context is missing");
        }
        return shopId;
    }

    @GetMapping
    public AdminDashboardDto getDashboardStats() {
        LocalDateTime now = LocalDateTime.now();
        Long shopId = getShopId();
        
        AdminDashboardDto dto = new AdminDashboardDto();
        dto.setTotalItems(itemRepository.countByShopId(shopId));
        dto.setLowStockItems(itemRepository.countByShopIdAndStockQuantityLessThanEqual(shopId, 10)); // Low stock threshold: 10
        dto.setActiveDiscounts(discountRepository.countActiveForShop(shopId, now));
        
        // Calculate revenue for the last 6 months
        java.util.List<com.ecommerce.backend.entity.Order> orders = orderRepository.findByShopId(shopId, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        java.util.Map<String, Double> monthlyRevenue = new java.util.LinkedHashMap<>();
        
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");
        for (int i = 5; i >= 0; i--) {
            monthlyRevenue.put(now.minusMonths(i).format(formatter), 0.0);
        }
        
        for (com.ecommerce.backend.entity.Order order : orders) {
            if (order.getStatus() != com.ecommerce.backend.entity.OrderStatus.CANCELLED && order.getCreatedAt().isAfter(now.minusMonths(6))) {
                String monthKey = order.getCreatedAt().format(formatter);
                if (monthlyRevenue.containsKey(monthKey)) {
                    monthlyRevenue.put(monthKey, monthlyRevenue.get(monthKey) + (order.getTotal() != null ? order.getTotal().doubleValue() : 0.0));
                }
            }
        }
        
        java.util.List<AdminDashboardDto.RevenuePoint> revenuePoints = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Double> entry : monthlyRevenue.entrySet()) {
            revenuePoints.add(new AdminDashboardDto.RevenuePoint(entry.getKey(), entry.getValue()));
        }
        dto.setRevenueData(revenuePoints);
        
        return dto;
    }
}
