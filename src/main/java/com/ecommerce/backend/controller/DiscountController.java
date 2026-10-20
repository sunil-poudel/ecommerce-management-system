package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.DiscountDto;
import com.ecommerce.backend.service.DiscountService;
import com.ecommerce.backend.security.ShopContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountService discountService;

    private Long getShopId() {
        Long shopId = ShopContext.getShopId();
        if (shopId == null) {
            throw new IllegalArgumentException("Shop context is missing");
        }
        return shopId;
    }

    @GetMapping("/discounts")
    public List<DiscountDto> getAllDiscounts() {
        return discountService.getAllDiscounts(getShopId());
    }

    @GetMapping("/discounts/{id}")
    public DiscountDto getDiscountById(@PathVariable Long id) {
        return discountService.getDiscountById(getShopId(), id);
    }

    @PostMapping("/admin/discounts")
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountDto createDiscount(@Valid @RequestBody DiscountDto dto) {
        return discountService.createDiscount(getShopId(), dto);
    }

    @PutMapping("/admin/discounts/{id}")
    public DiscountDto updateDiscount(@PathVariable Long id, @Valid @RequestBody DiscountDto dto) {
        return discountService.updateDiscount(getShopId(), id, dto);
    }

    @DeleteMapping("/admin/discounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDiscount(@PathVariable Long id) {
        discountService.deleteDiscount(getShopId(), id);
    }

    @PostMapping("/admin/discounts/bulk-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bulkDeleteDiscounts(@RequestBody List<Long> ids) {
        discountService.bulkDeleteDiscounts(getShopId(), ids);
    }
}
