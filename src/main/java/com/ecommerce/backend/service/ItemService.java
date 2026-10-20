package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.ItemDto;
import com.ecommerce.backend.dto.ItemLocationDto;
import com.ecommerce.backend.entity.Category;
import com.ecommerce.backend.entity.Item;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.repository.CategoryRepository;
import com.ecommerce.backend.repository.DiscountRepository;
import com.ecommerce.backend.repository.ItemRepository;
import com.ecommerce.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final DiscountCalculationService discountCalculationService;
    private final DiscountRepository discountRepository;
    private final ShopRepository shopRepository;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private CategoryService categoryService;

    public Page<ItemDto> getItems(Long shopId, Long categoryId, String search, Boolean inStock, Boolean onDiscount, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        java.util.Collection<Long> categoryIds = java.util.Collections.singletonList(-1L);
        boolean hasCategory = false;
        if (categoryId != null) {
            categoryIds = categoryService.getAllDescendantCategoryIds(shopId, categoryId);
            hasCategory = true;
        }
        return itemRepository.findItemsWithFilters(shopId, categoryIds, hasCategory, search, inStock, onDiscount, minPrice, maxPrice, pageable)
                .map(this::mapToDtoWithEffectivePrice);
    }

    public ItemDto getItemById(Long shopId, Long id) {
        return itemRepository.findByIdAndShopId(id, shopId)
                .map(this::mapToDtoWithEffectivePrice)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
    }

    public Page<ItemDto> getItemsForAdmin(Long shopId, Long categoryId, String search, Boolean inStock, Boolean onDiscount, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        java.util.Collection<Long> categoryIds = java.util.Collections.singletonList(-1L);
        boolean hasCategory = false;
        if (categoryId != null) {
            categoryIds = categoryService.getAllDescendantCategoryIds(shopId, categoryId);
            hasCategory = true;
        }
        return itemRepository.findItemsWithFilters(shopId, categoryIds, hasCategory, search, inStock, onDiscount, minPrice, maxPrice, pageable)
                .map(this::mapToDtoWithLocations);
    }

    public ItemDto getItemByIdForAdmin(Long shopId, Long id) {
        return itemRepository.findByIdAndShopId(id, shopId)
                .map(this::mapToDtoWithLocations)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
    }

    @Transactional
    public ItemDto createItem(Long shopId, ItemDto dto) {
        if (itemRepository.existsByShopIdAndSku(shopId, dto.getSku())) {
            throw new IllegalArgumentException("An item with this SKU already exists.");
        }
        Shop shop = shopRepository.findById(shopId).orElseThrow();
        Item item = new Item();
        item.setShop(shop);
        return mapToDtoWithLocations(itemRepository.save(populateItem(shopId, item, dto, shop)));
    }

    @Transactional
    public ItemDto updateItem(Long shopId, Long id, ItemDto dto) {
        Item item = itemRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
                
        if (itemRepository.existsByShopIdAndSkuAndIdNot(shopId, dto.getSku(), id)) {
            throw new IllegalArgumentException("An item with this SKU already exists.");
        }
        
        return mapToDtoWithLocations(itemRepository.save(populateItem(shopId, item, dto, item.getShop())));
    }

    @Transactional
    public void deleteItem(Long shopId, Long id) {
        Item item = itemRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
                
        for (com.ecommerce.backend.entity.ItemUnit unit : item.getUnits()) {
            if (discountRepository.existsByShopIdAndItemUnitId(shopId, unit.getId())) {
                throw new IllegalStateException("Cannot delete item because one of its units has linked discounts.");
            }
        }
        itemRepository.deleteById(id);
    }
    @Transactional
    public void bulkDeleteItems(Long shopId, List<Long> ids) {
        for (Long id : ids) {
            deleteItem(shopId, id);
        }
    }
    
    @Transactional
    public void bulkUpdateStock(Long shopId, List<Long> itemIds, int stockToAdd) {
        List<Item> items = itemRepository.findAllById(itemIds);
        for (Item item : items) {
            if (!item.getShop().getId().equals(shopId)) continue;
            for (com.ecommerce.backend.entity.ItemUnit unit : item.getUnits()) {
                if (unit.isPrimary()) {
                    int newStock = unit.getStockQuantity() + stockToAdd;
                    if (newStock < 0) newStock = 0;
                    unit.setStockQuantity(newStock);
                    break;
                }
            }
        }
        itemRepository.saveAll(items);
    }

    private Item populateItem(Long shopId, Item item, ItemDto dto, Shop shop) {
        if (item.getId() == null || (item.getName() != null && !item.getName().equals(dto.getName()))) {
            item.setNeedsPhoneticUpdate(true);
        }
        item.setName(dto.getName());
        item.setSku(dto.getSku());
        item.setDescription(dto.getDescription());
        item.setImageUrl(dto.getImageUrl());
        item.setAdditionalImageUrls(dto.getAdditionalImageUrls() != null ? dto.getAdditionalImageUrls() : new java.util.ArrayList<>());
        
        // Map units
        if (dto.getUnits() == null || dto.getUnits().isEmpty()) {
            throw new IllegalArgumentException("At least one unit is required");
        }
        long primaryCount = dto.getUnits().stream().filter(com.ecommerce.backend.dto.ItemUnitDto::isPrimary).count();
        if (primaryCount != 1) {
            throw new IllegalArgumentException("Exactly one unit must be marked as primary");
        }
        
        java.util.List<com.ecommerce.backend.entity.ItemUnit> currentUnits = new java.util.ArrayList<>(item.getUnits());
        item.getUnits().clear();
        
        for (com.ecommerce.backend.dto.ItemUnitDto unitDto : dto.getUnits()) {
            com.ecommerce.backend.entity.ItemUnit unit = null;
            if (unitDto.getId() != null) {
                unit = currentUnits.stream()
                        .filter(u -> u.getId().equals(unitDto.getId()))
                        .findFirst()
                        .orElse(null);
            }
            if (unit == null) {
                unit = new com.ecommerce.backend.entity.ItemUnit();
                unit.setItem(item);
                unit.setShop(shop);
            }
            unit.setLabel(unitDto.getLabel());
            unit.setDescription(unitDto.getDescription());
            unit.setPrice(unitDto.getPrice());
            unit.setStockQuantity(unitDto.getStockQuantity());
            unit.setPrimary(unitDto.isPrimary());
            item.getUnits().add(unit);
        }
        
        for (com.ecommerce.backend.entity.ItemUnit oldUnit : currentUnits) {
            if (!item.getUnits().contains(oldUnit)) {
                if (discountRepository.existsByShopIdAndItemUnitId(shopId, oldUnit.getId())) {
                    throw new IllegalStateException("Cannot remove unit '" + oldUnit.getLabel() + "' because it has linked discounts.");
                }
            }
        }
        
        Category category = categoryRepository.findByIdAndShopId(dto.getCategoryId(), shopId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        item.setCategory(category);
        
        // Map locations (optional, admin-only field)
        if (dto.getLocations() != null) {
            java.util.List<com.ecommerce.backend.entity.ItemLocation> locationEntities = new java.util.ArrayList<>();
            for (ItemLocationDto locDto : dto.getLocations()) {
                com.ecommerce.backend.entity.ItemLocation loc = new com.ecommerce.backend.entity.ItemLocation();
                loc.setKey(locDto.getKey());
                loc.setValue(locDto.getValue());
                loc.setDescription(locDto.getDescription());
                locationEntities.add(loc);
            }
            item.setLocations(locationEntities);
        }
        // If dto.getLocations() is null, preserve existing locations (no-op)
        
        return item;
    }

    private ItemDto mapToDtoWithEffectivePrice(Item item) {
        ItemDto dto = new ItemDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setSku(item.getSku());
        dto.setDescription(item.getDescription());
        dto.setImageUrl(item.getImageUrl());
        dto.setAdditionalImageUrls(item.getAdditionalImageUrls());
        dto.setPhoneticSpellings(item.getPhoneticSpellings());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        
        if (item.getCategory() != null) {
            dto.setCategoryId(item.getCategory().getId());
            dto.setCategoryName(item.getCategory().getName());
        }
        
        java.util.List<com.ecommerce.backend.dto.ItemUnitDto> unitDtos = new java.util.ArrayList<>();
        for (com.ecommerce.backend.entity.ItemUnit unit : item.getUnits()) {
            com.ecommerce.backend.dto.ItemUnitDto unitDto = new com.ecommerce.backend.dto.ItemUnitDto();
            unitDto.setId(unit.getId());
            unitDto.setLabel(unit.getLabel());
            unitDto.setDescription(unit.getDescription());
            unitDto.setPrice(unit.getPrice());
            unitDto.setStockQuantity(unit.getStockQuantity());
            unitDto.setPrimary(unit.isPrimary());
            
            BigDecimal effectivePrice = discountCalculationService.calculateEffectivePrice(unit);
            unitDto.setEffectivePrice(effectivePrice);
            
            unitDtos.add(unitDto);
            if (unit.isPrimary()) {
                dto.setPrimaryUnit(unitDto);
            }
        }
        dto.setUnits(unitDtos);
        
        return dto;
    }

    private ItemDto mapToDtoWithLocations(Item item) {
        ItemDto dto = mapToDtoWithEffectivePrice(item);
        if (item.getLocations() != null && !item.getLocations().isEmpty()) {
            java.util.List<ItemLocationDto> locationDtos = new java.util.ArrayList<>();
            for (com.ecommerce.backend.entity.ItemLocation loc : item.getLocations()) {
                ItemLocationDto locDto = new ItemLocationDto();
                locDto.setKey(loc.getKey());
                locDto.setValue(loc.getValue());
                locDto.setDescription(loc.getDescription());
                locationDtos.add(locDto);
            }
            dto.setLocations(locationDtos);
        }
        return dto;
    }
}
