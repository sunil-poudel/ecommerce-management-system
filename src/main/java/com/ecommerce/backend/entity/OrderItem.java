package com.ecommerce.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Snapshot of the item and unit at the time of purchase
    @Column(nullable = false)
    private Long itemId;
    
    @Column(nullable = false)
    private Long itemUnitId;
    
    @Column(nullable = false)
    private String itemName;
    
    @Column(nullable = false)
    private String unitLabel;
    
    private String unitDescription;
    
    private String imageUrl;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;
}
