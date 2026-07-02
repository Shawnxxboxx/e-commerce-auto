package com.auto.ecommerce.ecommerceauto.draft.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ListingDraftTransactionRow {
    private String color;
    private String size;
    private String stockingMode;
    private String skc;
    private String sku;
    private BigDecimal price;
    private Integer stock;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal weightGram;
    private Boolean enabled = true;
}
