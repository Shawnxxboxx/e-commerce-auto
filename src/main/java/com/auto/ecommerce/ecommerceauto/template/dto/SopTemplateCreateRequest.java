package com.auto.ecommerce.ecommerceauto.template.dto;

import lombok.Data;

@Data
public class SopTemplateCreateRequest {
    private String templateId;
    private String name;
    private String titlePrompt;
    private String mainImagePrompt;
}
