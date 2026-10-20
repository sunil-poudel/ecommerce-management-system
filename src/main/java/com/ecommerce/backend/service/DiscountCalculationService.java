package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.Discount;
import com.ecommerce.backend.entity.DiscountValueType;
import com.ecommerce.backend.entity.ItemUnit;
import com.ecommerce.backend.repository.DiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountCalculationService {

    private final DiscountRepository discountRepository;

    public BigDecimal calculateEffectivePrice(ItemUnit itemUnit) {
        LocalDateTime now = LocalDateTime.now();
        Long shopId = itemUnit.getShop().getId();
        
        // 1. Check unit level discounts
        List<Discount> unitDiscounts = discountRepository.findActiveForShopAndItemUnit(shopId, itemUnit.getId(), now);
        if (!unitDiscounts.isEmpty()) {
            return applyDiscount(itemUnit.getPrice(), unitDiscounts.get(0));
        }
        
        // 2. Check category level discounts
        if (itemUnit.getItem().getCategory() != null) {
            List<Discount> categoryDiscounts = discountRepository.findActiveForShopAndCategory(shopId, itemUnit.getItem().getCategory().getId(), now);
            if (!categoryDiscounts.isEmpty()) {
                return applyDiscount(itemUnit.getPrice(), categoryDiscounts.get(0));
            }
        }
        
        return itemUnit.getPrice();
    }

    private BigDecimal applyDiscount(BigDecimal originalPrice, Discount discount) {
        if (discount.getValueType() == DiscountValueType.FLAT) {
            BigDecimal newPrice = originalPrice.subtract(discount.getDiscountValue());
            return newPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newPrice;
        } else {
            // PERCENT
            BigDecimal percentage = discount.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal deduction = originalPrice.multiply(percentage);
            BigDecimal newPrice = originalPrice.subtract(deduction).setScale(2, RoundingMode.HALF_UP);
            return newPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newPrice;
        }
    }
}
