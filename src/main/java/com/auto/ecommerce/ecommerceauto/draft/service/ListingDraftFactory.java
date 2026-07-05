package com.auto.ecommerce.ecommerceauto.draft.service;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftStatus;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftTransactionRow;
import com.auto.ecommerce.ecommerceauto.material.model.MaterialTransactionRow;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ListingDraftFactory {

    public ListingDraft create(String draftId, SopTemplateEntity template, ProductMaterialPackage material, ListingDraftStatus status) {
        ListingDraft draft = new ListingDraft();
        draft.setDraftId(draftId);
        draft.setTemplateId(String.valueOf(template.getId()));
        draft.setTemplateName(template.getName());
        draft.setTitlePromptSnapshot(template.getTitlePrompt());
        draft.setMainImagePromptSnapshot(template.getMainImagePrompt());
        draft.setMaterialPackagePath(material.getMaterialPackagePath());
        draft.setStatus(status);
        draft.setShopName(material.getShopName());
        draft.setCategoryName(material.getCategoryName());
        draft.setCategoryAttributes(material.getCategoryAttributes());
        draft.setSourceUrl(material.getSourceUrl());
        draft.setChineseTitle(material.getProductName());
        draft.setEnglishTitle("");
        draft.setBrand(material.getBrand());
        draft.setProductMainImage(first(material.getMainImageSourcePaths()));
        draft.setProductDetailImages(material.getDetailImagePaths());
        draft.setDescriptionImagePaths(material.getDetailImagePaths());
        draft.setVariantAttributes(splitVariantAttributes(material.getVariantAttributes()));
        draft.setVariantPreviewImages(material.getSizeChartImagePaths());
        draft.setTransactionInfo(material.getTransactionRows().stream().map(this::mapTransactionRow).toList());
        return draft;
    }

    private Map<String, List<String>> splitVariantAttributes(Map<String, String> attributes) {
        return attributes.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Arrays.stream(entry.getValue().split(","))
                                .map(String::trim)
                                .filter(value -> !value.isBlank())
                                .toList()));
    }

    private ListingDraftTransactionRow mapTransactionRow(MaterialTransactionRow row) {
        ListingDraftTransactionRow target = new ListingDraftTransactionRow();
        target.setColor(row.getColor());
        target.setSize(row.getSpecification());
        target.setStockingMode(row.getStockingMode());
        target.setSkc(row.getSkc());
        target.setSku(row.getSku());
        target.setPrice(row.getPrice());
        target.setStock(row.getStock());
        target.setLength(row.getLength());
        target.setWidth(row.getWidth());
        target.setHeight(row.getHeight());
        target.setWeightGram(row.getWeightGram());
        return target;
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
