package com.ecommerce.backend.security;

public class ShopContext {
    private static final ThreadLocal<Long> currentShopId = new ThreadLocal<>();

    private ShopContext() {
        // Utility class with static methods only
    }

    public static void setShopId(Long shopId) {
        currentShopId.set(shopId);
    }

    public static Long getShopId() {
        return currentShopId.get();
    }

    public static void clear() {
        currentShopId.remove();
    }
}
