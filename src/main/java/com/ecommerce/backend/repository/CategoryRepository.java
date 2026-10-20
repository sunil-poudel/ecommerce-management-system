package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByIdAndShopId(Long id, Long shopId);
    java.util.List<Category> findByShopId(Long shopId);
    boolean existsByShopIdAndParentId(Long shopId, Long parentId);
    boolean existsByShopIdAndName(Long shopId, String name);
    boolean existsByShopIdAndNameAndIdNot(Long shopId, String name, Long id);

    @org.springframework.data.jpa.repository.Query("SELECT c.id FROM Category c WHERE c.shop.id = :shopId AND c.parent.id = :parentId")
    java.util.List<Long> findChildIdsByShopIdAndParentId(@org.springframework.data.repository.query.Param("shopId") Long shopId, @org.springframework.data.repository.query.Param("parentId") Long parentId);
}
