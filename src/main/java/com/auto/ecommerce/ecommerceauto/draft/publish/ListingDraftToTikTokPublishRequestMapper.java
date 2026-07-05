package com.auto.ecommerce.ecommerceauto.draft.publish;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftTransactionRow;
import com.auto.ecommerce.ecommerceauto.playwright.TikTokPublishRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ListingDraftToTikTokPublishRequestMapper {

    public TikTokPublishRequest map(ListingDraft draft) {
        return TikTokPublishRequest.builder()
                .shopName(draft.getShopName())
                .categoryName(draft.getCategoryName())
                .categoryAttributes(draft.getCategoryAttributes())
                .sourceUrl(draft.getSourceUrl())
                .chineseTitle(draft.getChineseTitle())
                .englishTitle(draft.getEnglishTitle())
                .brand(draft.getBrand())
                .picSetType(draft.getPicSetType())
                .productMainImage(draft.getProductMainImage())
                .productDetailImages(draft.getProductDetailImages())
                .descriptionImagePaths(draft.getDescriptionImagePaths())
                .variantAttributes(draft.getVariantAttributes())
                .variantPreviewImages(draft.getVariantPreviewImages())
                .transactionInfo(draft.getTransactionInfo().stream()
                        .map(this::mapTransactionRow)
                        .toList())
                .publish(draft.isPublish())
                .build();
    }

    private TikTokPublishRequest.TransactionRow mapTransactionRow(ListingDraftTransactionRow row) {
        return TikTokPublishRequest.TransactionRow.builder()
                .color(row.getColor())
                .size(row.getSize())
                .stockingMode(row.getStockingMode())
                .skc(row.getSkc())
                .sku(row.getSku())
                .price(toDouble(row.getPrice()))
                .stock(row.getStock())
                .length(toDouble(row.getLength()))
                .width(toDouble(row.getWidth()))
                .height(toDouble(row.getHeight()))
                .weight(toDouble(row.getWeightGram()))
                .enabled(row.getEnabled())
                .build();
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
