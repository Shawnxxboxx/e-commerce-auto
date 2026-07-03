package com.auto.ecommerce.ecommerceauto.draft.service;

import com.auto.ecommerce.ecommerceauto.draft.ai.AiDraftGenerationResult;
import com.auto.ecommerce.ecommerceauto.draft.ai.ListingDraftAiGenerator;
import com.auto.ecommerce.ecommerceauto.draft.entity.ListingDraftEntity;
import com.auto.ecommerce.ecommerceauto.draft.mapper.ListingDraftMapper;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftStatus;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ListingDraftGenerationWorker {

    private final ListingDraftMapper draftMapper;
    private final SopTemplateMapper templateMapper;
    private final MaterialPackageParser materialPackageParser;
    private final ListingDraftFactory draftFactory;
    private final ListingDraftAiGenerator aiGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 放在独立 Bean 中调用，避免同类自调用导致 @Async 不生效。
    @Async
    public void generate(String draftId) {
        ListingDraftEntity entity = findByDraftId(draftId);
        if (entity == null) {
            return;
        }
        try {
            SopTemplateEntity template = templateMapper.selectById(Long.valueOf(entity.getTemplateId()));
            ProductMaterialPackage material = materialPackageParser.parse(Path.of(entity.getMaterialPackagePath()));
            ListingDraft draft = draftFactory.create(draftId, template, material, ListingDraftStatus.GENERATING);
            AiDraftGenerationResult result = aiGenerator.generate(template, material);
            draft.setChineseTitle(result.getChineseTitle());
            draft.setEnglishTitle(result.getEnglishTitle());
            draft.setProductMainImage(result.getMainImagePath());
            draft.setStatus(ListingDraftStatus.GENERATED);

            entity.setStatus(ListingDraftStatus.GENERATED.name());
            entity.setDraftJson(toJson(draft));
            entity.setLastErrorType(null);
            entity.setLastErrorMessage(null);
            entity.setUpdateTime(LocalDateTime.now());
            draftMapper.updateById(entity);
        } catch (Exception e) {
            markFailed(entity, e);
        }
    }

    private ListingDraftEntity findByDraftId(String draftId) {
        return draftMapper.selectOne(new LambdaQueryWrapper<ListingDraftEntity>()
                .eq(ListingDraftEntity::getDraftId, draftId)
                .last("limit 1"));
    }

    private void markFailed(ListingDraftEntity entity, Exception error) {
        entity.setStatus(ListingDraftStatus.FAILED.name());
        entity.setLastErrorType(error.getClass().getSimpleName());
        entity.setLastErrorMessage(error.getMessage());
        entity.setUpdateTime(LocalDateTime.now());
        draftMapper.updateById(entity);
    }

    private String toJson(ListingDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("草稿 JSON 序列化失败", e);
        }
    }
}
