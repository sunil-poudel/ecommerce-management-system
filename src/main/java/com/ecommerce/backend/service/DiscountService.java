package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.DiscountDto;
import com.ecommerce.backend.entity.Category;
import com.ecommerce.backend.entity.Discount;
import com.ecommerce.backend.entity.DiscountType;
import com.ecommerce.backend.entity.ItemUnit;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.repository.CategoryRepository;
import com.ecommerce.backend.repository.DiscountRepository;
import com.ecommerce.backend.repository.ItemRepository;
import com.ecommerce.backend.repository.ItemUnitRepository;
import com.ecommerce.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountRepository discountRepository;
    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final CategoryRepository categoryRepository;
    private final ShopRepository shopRepository;

    public List<DiscountDto> getAllDiscounts(Long shopId) {
        return discountRepository.findByShopId(shopId, org.springframework.data.domain.PageRequest.of(0, 1000)).getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public DiscountDto getDiscountById(Long shopId, Long id) {
        return discountRepository.findByIdAndShopId(id, shopId)
                .map(this::mapToDto)
                .orElseThrow(() -> new IllegalArgumentException("Discount not found"));
    }

    @Transactional
    public DiscountDto createDiscount(Long shopId, DiscountDto dto) {
        validateDates(dto);
        Shop shop = shopRepository.findById(shopId).orElseThrow();
        Discount discount = new Discount();
        discount.setShop(shop);
        return mapToDto(discountRepository.save(populateDiscount(shopId, discount, dto)));
    }

    @Transactional
    public DiscountDto updateDiscount(Long shopId, Long id, DiscountDto dto) {
        validateDates(dto);
        Discount discount = discountRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Discount not found"));
        return mapToDto(discountRepository.save(populateDiscount(shopId, discount, dto)));
    }

    @Transactional
    public void deleteDiscount(Long shopId, Long id) {
        if (!discountRepository.findByIdAndShopId(id, shopId).isPresent()) {
            throw new IllegalArgumentException("Discount not found");
        }
        discountRepository.deleteById(id);
    }
    
    @Transactional
    public void bulkDeleteDiscounts(Long shopId, List<Long> ids) {
        for (Long id : ids) {
            deleteDiscount(shopId, id);
        }
    }
    
    private void validateDates(DiscountDto dto) {
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date.");
        }
    }

    private Discount populateDiscount(Long shopId, Discount discount, DiscountDto dto) {
        discount.setType(dto.getType());
        discount.setValueType(dto.getValueType());
        discount.setDiscountValue(dto.getDiscountValue());
        discount.setStartDate(dto.getStartDate());
        discount.setEndDate(dto.getEndDate());

        if (dto.getType() == DiscountType.ITEM) {
            // Handle legacy fallback
            if (dto.getItemUnitId() == null && dto.getItemId() != null) {
                com.ecommerce.backend.entity.Item item = itemRepository.findByIdAndShopId(dto.getItemId(), shopId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));
                ItemUnit primary = item.getUnits().stream()
                    .filter(ItemUnit::isPrimary)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Item has no primary unit"));
                dto.setItemUnitId(primary.getId());
            }

            if (dto.getItemUnitId() == null) {
                throw new IllegalArgumentException("Item Unit ID is required for ITEM type discount");
            }
            ItemUnit unit = itemUnitRepository.findById(dto.getItemUnitId())
                    .orElseThrow(() -> new IllegalArgumentException("Item unit not found"));
            if (!unit.getShop().getId().equals(shopId)) {
                throw new IllegalArgumentException("Unit belongs to another shop");
            }
            discount.setItemUnit(unit);
            discount.setCategory(null);
        } else if (dto.getType() == DiscountType.CATEGORY) {
            if (dto.getCategoryId() == null) {
                throw new IllegalArgumentException("Category ID is required for CATEGORY type discount");
            }
            Category category = categoryRepository.findByIdAndShopId(dto.getCategoryId(), shopId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            discount.setCategory(category);
            discount.setItemUnit(null);
        }
        return discount;
    }

    private DiscountDto mapToDto(Discount discount) {
        DiscountDto dto = new DiscountDto();
        dto.setId(discount.getId());
        dto.setType(discount.getType());
        dto.setValueType(discount.getValueType());
        dto.setDiscountValue(discount.getDiscountValue());
        dto.setStartDate(discount.getStartDate());
        dto.setEndDate(discount.getEndDate());

        if (discount.getItemUnit() != null) {
            dto.setItemUnitId(discount.getItemUnit().getId());
            dto.setItemId(discount.getItemUnit().getItem().getId()); // Legacy fallback
            dto.setItemUnitLabel(discount.getItemUnit().getLabel());
            dto.setItemName(discount.getItemUnit().getItem().getName());
        }
        if (discount.getCategory() != null) {
            dto.setCategoryId(discount.getCategory().getId());
            dto.setCategoryName(discount.getCategory().getName());
        }
        return dto;
    }
}
