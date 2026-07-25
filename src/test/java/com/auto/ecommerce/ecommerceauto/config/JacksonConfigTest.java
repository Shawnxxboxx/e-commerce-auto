package com.auto.ecommerce.ecommerceauto.config;

import com.auto.ecommerce.ecommerceauto.draft.dto.ListingDraftResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void serializesListingDraftResponseWithLocalDateTime() throws Exception {
        ListingDraftResponse response = new ListingDraftResponse();
        response.setDraftId("draft-1");
        response.setCreateTime(LocalDateTime.of(2026, 7, 25, 12, 30));

        String json = new JacksonConfig().objectMapper().writeValueAsString(response);

        assertThat(json).contains("\"draftId\":\"draft-1\"", "\"createTime\":");
    }
}
