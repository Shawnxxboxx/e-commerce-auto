package com.auto.ecommerce.ecommerceauto.template.service;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateUpdateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;

import java.util.List;

public interface SopTemplateService {
    SopTemplateEntity createTemplate(SopTemplateCreateRequest request);

    List<SopTemplateEntity> listTemplates();

    SopTemplateEntity updateTemplate(Long id, SopTemplateUpdateRequest request);
}
