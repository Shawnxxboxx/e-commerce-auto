package com.auto.ecommerce.ecommerceauto.draft.dto;

import lombok.Data;

import java.util.List;

@Data
public class ListingDraftPageResponse {
    private List<ListingDraftResponse> records;
    private long total;
    private int page;
    private int size;
}
