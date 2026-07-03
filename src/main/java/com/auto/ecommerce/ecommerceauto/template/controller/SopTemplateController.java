package com.auto.ecommerce.ecommerceauto.template.controller;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateUpdateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.service.SopTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sop-templates")
public class SopTemplateController {

    private final SopTemplateService service;

    @GetMapping
    public List<SopTemplateEntity> list() {
        return service.listTemplates();
    }

    @PostMapping
    public SopTemplateEntity create(@RequestBody SopTemplateCreateRequest request) {
        return service.createTemplate(request);
    }

    @PutMapping("/{id}")
    public SopTemplateEntity update(@PathVariable Long id, @RequestBody SopTemplateUpdateRequest request) {
        return service.updateTemplate(id, request);
    }
}
