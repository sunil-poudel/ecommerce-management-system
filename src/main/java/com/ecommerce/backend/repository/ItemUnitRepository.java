package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.ItemUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemUnitRepository extends JpaRepository<ItemUnit, Long> {
}
