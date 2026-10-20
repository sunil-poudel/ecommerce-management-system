package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Long> {
    Optional<Discount> findByIdAndShopId(Long id, Long shopId);
    Page<Discount> findByShopId(Long shopId, Pageable pageable);

    @Query("SELECT d FROM Discount d WHERE d.shop.id = :shopId AND d.startDate <= :now AND d.endDate >= :now AND d.itemUnit.id = :itemUnitId")
    List<Discount> findActiveForShopAndItemUnit(@Param("shopId") Long shopId, @Param("itemUnitId") Long itemUnitId, @Param("now") LocalDateTime now);

    @Query("SELECT d FROM Discount d WHERE d.shop.id = :shopId AND d.startDate <= :now AND d.endDate >= :now AND d.category.id = :categoryId")
    List<Discount> findActiveForShopAndCategory(@Param("shopId") Long shopId, @Param("categoryId") Long categoryId, @Param("now") LocalDateTime now);
    
    @Query("SELECT d FROM Discount d WHERE d.shop.id = :shopId AND d.startDate <= :now AND d.endDate >= :now")
    List<Discount> findAllActiveForShop(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

    boolean existsByShopIdAndCategoryId(Long shopId, Long categoryId);
    boolean existsByShopIdAndItemUnitId(Long shopId, Long itemUnitId);
    
    @Query("SELECT COUNT(d) FROM Discount d WHERE d.shop.id = :shopId AND d.startDate <= :now AND d.endDate >= :now")
    long countActiveForShop(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);
}
