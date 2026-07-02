package com.auto.ecommerce.ecommerceauto.playwright;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表单填充请求参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormFillRequest {

    /** 目标页面 URL */
    private String url;

    /** 表单字段列表 */
    private List<FieldFill> fields;

    /** 文件上传列表 */
    private List<FileUpload> files;

    /** 提交按钮的 CSS 选择器（默认 button[type="submit"]） */
    private String submitSelector = "button[type='submit']";

    /** 提交后等待的 URL 片段（用于确认提交成功） */
    private String expectUrlContains;

    /** 提交后等待的选择器（用于确认提交成功） */
    private String expectSelector;


    // —— 内嵌类 ——

    /**
     * 单个表单字段
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldFill {
        /** 定位方式: css / label / text / role */
        private String by = "css";

        /** 定位表达式，例如 "#name"（css）、"姓名"（label）、"提交"（role name） */
        private String selector;

        /** 要填入的值（select 时填 option 的 label） */
        private String value;

        /** 操作类型: fill / select / check / uncheck / click */
        private String action = "fill";
    }

    /**
     * 文件上传项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileUpload {
        /** 文件 input 的 CSS 选择器 */
        private String selector;

        /** 文件路径（绝对路径或相对于工作目录的路径） */
        private String filePath;
    }
}
