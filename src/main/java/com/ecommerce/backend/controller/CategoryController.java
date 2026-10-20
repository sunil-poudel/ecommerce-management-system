package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.CategoryDto;
import com.ecommerce.backend.service.CategoryService;
import com.ecommerce.backend.security.ShopContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    private Long getShopId() {
        Long shopId = ShopContext.getShopId();
        if (shopId == null) {
            throw new IllegalArgumentException("Shop context is missing");
        }
        return shopId;
    }

    @GetMapping("/categories")
    public List<CategoryDto> getAllCategories() {
        return categoryService.getAllCategories(getShopId());
    }

    @GetMapping("/categories/{id}")
    public CategoryDto getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(getShopId(), id);
    }

    @PostMapping("/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryDto dto) {
        return categoryService.createCategory(getShopId(), dto);
    }

    @PutMapping("/admin/categories/{id}")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDto dto) {
        return categoryService.updateCategory(getShopId(), id, dto);
    }

    @DeleteMapping("/admin/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(getShopId(), id);
    }
    
    @PostMapping("/admin/categories/bulk-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bulkDeleteCategories(@RequestBody List<Long> ids) {
        categoryService.bulkDeleteCategories(getShopId(), ids);
    }
}
