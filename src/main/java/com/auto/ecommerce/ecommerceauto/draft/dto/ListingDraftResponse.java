package com.auto.ecommerce.ecommerceauto.draft.dto;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ListingDraftResponse {
    private String draftId;
    private String status;
    private ListingDraft draft;
    private String lastErrorType;
    private String lastErrorMessage;
    private String publishScreenshotPath;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
