package com.auto.ecommerce.ecommerceauto.draft.validation;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftStatus;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftTransactionRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ListingDraftValidatorTest {

    private final ListingDraftValidator validator = new ListingDraftValidator();

    @Test
    void acceptsCompleteDraft() {
        ListingDraft draft = completeDraft();

        assertThat(validator.validate(draft)).isEmpty();
    }

    @Test
    void reportsMissingRequiredFields() {
        ListingDraft draft = new ListingDraft();

        assertThat(validator.validate(draft))
                .contains("缺少中文标题", "缺少产品主图", "缺少交易信息");
    }

    public static ListingDraft completeDraft() {
        ListingDraftTransactionRow row = new ListingDraftTransactionRow();
        row.setColor("套装1");
        row.setSize("均码");
        row.setStockingMode("JIT备货");
        row.setSkc("黑白剪刀");
        row.setSku("黑白剪刀-套装1-均码");
        row.setPrice(new BigDecimal("5"));
        row.setStock(999);
        row.setLength(new BigDecimal("21"));
        row.setWidth(new BigDecimal("9"));
        row.setHeight(new BigDecimal("2"));
        row.setWeightGram(new BigDecimal("150"));

        ListingDraft draft = new ListingDraft();
        draft.setDraftId("draft-1");
        draft.setTemplateId("template-1");
        draft.setTemplateName("TikTok 模板");
        draft.setTitlePromptSnapshot("生成中英文标题");
        draft.setMainImagePromptSnapshot("生成主图");
        draft.setMaterialPackagePath("/tmp/materials/黑白剪刀");
        draft.setStatus(ListingDraftStatus.GENERATED);
        draft.setShopName("xxx店铺");
        draft.setCategoryName("家用工具/厨房工具");
        draft.setCategoryAttributes(Map.of("材质", "不锈钢"));
        draft.setSourceUrl("https://example.com/item/1");
        draft.setChineseTitle("黑白剪刀家用厨房剪");
        draft.setEnglishTitle("Black White Kitchen Scissors");
        draft.setBrand("无品牌");
        draft.setProductMainImage("/tmp/materials/黑白剪刀/主图/generated.png");
        draft.setProductSizeChartImage("/tmp/materials/黑白剪刀/尺码表/size.jpg");
        draft.setDescriptionImagePaths(List.of("/tmp/materials/黑白剪刀/副图/a.jpg"));
        draft.setVariantAttributes(Map.of("颜色", List.of("套装1"), "规格", List.of("均码")));
        draft.setTransactionInfo(List.of(row));
        return draft;
    }
}
