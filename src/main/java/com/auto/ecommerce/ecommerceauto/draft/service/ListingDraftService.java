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
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService;
import com.auto.ecommerce.ecommerceauto.playwright.MabangPublisher;
import com.auto.ecommerce.ecommerceauto.playwright.TikTokPublishRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingDraftService {

    private static final Logger log = LoggerFactory.getLogger(ListingDraftService.class);

    private final ListingDraftMapper draftMapper;
    private final SopTemplateMapper templateMapper;
    private final MaterialPackageParser materialPackageParser;
    private final MaterialPackageService materialPackageService;
    private final ListingDraftFactory draftFactory;
    private final ListingDraftGenerationWorker generationWorker;
    private final ListingDraftValidator validator;
    private final ListingDraftToTikTokPublishRequestMapper publishRequestMapper;
    private final MabangPublisher mabangPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object publishDeleteLock = new Object();

    public ListingDraftResponse startGeneration(GenerateListingDraftRequest request) {
        if (request.getTemplateId() == null) {
            throw new IllegalArgumentException("请选择 SOP 模板");
        }
        if (request.getMaterialPackageId() == null || request.getMaterialPackageId().isBlank()) {
            throw new IllegalArgumentException("请选择素材包");
        }
        if (draftMapper.selectCount(new LambdaQueryWrapper<ListingDraftEntity>()
                .eq(ListingDraftEntity::getMaterialPackageId, request.getMaterialPackageId())) > 0) {
            throw new IllegalArgumentException("该素材包已生成草稿");
        }

        SopTemplateEntity template = templateMapper.selectById(request.getTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("SOP 模板不存在: " + request.getTemplateId());
        }

        Path materialPackagePath = Path.of(materialPackageService.require(request.getMaterialPackageId()).getStoragePath());
        ProductMaterialPackage material = materialPackageParser.parse(materialPackagePath);
        material.setMaterialPackageId(request.getMaterialPackageId());
        material.setMaterialPackagePath(materialPackagePath.toString());
        String draftId = "draft-" + UUID.randomUUID();
        ListingDraft draft = draftFactory.create(draftId, template, material, ListingDraftStatus.GENERATING);
        draft.setMaterialPackageId(request.getMaterialPackageId());
        ListingDraftEntity entity = toEntity(draft);
        draftMapper.insert(entity);
        // 先返回 draftId，前端进入草稿页轮询生成状态。
        generationWorker.generate(draftId);
        return toResponse(entity);
    }

    public ListingDraftResponse get(String draftId) {
        return toResponse(requireEntity(draftId));
    }

    public void delete(String draftId) {
        synchronized (publishDeleteLock) {
            deleteLocked(draftId);
        }
    }

    private void deleteLocked(String draftId) {
        ListingDraftEntity entity = requireEntity(draftId);
        ListingDraftStatus status = ListingDraftStatus.valueOf(entity.getStatus());
        if (status == ListingDraftStatus.GENERATING || status == ListingDraftStatus.PUBLISHING) {
            throw new DraftConflictException("生成或发布中的草稿不能删除");
        }

        String materialPackageId = entity.getMaterialPackageId();
        if (materialPackageId == null || materialPackageId.isBlank()) {
            transactionTemplate.executeWithoutResult(ignored -> deleteDraft(entity));
            return;
        }

        materialPackageService.require(materialPackageId);
        materialPackageService.quarantine(materialPackageId);
        try {
            transactionTemplate.executeWithoutResult(ignored -> {
                deleteDraft(entity);
                materialPackageService.delete(materialPackageId);
            });
        } catch (RuntimeException exception) {
            restoreMaterialPackage(materialPackageId, exception);
            throw exception;
        }

        try {
            materialPackageService.purgeQuarantine(materialPackageId);
        } catch (RuntimeException exception) {
            log.error("清理素材包隔离区失败: {}", materialPackageId, exception);
        }
    }

    public ListingDraftPageResponse list(int page, int size, String keyword, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<ListingDraftEntity> all = draftMapper.selectList(new LambdaQueryWrapper<ListingDraftEntity>()
                .orderByDesc(ListingDraftEntity::getUpdateTime));
        List<ListingDraftResponse> filtered = all.stream()
                .map(this::toResponse)
                .filter(response -> matches(response, keyword, status))
                .toList();
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());

        ListingDraftPageResponse response = new ListingDraftPageResponse();
        // ponytail: 本地 MVP 草稿量很小；草稿数量变大时再切 MyBatis-Plus DB 分页。
        response.setRecords(filtered.subList(from, to));
        response.setTotal(filtered.size());
        response.setPage(safePage);
        response.setSize(safeSize);
        return response;
    }

    private boolean matches(ListingDraftResponse response, String keyword, String status) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase(response.getStatus())) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String target = keyword.toLowerCase();
        ListingDraft draft = response.getDraft();
        return contains(response.getDraftId(), target)
                || contains(response.getStatus(), target)
                || contains(draft.getChineseTitle(), target)
                || contains(draft.getEnglishTitle(), target)
                || contains(draft.getTemplateName(), target)
                || contains(draft.getShopName(), target)
                || contains(draft.getMaterialPackagePath(), target);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    public MabangPublisher.PublishResult publish(String draftId) {
        PublishPreparation preparation;
        synchronized (publishDeleteLock) {
            preparation = preparePublish(draftId);
        }

        MabangPublisher.PublishResult result = mabangPublisher.publish(preparation.request());
        synchronized (publishDeleteLock) {
            finishPublish(draftId, preparation.draft(), result);
        }
        return result;
    }

    private PublishPreparation preparePublish(String draftId) {
        ListingDraftEntity entity = requireEntity(draftId);
        ListingDraftStatus status = ListingDraftStatus.valueOf(entity.getStatus());
        if (status == ListingDraftStatus.GENERATING || status == ListingDraftStatus.PUBLISHING) {
            throw new DraftConflictException("生成或发布中的草稿不能重复发布");
        }
        ListingDraft draft = readDraft(entity);
        enrichQualificationData(draft);
        enrichDescriptionImages(draft);
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
        return new PublishPreparation(draft, request);
    }

    private void finishPublish(String draftId, ListingDraft draft, MabangPublisher.PublishResult result) {
        ListingDraftEntity entity = requireEntity(draftId);
        draft.setStatus(result.isSuccess() ? ListingDraftStatus.PUBLISHED : ListingDraftStatus.FAILED);
        entity.setStatus(draft.getStatus().name());
        entity.setDraftJson(toJson(draft));
        entity.setLastErrorType(result.isSuccess() ? null : "MabangPublishFailed");
        entity.setLastErrorMessage(result.isSuccess() ? null : result.getMessage());
        entity.setPublishScreenshotPath(result.getScreenshotPath());
        entity.setUpdateTime(LocalDateTime.now());
        draftMapper.updateById(entity);
    }

    /**
     * 兼容早期生成的草稿：资质字段接入前保存的 draftJson 没有这些数据，
     * 发布前从素材包重新读取，避免第 8 步因字段为空而被直接跳过。
     */
    private void enrichQualificationData(ListingDraft draft) {
        boolean missingManufacturer = draft.getManufacturer() == null || draft.getManufacturer().isBlank();
        boolean missingResponsiblePerson = draft.getEuResponsiblePerson() == null
                || draft.getEuResponsiblePerson().isBlank();
        boolean missingPackageImages = draft.getPackageImagePaths() == null || draft.getPackageImagePaths().isEmpty();
        if (!missingManufacturer && !missingResponsiblePerson && !missingPackageImages) {
            return;
        }

        ProductMaterialPackage material = materialPackageParser.parse(Path.of(draft.getMaterialPackagePath()));
        if (missingManufacturer) {
            draft.setManufacturer(material.getManufacturer());
        }
        if (missingResponsiblePerson) {
            draft.setEuResponsiblePerson(material.getEuResponsiblePerson());
        }
        if (missingPackageImages) {
            draft.setPackageImagePaths(material.getPackageImagePaths());
        }
    }

    /**
     * 兼容 AI 主图生成前保存的旧草稿，发布时补齐描述图首图。
     */
    private void enrichDescriptionImages(ListingDraft draft) {
        List<String> images = new ArrayList<>();
        if (draft.getProductMainImage() != null && !draft.getProductMainImage().isBlank()) {
            images.add(draft.getProductMainImage());
        }
        if (draft.getProductDetailImages() != null) {
            images.addAll(draft.getProductDetailImages());
        }
        draft.setDescriptionImagePaths(images);
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

    private void deleteDraft(ListingDraftEntity entity) {
        if (draftMapper.deleteById(entity.getId()) != 1) {
            throw new IllegalStateException("草稿数据库删除失败: " + entity.getDraftId());
        }
    }

    private void restoreMaterialPackage(String materialPackageId, RuntimeException original) {
        try {
            materialPackageService.restore(materialPackageId);
        } catch (RuntimeException restoreException) {
            original.addSuppressed(restoreException);
        }
    }

    private ListingDraftEntity toEntity(ListingDraft draft) {
        LocalDateTime now = LocalDateTime.now();
        ListingDraftEntity entity = new ListingDraftEntity();
        entity.setDraftId(draft.getDraftId());
        entity.setTemplateId(draft.getTemplateId());
        entity.setTemplateName(draft.getTemplateName());
        entity.setTitlePromptSnapshot(draft.getTitlePromptSnapshot());
        entity.setMainImagePromptSnapshot(draft.getMainImagePromptSnapshot());
        entity.setMaterialPackageId(draft.getMaterialPackageId());
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
        response.setDraft(toResponseDraft(entity));
        response.setLastErrorType(entity.getLastErrorType());
        response.setLastErrorMessage(entity.getLastErrorMessage());
        response.setPublishScreenshotPath(entity.getPublishScreenshotPath());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private ListingDraft toResponseDraft(ListingDraftEntity entity) {
        ListingDraft draft = readDraft(entity);
        if (entity.getMaterialPackageId() == null || entity.getMaterialPackageId().isBlank()) {
            return draft;
        }

        Path packageRoot = materialPackageService.packagePath(entity.getMaterialPackageId())
                .toAbsolutePath()
                .normalize();
        draft.setProductMainImage(relativeImagePath(packageRoot, draft.getProductMainImage()));
        draft.setProductSizeChartImage(relativeImagePath(packageRoot, draft.getProductSizeChartImage()));
        draft.setProductDetailImages(relativeImagePaths(packageRoot, draft.getProductDetailImages()));
        draft.setVariantPreviewImages(relativeImagePaths(packageRoot, draft.getVariantPreviewImages()));
        draft.setDescriptionImagePaths(relativeImagePaths(packageRoot, draft.getDescriptionImagePaths()));
        draft.setPackageImagePaths(relativeImagePaths(packageRoot, draft.getPackageImagePaths()));
        return draft;
    }

    private List<String> relativeImagePaths(Path packageRoot, List<String> imagePaths) {
        if (imagePaths == null) {
            return null;
        }
        return imagePaths.stream().map(path -> relativeImagePath(packageRoot, path)).toList();
    }

    private String relativeImagePath(Path packageRoot, String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return imagePath;
        }
        Path image = Path.of(imagePath).toAbsolutePath().normalize();
        if (!image.startsWith(packageRoot)) {
            throw new IllegalArgumentException("草稿图片路径不在素材包目录内: " + imagePath);
        }
        return packageRoot.relativize(image).toString();
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

    private record PublishPreparation(ListingDraft draft, TikTokPublishRequest request) {
    }
}
