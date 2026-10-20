package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.CategoryDto;
import com.ecommerce.backend.entity.Category;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.repository.CategoryRepository;
import com.ecommerce.backend.repository.DiscountRepository;
import com.ecommerce.backend.repository.ItemRepository;
import com.ecommerce.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final DiscountRepository discountRepository;
    private final ShopRepository shopRepository;

    public List<CategoryDto> getAllCategories(Long shopId) {
        return categoryRepository.findByShopId(shopId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public CategoryDto getCategoryById(Long shopId, Long id) {
        return categoryRepository.findByIdAndShopId(id, shopId)
                .map(this::mapToDto)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
    }

    @Transactional
    public CategoryDto createCategory(Long shopId, CategoryDto dto) {
        if (categoryRepository.existsByShopIdAndName(shopId, dto.getName())) {
            throw new IllegalArgumentException("A category with this name already exists.");
        }
        Shop shop = shopRepository.findById(shopId).orElseThrow();
        Category category = new Category();
        category.setName(dto.getName());
        category.setImageUrl(dto.getImageUrl());
        category.setShop(shop);
        
        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findByIdAndShopId(dto.getParentId(), shopId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            
            if (getDepth(parent) >= 4) {
                throw new IllegalArgumentException("Maximum category depth of 4 reached. Cannot add sub-categories to this level.");
            }
            category.setParent(parent);
        }
        
        return mapToDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto updateCategory(Long shopId, Long id, CategoryDto dto) {
        Category category = categoryRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
                
        if (categoryRepository.existsByShopIdAndNameAndIdNot(shopId, dto.getName(), id)) {
            throw new IllegalArgumentException("A category with this name already exists.");
        }
        
        category.setName(dto.getName());
        category.setImageUrl(dto.getImageUrl());
        
        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(id)) {
                throw new IllegalArgumentException("Category cannot be its own parent");
            }
            Category parent = categoryRepository.findByIdAndShopId(dto.getParentId(), shopId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            
            // Check circular dependency
            if (isCircular(id, parent)) {
                throw new IllegalArgumentException("Circular dependency detected! Cannot set a descendant as a parent.");
            }
            
            // Check max depth constraint
            int parentDepth = getDepth(parent);
            List<Category> allCategories = categoryRepository.findByShopId(shopId);
            int subtreeDepth = getMaxSubtreeDepth(id, allCategories);
            
            if (parentDepth + subtreeDepth > 4) {
                throw new IllegalArgumentException("Updating this category's parent would exceed the maximum allowed depth of 4.");
            }
            
            category.setParent(parent);
        } else {
            category.setParent(null);
        }
        
        return mapToDto(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long shopId, Long id) {
        if (!categoryRepository.findByIdAndShopId(id, shopId).isPresent()) {
            throw new IllegalArgumentException("Category not found");
        }
        if (categoryRepository.existsByShopIdAndParentId(shopId, id)) {
            throw new IllegalStateException("Cannot delete category because it has subcategories. Please reassign or delete them first.");
        }
        if (itemRepository.existsByShopIdAndCategoryId(shopId, id)) {
            throw new IllegalStateException("Cannot delete category because it contains items. Please reassign or delete the items first.");
        }
        if (discountRepository.existsByShopIdAndCategoryId(shopId, id)) {
            throw new IllegalStateException("Cannot delete category because it has linked discounts.");
        }
        categoryRepository.deleteById(id);
    }
    
    @Transactional
    public void bulkDeleteCategories(Long shopId, List<Long> ids) {
        for (Long id : ids) {
            deleteCategory(shopId, id);
        }
    }

    private int getDepth(Category category) {
        int depth = 1;
        Category current = category;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private boolean isCircular(Long targetId, Category newParent) {
        Category current = newParent;
        while (current != null) {
            if (current.getId().equals(targetId)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private int getMaxSubtreeDepth(Long rootId, List<Category> allCategories) {
        List<Category> children = allCategories.stream()
                .filter(c -> c.getParent() != null && c.getParent().getId().equals(rootId))
                .collect(Collectors.toList());
        if (children.isEmpty()) {
            return 1;
        }
        int maxChildDepth = 0;
        for (Category child : children) {
            maxChildDepth = Math.max(maxChildDepth, getMaxSubtreeDepth(child.getId(), allCategories));
        }
        return 1 + maxChildDepth;
    }

    private CategoryDto mapToDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setImageUrl(category.getImageUrl());
        if (category.getParent() != null) {
            dto.setParentId(category.getParent().getId());
            dto.setParentName(category.getParent().getName());
        }
        return dto;
    }

    public java.util.Set<Long> getAllDescendantCategoryIds(Long shopId, Long categoryId) {
        java.util.Set<Long> descendantIds = new java.util.HashSet<>();
        if (categoryId == null) return descendantIds;
        
        descendantIds.add(categoryId);
        fetchDescendantsRecursive(shopId, categoryId, descendantIds);
        return descendantIds;
    }

    private void fetchDescendantsRecursive(Long shopId, Long parentId, java.util.Set<Long> accumulatedIds) {
        List<Long> children = categoryRepository.findChildIdsByShopIdAndParentId(shopId, parentId);
        for (Long childId : children) {
            if (accumulatedIds.add(childId)) {
                fetchDescendantsRecursive(shopId, childId, accumulatedIds);
            }
        }
    }
}
