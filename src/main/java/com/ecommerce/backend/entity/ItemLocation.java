package com.ecommerce.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class ItemLocation {
    @Column(name = "`key`")
    private String key;

    @Column(name = "`value`")
    private String value;

    private String description;
}
