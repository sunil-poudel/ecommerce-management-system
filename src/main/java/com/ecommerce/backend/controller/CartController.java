package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.AddToCartRequest;
import com.ecommerce.backend.dto.CartDto;
import com.ecommerce.backend.service.CartService;
import com.ecommerce.backend.security.ShopContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    private Long getShopId() {
        Long shopId = ShopContext.getShopId();
        if (shopId == null) {
            throw new IllegalArgumentException("Shop context is missing");
        }
        return shopId;
    }

    @GetMapping
    public CartDto getCart(@RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
        return cartService.getCartDto(getShopId(), sessionId);
    }

    @PostMapping("/items")
    public CartDto addToCart(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @Valid @RequestBody AddToCartRequest request) {
        return cartService.addToCart(getShopId(), sessionId, request);
    }

    @PutMapping("/items/{cartItemId}")
    public CartDto updateCartItem(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @PathVariable Long cartItemId,
            @RequestParam int quantity) {
        return cartService.updateQuantity(getShopId(), sessionId, cartItemId, quantity);
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartDto removeCartItem(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @PathVariable Long cartItemId) {
        return cartService.removeCartItem(getShopId(), sessionId, cartItemId);
    }

    @DeleteMapping
    public CartDto clearCart(@RequestHeader(value = "X-Session-ID", required = false) String sessionId) {
        return cartService.clearCart(getShopId(), sessionId);
    }
}
