package com.auto.ecommerce.ecommerceauto.playwright;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 表单填充结果
 */
@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FormFillResult {

    private boolean success;
    private String message;
    private String finalUrl;
    private long elapsedMs;
    private String screenshotPath;

    public static FormFillResult success(String finalUrl, long elapsedMs) {
        return FormFillResult.builder()
                .success(true)
                .message("表单提交成功")
                .finalUrl(finalUrl)
                .elapsedMs(elapsedMs)
                .build();
    }

    public static FormFillResult failure(String message, long elapsedMs, String screenshotPath) {
        return FormFillResult.builder()
                .success(false)
                .message(message)
                .elapsedMs(elapsedMs)
                .screenshotPath(screenshotPath)
                .build();
    }
}
