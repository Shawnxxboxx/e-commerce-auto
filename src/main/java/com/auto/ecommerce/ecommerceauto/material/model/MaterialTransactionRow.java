package com.auto.ecommerce.ecommerceauto.material.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialTransactionRow {
    private String color;
    private String specification;
    private String stockingMode;
    private String skc;
    private String sku;
    private BigDecimal price;
    private Integer stock;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal weightGram;
}
