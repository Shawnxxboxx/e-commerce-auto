package com.auto.ecommerce.ecommerceauto.playwright;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MabangPublisherTest {

    @Test
    void buildsProductImagesInUploadOrder() {
        MabangPublisher publisher = new MabangPublisher(new PlaywrightProperties());
        TikTokPublishRequest request = TikTokPublishRequest.builder()
                .productMainImage("/tmp/main.jpg")
                .productDetailImages(List.of("/tmp/detail-1.jpg", "/tmp/detail-2.jpg"))
                .build();

        assertThat(publisher.productImages(request))
                .containsExactly("/tmp/main.jpg", "/tmp/detail-1.jpg", "/tmp/detail-2.jpg");
    }
}
