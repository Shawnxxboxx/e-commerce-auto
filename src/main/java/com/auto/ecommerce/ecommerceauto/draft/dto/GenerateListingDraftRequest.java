package com.auto.ecommerce.ecommerceauto.draft.dto;

import lombok.Data;

@Data
public class GenerateListingDraftRequest {
    private Long templateId;
    private String materialPackagePath;
}
