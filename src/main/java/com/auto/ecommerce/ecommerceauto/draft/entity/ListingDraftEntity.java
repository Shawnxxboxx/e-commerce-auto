package com.auto.ecommerce.ecommerceauto.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("listing_draft")
public class ListingDraftEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String draftId;
    private String templateId;
    private String templateName;
    private String titlePromptSnapshot;
    private String mainImagePromptSnapshot;
    private String materialPackageId;
    private String materialPackagePath;
    private String status;
    private String draftJson;
    private String publishRequestJson;
    private String lastErrorType;
    private String lastErrorMessage;
    private String publishScreenshotPath;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
