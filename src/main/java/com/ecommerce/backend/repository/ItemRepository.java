package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByIdAndShopId(Long id, Long shopId);
    java.util.List<Item> findByShopIdAndNeedsPhoneticUpdateTrue(Long shopId);
    Page<Item> findByShopId(Long shopId, Pageable pageable);
    Page<Item> findByShopIdAndCategoryId(Long shopId, Long categoryId, Pageable pageable);
    Page<Item> findByShopIdAndNameContainingIgnoreCase(Long shopId, String name, Pageable pageable);
    boolean existsByShopIdAndCategoryId(Long shopId, Long categoryId);
    
    @Query("SELECT COUNT(i) FROM Item i JOIN i.units u WHERE i.shop.id = :shopId AND u.isPrimary = true AND u.stockQuantity <= :stock")
    long countByShopIdAndStockQuantityLessThanEqual(@Param("shopId") Long shopId, @Param("stock") int stock);
    
    long countByShopId(Long shopId);
    
    boolean existsByShopIdAndSku(Long shopId, String sku);
    boolean existsByShopIdAndSkuAndIdNot(Long shopId, String sku, Long id);

    @Query("SELECT DISTINCT i FROM Item i " +
           "JOIN i.units u ON u.isPrimary = true " +
           "LEFT JOIN Discount d ON (d.itemUnit.id = u.id OR d.category.id = i.category.id) AND d.startDate <= CURRENT_TIMESTAMP AND d.endDate >= CURRENT_TIMESTAMP " +
           "WHERE i.shop.id = :shopId " +
           "AND (:hasCategory = false OR i.category.id IN :categoryIds) " +
           "AND (:search IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:inStock IS NULL OR :inStock = false OR u.stockQuantity > 0) " +
           "AND (:minPrice IS NULL OR u.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR u.price <= :maxPrice) " +
           "AND (:onDiscount IS NULL OR :onDiscount = false OR d.id IS NOT NULL)")
    Page<Item> findItemsWithFilters(
            @Param("shopId") Long shopId,
            @Param("categoryIds") java.util.Collection<Long> categoryIds,
            @Param("hasCategory") boolean hasCategory,
            @Param("search") String search,
            @Param("inStock") Boolean inStock,
            @Param("onDiscount") Boolean onDiscount,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );
}
