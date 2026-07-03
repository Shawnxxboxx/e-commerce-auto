package com.auto.ecommerce.ecommerceauto.draft.ai;

import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;

public interface ListingDraftAiGenerator {
    AiDraftGenerationResult generate(SopTemplateEntity template, ProductMaterialPackage material);
}
