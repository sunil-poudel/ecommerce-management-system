package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.CheckoutRequest;
import com.ecommerce.backend.dto.OrderDto;
import com.ecommerce.backend.entity.OrderStatus;
import com.ecommerce.backend.service.OrderService;
import com.ecommerce.backend.security.ShopContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private Long getShopId() {
        Long shopId = ShopContext.getShopId();
        if (shopId == null) {
            throw new IllegalArgumentException("Shop context is missing");
        }
        return shopId;
    }

    @PostMapping("/checkout")
    
    public OrderDto checkout(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(getShopId(), sessionId, request);
    }

    @GetMapping("/my-orders")
    public List<OrderDto> getMyOrders(@RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
        return orderService.getOrdersForSession(getShopId(), sessionId);
    }

    @GetMapping("/{id}")
    public OrderDto getOrderById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
        return orderService.getOrderById(getShopId(), id, sessionId);
    }

    // Admin endpoints
    @GetMapping("/admin")
    public org.springframework.data.domain.Page<OrderDto> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search) {
        return orderService.getAllOrders(getShopId(), page, size, status, search);
    }

    @PutMapping("/admin/{id}/status")
    public OrderDto updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return orderService.updateOrderStatus(getShopId(), id, status);
    }
    
    @PostMapping("/{id}/cancel")
    public OrderDto cancelOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
        return orderService.cancelOrder(getShopId(), id, sessionId);
    }
}
