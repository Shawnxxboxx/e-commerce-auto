package com.auto.ecommerce.ecommerceauto.playwright;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * TikTok 全托管刊登 — 表单请求参数。
 * <p>
 * 对应马帮 ERP 刊登页面的各模块字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TikTokPublishRequest {

    /** 目标页面 URL（不填则使用默认马帮刊登页） */
    private String url;

    // ========== 1. 店铺及类目 ==========

    /** 店铺名称（需与下拉框选项文本一致） */
    private String shopName;

    /** 产品类目名称（选择类目前提：已选店铺） */
    private String categoryName;

    /**
     * 分类属性（类目选中后动态生成的属性表单，如原产地、材质、化学物质等）。
     * <p>
     * key = 属性名（对应页面 label 文本，用于拼 placeholder "请选择{属性名}"，如 "原产地"）；
     * value = 选项完整文本（对应下拉项 &lt;span&gt; 文本，需含括号英文，如 "中国大陆(Mainland China)"）。
     * <p>
     * 示例：
     * <pre>
     * {
     *   "原产地": "中国大陆(Mainland China)",
     *   "是否含有化学物质": "否(No)",
     *   "材质": "PU革(PU Leather)",
     *   "季节": "夏(Summer)"
     * }
     * </pre>
     * 注意：不同类目下的属性集不同，请按所选类目实际出现的属性填写。
     */
    private Map<String, String> categoryAttributes;

    // ========== 2. 基本信息 ==========

    /** 来源URL */
    private String sourceUrl;

    /** 中文标题（必填） */
    private String chineseTitle;

    /** 英文标题 */
    private String englishTitle;

    /** 品牌名称 */
    @Builder.Default
    private String brand = "无品牌";

    // ========== 3. 商品素材 ==========

    /** 传图模式：SpuWithSkc=SPU轮播图+SKC预览, SpuWithSku=SPU轮播图+SKU预览 */
    private String picSetType;

    /** 产品首图文件路径 */
    private String productMainImage;

    /** 尺寸图文件路径 */
    private String productSizeChartImage;

    /** 细节图文件路径列表 */
    private List<String> productDetailImages;

    /** 产品视频文件路径 */
    private String videoFilePath;

    /** 描述图文件路径列表 */
    private List<String> descriptionImagePaths;

    // ========== 4. 变种信息 ==========

    /**
     * 变种属性组：属性组名 → 选中的值列表。
     * <p>
     * 示例：
     * <pre>
     * {
     *   "颜色": ["黑色", "白色"],
     *   "尺码": ["S", "M", "L"]
     * }
     * </pre>
     * 系统会根据这些值的笛卡尔积自动在表格中生成组合行。
     */
    private Map<String, List<String>> variantAttributes;

    /**
     * 预览图文件路径列表，按表格生成的行顺序一一对应。
     * 例如属性组 颜色=[黑,白] &times; 尺码=[S,M] 生成 4 行：
     * 黑/S, 黑/M, 白/S, 白/M → 此列表也应有 4 个路径。
     */
    private List<String> variantPreviewImages;

    /** SKU 变种列表（旧版，用于简单 input 表单；新版优先使用 variantAttributes） */
    private List<VariantSku> variantSkus;

    /**
     * 交易信息行列表（变种属性选择完成后弹出的表格）。
     * <p>
     * 按颜色+尺码一一对应表格中的行/merge-div 组合。
     */
    private List<TransactionRow> transactionInfo;

    // ========== 5. 资质合规 ==========

    /** 制造商名称 */
    private String manufacturer;

    /** 欧盟责任人名称 */
    private String euResponsiblePerson;

    // ========== 6. 提交动作 ==========

    /** true=保存并刊登，false=仅保存 */
    @Builder.Default
    private boolean publish = false;


    // ========== 内嵌类 ==========

    /**
     * SKU 变种
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantSku {
        /** 变种属性名，例如 "颜色"、"尺寸" */
        private String attributeName;

        /** 变种属性值，例如 "红色"、"XL" */
        private String attributeValue;

        /** SKU 编码 */
        private String sku;

        /** 价格 */
        private Double price;

        /** 库存 */
        private Integer stock;

        /** 商品条码 */
        private String barcode;

        /** 重量（g） */
        private Double weight;
    }

    /**
     * 交易信息行 — 对应变种弹出表格中的一行（颜色 × 尺码组合）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionRow {
        /** 颜色名称（用于匹配表格行） */
        private String color;

        /** 尺码名称（用于匹配 merge-div） */
        private String size;

        /** 商品货号 SKC（每颜色组一个值） */
        private String skc;

        /** 备货模式：JIT备货 / 普通备货 */
        private String stockingMode;

        /** SKU 货号 */
        private String sku;

        /** 不含税价（CNY） */
        private Double price;

        /** 库存 */
        private Integer stock;

        /** 长（cm） */
        private Double length;

        /** 宽（cm） */
        private Double width;

        /** 高（cm） */
        private Double height;

        /** 重量（g） */
        private Double weight;

        /** 商品状态，默认 true（启用） */
        @Builder.Default
        private Boolean enabled = true;
    }
}
