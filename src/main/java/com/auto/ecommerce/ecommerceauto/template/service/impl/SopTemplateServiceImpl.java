package com.auto.ecommerce.ecommerceauto.template.service.impl;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateUpdateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.auto.ecommerce.ecommerceauto.template.service.SopTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SopTemplateServiceImpl implements SopTemplateService {

    private final SopTemplateMapper mapper;

    @Override
    public SopTemplateEntity createTemplate(SopTemplateCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        SopTemplateEntity entity = new SopTemplateEntity();
        entity.setTemplateId(request.getTemplateId());
        entity.setName(request.getName());
        entity.setTitlePrompt(request.getTitlePrompt());
        entity.setMainImagePrompt(request.getMainImagePrompt());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public List<SopTemplateEntity> listTemplates() {
        return mapper.selectList(new LambdaQueryWrapper<SopTemplateEntity>()
                .orderByDesc(SopTemplateEntity::getUpdateTime));
    }

    @Override
    public SopTemplateEntity updateTemplate(String templateId, SopTemplateUpdateRequest request) {
        SopTemplateEntity entity = mapper.selectOne(new LambdaQueryWrapper<SopTemplateEntity>()
                .eq(SopTemplateEntity::getTemplateId, templateId));
        if (entity == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        entity.setName(request.getName());
        entity.setTitlePrompt(request.getTitlePrompt());
        entity.setMainImagePrompt(request.getMainImagePrompt());
        entity.setUpdateTime(LocalDateTime.now());
        mapper.updateById(entity);
        return entity;
    }
}
