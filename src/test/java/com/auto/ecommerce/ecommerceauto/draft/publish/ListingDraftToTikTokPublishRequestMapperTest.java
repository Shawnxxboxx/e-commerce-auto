package com.auto.ecommerce.ecommerceauto.draft.publish;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.playwright.TikTokPublishRequest;
import org.junit.jupiter.api.Test;

import static com.auto.ecommerce.ecommerceauto.draft.validation.ListingDraftValidatorTest.completeDraft;
import static org.assertj.core.api.Assertions.assertThat;

class ListingDraftToTikTokPublishRequestMapperTest {

    private final ListingDraftToTikTokPublishRequestMapper mapper = new ListingDraftToTikTokPublishRequestMapper();

    @Test
    void mapsDraftToTikTokPublishRequest() {
        ListingDraft draft = completeDraft();

        TikTokPublishRequest request = mapper.map(draft);

        assertThat(request.getShopName()).isEqualTo("xxx店铺");
        assertThat(request.getCategoryName()).isEqualTo("家用工具/厨房工具");
        assertThat(request.getChineseTitle()).isEqualTo("黑白剪刀家用厨房剪");
        assertThat(request.getEnglishTitle()).isEqualTo("Black White Kitchen Scissors");
        assertThat(request.getProductMainImage()).endsWith("generated.png");
        assertThat(request.getProductSizeChartImage()).isNull();
        assertThat(request.getDescriptionImagePaths()).hasSize(1);
        assertThat(request.getVariantAttributes()).containsKey("颜色");
        assertThat(request.getTransactionInfo()).hasSize(1);
        assertThat(request.getTransactionInfo().getFirst().getSku()).isEqualTo("黑白剪刀-套装1-均码");
        assertThat(request.getTransactionInfo().getFirst().getPrice()).isEqualTo(5.0);
        assertThat(request.isPublish()).isFalse();
    }
}
