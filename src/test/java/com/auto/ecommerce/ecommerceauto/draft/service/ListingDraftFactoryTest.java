package com.auto.ecommerce.ecommerceauto.draft.service;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftStatus;
import com.auto.ecommerce.ecommerceauto.material.model.MaterialTransactionRow;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListingDraftFactoryTest {

    private final ListingDraftFactory factory = new ListingDraftFactory();

    @Test
    void createsGeneratingDraftFromTemplateAndMaterial() {
        SopTemplateEntity template = new SopTemplateEntity();
        template.setId(7L);
        template.setName("女装模板");
        template.setTitlePrompt("生成中英文标题");
        template.setMainImagePrompt("生成商品主图");

        MaterialTransactionRow row = new MaterialTransactionRow();
        row.setColor("黑色");
        row.setSpecification("M");
        row.setStockingMode("JIT备货");
        row.setSkc("SKC-1");
        row.setSku("SKU-1");
        row.setPrice(new BigDecimal("19.9"));
        row.setStock(20);
        row.setLength(new BigDecimal("10"));
        row.setWidth(new BigDecimal("20"));
        row.setHeight(new BigDecimal("3"));
        row.setWeightGram(new BigDecimal("180"));

        ProductMaterialPackage material = new ProductMaterialPackage();
        material.setMaterialPackagePath("/tmp/pkg");
        material.setProductName("黑色连衣裙");
        material.setSourceUrl("https://example.com/item");
        material.setShopName("测试店铺");
        material.setCategoryName("女装/连衣裙");
        material.setBrand("无品牌");
        material.setCategoryAttributes(Map.of("材质", "棉"));
        material.setVariantAttributes(Map.of("颜色", "黑色,白色"));
        material.setSizeChartImagePath("/tmp/pkg/尺码表/b.jpg");
        material.setSizeChartImagePaths(List.of("/tmp/pkg/尺码表/a.jpg", "/tmp/pkg/尺码表/b.jpg"));
        material.setDetailImagePaths(List.of("/tmp/pkg/副图/1.jpg"));
        material.setMainImageSourcePaths(List.of("/tmp/pkg/主图/source.jpg"));
        material.setTransactionRows(List.of(row));

        ListingDraft draft = factory.create("draft-1", template, material, ListingDraftStatus.GENERATING);

        assertThat(draft.getDraftId()).isEqualTo("draft-1");
        assertThat(draft.getTemplateId()).isEqualTo("7");
        assertThat(draft.getTemplateName()).isEqualTo("女装模板");
        assertThat(draft.getTitlePromptSnapshot()).isEqualTo("生成中英文标题");
        assertThat(draft.getMainImagePromptSnapshot()).isEqualTo("生成商品主图");
        assertThat(draft.getStatus()).isEqualTo(ListingDraftStatus.GENERATING);
        assertThat(draft.getChineseTitle()).isEqualTo("黑色连衣裙");
        assertThat(draft.getProductMainImage()).isEqualTo("/tmp/pkg/主图/source.jpg");
        assertThat(draft.getProductSizeChartImage()).isEqualTo("/tmp/pkg/尺码表/b.jpg");
        assertThat(draft.getDescriptionImagePaths()).containsExactly("/tmp/pkg/副图/1.jpg");
        assertThat(draft.getVariantAttributes()).containsEntry("颜色", List.of("黑色", "白色"));
        assertThat(draft.getVariantPreviewImages()).containsExactly("/tmp/pkg/尺码表/a.jpg", "/tmp/pkg/尺码表/b.jpg");
        assertThat(draft.getTransactionInfo()).hasSize(1);
        assertThat(draft.getTransactionInfo().getFirst().getSku()).isEqualTo("SKU-1");
    }
}
