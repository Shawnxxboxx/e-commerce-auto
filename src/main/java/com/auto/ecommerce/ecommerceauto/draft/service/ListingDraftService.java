package com.auto.ecommerce.ecommerceauto.draft.service;

import com.auto.ecommerce.ecommerceauto.draft.dto.GenerateListingDraftRequest;
import com.auto.ecommerce.ecommerceauto.draft.dto.ListingDraftPageResponse;
import com.auto.ecommerce.ecommerceauto.draft.dto.ListingDraftResponse;
import com.auto.ecommerce.ecommerceauto.draft.entity.ListingDraftEntity;
import com.auto.ecommerce.ecommerceauto.draft.mapper.ListingDraftMapper;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftStatus;
import com.auto.ecommerce.ecommerceauto.draft.publish.ListingDraftToTikTokPublishRequestMapper;
import com.auto.ecommerce.ecommerceauto.draft.validation.ListingDraftValidator;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import com.auto.ecommerce.ecommerceauto.playwright.MabangPublisher;
import com.auto.ecommerce.ecommerceauto.playwright.TikTokPublishRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingDraftService {

    private final ListingDraftMapper draftMapper;
    private final SopTemplateMapper templateMapper;
    private final MaterialPackageParser materialPackageParser;
    private final ListingDraftFactory draftFactory;
    private final ListingDraftGenerationWorker generationWorker;
    private final ListingDraftValidator validator;
    private final ListingDraftToTikTokPublishRequestMapper publishRequestMapper;
    private final MabangPublisher mabangPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ListingDraftResponse startGeneration(GenerateListingDraftRequest request) {
        if (request.getTemplateId() == null) {
            throw new IllegalArgumentException("请选择 SOP 模板");
        }
        if (request.getMaterialPackagePath() == null || request.getMaterialPackagePath().isBlank()) {
            throw new IllegalArgumentException("请选择素材包目录");
        }

        SopTemplateEntity template = templateMapper.selectById(request.getTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("SOP 模板不存在: " + request.getTemplateId());
        }

        ProductMaterialPackage material = materialPackageParser.parse(Path.of(request.getMaterialPackagePath()));
        String draftId = "draft-" + UUID.randomUUID();
        ListingDraft draft = draftFactory.create(draftId, template, material, ListingDraftStatus.GENERATING);
        ListingDraftEntity entity = toEntity(draft);
        draftMapper.insert(entity);
        // 先返回 draftId，前端进入草稿页轮询生成状态。
        generationWorker.generate(draftId);
        return toResponse(entity);
    }

    public ListingDraftResponse get(String draftId) {
        return toResponse(requireEntity(draftId));
    }

    public ListingDraftPageResponse list(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<ListingDraftEntity> all = draftMapper.selectList(new LambdaQueryWrapper<ListingDraftEntity>()
                .orderByDesc(ListingDraftEntity::getUpdateTime));
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());

        ListingDraftPageResponse response = new ListingDraftPageResponse();
        // ponytail: 本地 MVP 草稿量很小；草稿数量变大时再切 MyBatis-Plus DB 分页。
        response.setRecords(all.subList(from, to).stream().map(this::toResponse).toList());
        response.setTotal(all.size());
        response.setPage(safePage);
        response.setSize(safeSize);
        return response;
    }

    public MabangPublisher.PublishResult publish(String draftId) {
        ListingDraftEntity entity = requireEntity(draftId);
        ListingDraft draft = readDraft(entity);
        List<String> errors = validator.validate(draft);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", errors));
        }

        draft.setStatus(ListingDraftStatus.PUBLISHING);
        TikTokPublishRequest request = publishRequestMapper.map(draft);
        entity.setStatus(ListingDraftStatus.PUBLISHING.name());
        entity.setDraftJson(toJson(draft));
        entity.setPublishRequestJson(toJson(request));
        entity.setUpdateTime(LocalDateTime.now());
        draftMapper.updateById(entity);

        MabangPublisher.PublishResult result = mabangPublisher.publish(request);
        draft.setStatus(result.isSuccess() ? ListingDraftStatus.PUBLISHED : ListingDraftStatus.FAILED);
        entity.setStatus(draft.getStatus().name());
        entity.setDraftJson(toJson(draft));
        entity.setLastErrorType(result.isSuccess() ? null : "MabangPublishFailed");
        entity.setLastErrorMessage(result.isSuccess() ? null : result.getMessage());
        entity.setPublishScreenshotPath(result.getScreenshotPath());
        entity.setUpdateTime(LocalDateTime.now());
        draftMapper.updateById(entity);
        return result;
    }

    private ListingDraftEntity requireEntity(String draftId) {
        ListingDraftEntity entity = draftMapper.selectOne(new LambdaQueryWrapper<ListingDraftEntity>()
                .eq(ListingDraftEntity::getDraftId, draftId)
                .last("limit 1"));
        if (entity == null) {
            throw new IllegalArgumentException("草稿不存在: " + draftId);
        }
        return entity;
    }

    private ListingDraftEntity toEntity(ListingDraft draft) {
        LocalDateTime now = LocalDateTime.now();
        ListingDraftEntity entity = new ListingDraftEntity();
        entity.setDraftId(draft.getDraftId());
        entity.setTemplateId(draft.getTemplateId());
        entity.setTemplateName(draft.getTemplateName());
        entity.setTitlePromptSnapshot(draft.getTitlePromptSnapshot());
        entity.setMainImagePromptSnapshot(draft.getMainImagePromptSnapshot());
        entity.setMaterialPackagePath(draft.getMaterialPackagePath());
        entity.setStatus(draft.getStatus().name());
        entity.setDraftJson(toJson(draft));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    private ListingDraftResponse toResponse(ListingDraftEntity entity) {
        ListingDraftResponse response = new ListingDraftResponse();
        response.setDraftId(entity.getDraftId());
        response.setStatus(entity.getStatus());
        response.setDraft(readDraft(entity));
        response.setLastErrorType(entity.getLastErrorType());
        response.setLastErrorMessage(entity.getLastErrorMessage());
        response.setPublishScreenshotPath(entity.getPublishScreenshotPath());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private ListingDraft readDraft(ListingDraftEntity entity) {
        try {
            return objectMapper.readValue(entity.getDraftJson(), ListingDraft.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("草稿 JSON 解析失败: " + entity.getDraftId(), e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
