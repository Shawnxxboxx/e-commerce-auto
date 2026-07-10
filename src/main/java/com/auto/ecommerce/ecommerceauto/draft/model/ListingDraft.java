package com.auto.ecommerce.ecommerceauto.draft.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ListingDraft {
    private String draftId;
    private String templateId;
    private String templateName;
    private String titlePromptSnapshot;
    private String mainImagePromptSnapshot;
    private String materialPackagePath;
    private ListingDraftStatus status = ListingDraftStatus.GENERATED;

    private String shopName;
    private String categoryName;
    private Map<String, String> categoryAttributes = new LinkedHashMap<>();
    private String sourceUrl;
    private String chineseTitle;
    private String englishTitle;
    private String brand = "无品牌";
    private String manufacturer;
    private String euResponsiblePerson;

    private String picSetType;
    private String productMainImage;
    private String productSizeChartImage;
    private List<String> productDetailImages = new ArrayList<>();
    private List<String> descriptionImagePaths = new ArrayList<>();
    private List<String> packageImagePaths = new ArrayList<>();

    private Map<String, List<String>> variantAttributes = new LinkedHashMap<>();
    private List<String> variantPreviewImages = new ArrayList<>();
    private List<ListingDraftTransactionRow> transactionInfo = new ArrayList<>();
    private boolean publish;
}
