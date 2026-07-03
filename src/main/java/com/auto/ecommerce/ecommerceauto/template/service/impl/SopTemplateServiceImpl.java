package com.auto.ecommerce.ecommerceauto.template.service.impl;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateUpdateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.auto.ecommerce.ecommerceauto.template.service.SopTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SopTemplateServiceImpl implements SopTemplateService {

    private final SopTemplateMapper mapper;

    @Override
    public SopTemplateEntity createTemplate(SopTemplateCreateRequest request) {
        Date now = new Date();
        SopTemplateEntity entity = new SopTemplateEntity();
        entity.setName(request.getName());
        entity.setTitlePrompt(request.getTitlePrompt());
        entity.setMainImagePrompt(request.getMainImagePrompt());
        // gmt 字段不依赖数据库默认值，返回给前端时也能立刻看到时间。
        entity.setGmtCreateTime(now);
        entity.setGmtModifiedTime(now);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public List<SopTemplateEntity> listTemplates() {
        return mapper.selectList(new LambdaQueryWrapper<SopTemplateEntity>()
                .orderByDesc(SopTemplateEntity::getGmtModifiedTime));
    }

    @Override
    public SopTemplateEntity updateTemplate(Long id, SopTemplateUpdateRequest request) {
        SopTemplateEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        entity.setName(request.getName());
        entity.setTitlePrompt(request.getTitlePrompt());
        entity.setMainImagePrompt(request.getMainImagePrompt());
        // 修改时间由服务层统一刷新，避免页面编辑后排序还是旧位置。
        entity.setGmtModifiedTime(new Date());
        mapper.updateById(entity);
        return entity;
    }
}
