package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.ItemDto;
import com.ecommerce.backend.service.ItemService;
import com.ecommerce.backend.security.ShopContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    private Long getShopId() {
        Long shopId = ShopContext.getShopId();
        if (shopId == null) {
            throw new IllegalArgumentException("Shop context is missing");
        }
        return shopId;
    }

    @GetMapping("/items")
    public Page<ItemDto> getItems(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Boolean onDiscount,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return itemService.getItems(getShopId(), categoryId, search, inStock, onDiscount, minPrice, maxPrice, pageable);
    }

    @GetMapping("/items/{id}")
    public ItemDto getItemById(@PathVariable Long id) {
        return itemService.getItemById(getShopId(), id);
    }

    @GetMapping("/admin/items")
    public Page<ItemDto> getItemsForAdmin(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Boolean onDiscount,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return itemService.getItemsForAdmin(getShopId(), categoryId, search, inStock, onDiscount, minPrice, maxPrice, pageable);
    }

    @GetMapping("/admin/items/{id}")
    public ItemDto getItemByIdForAdmin(@PathVariable Long id) {
        return itemService.getItemByIdForAdmin(getShopId(), id);
    }

    @PostMapping("/admin/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto createItem(@Valid @RequestBody ItemDto dto) {
        return itemService.createItem(getShopId(), dto);
    }

    @PutMapping("/admin/items/{id}")
    public ItemDto updateItem(@PathVariable Long id, @Valid @RequestBody ItemDto dto) {
        return itemService.updateItem(getShopId(), id, dto);
    }

    @DeleteMapping("/admin/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        itemService.deleteItem(getShopId(), id);
    }
    
    @PostMapping("/admin/items/bulk-stock")
    @ResponseStatus(HttpStatus.OK)
    public void bulkUpdateStock(@RequestBody Map<String, Object> payload) {
        List<Long> itemIds = (List<Long>) payload.get("itemIds");
        Integer stockToAdd = (Integer) payload.get("stockToAdd");
        itemService.bulkUpdateStock(getShopId(), itemIds, stockToAdd);
    }

    @PostMapping("/admin/items/bulk-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bulkDeleteItems(@RequestBody List<Long> ids) {
        itemService.bulkDeleteItems(getShopId(), ids);
    }
}
