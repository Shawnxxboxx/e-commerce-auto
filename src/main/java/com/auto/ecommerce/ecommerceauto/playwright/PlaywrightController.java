package com.auto.ecommerce.ecommerceauto.playwright;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * Playwright 表单自动化控制器。
 * <p>
 * 通过 REST API 触发浏览器自动化操作。
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/playwright")
@ConditionalOnProperty(name = "playwright.enabled", havingValue = "true", matchIfMissing = true)
public class PlaywrightController {

    private final PlaywrightFormAutomation formAutomation;
    private final MabangPublisher mabangPublisher;

    /**
     * 健康检查 — 查看 CDP 连接是否正常
     */
    @GetMapping("/status")
    public String status() {
        boolean connected = formAutomation.isConnected();
        return "Playwright CDP 连接状态: " + (connected ? "正常 ✅" : "异常 ❌");
    }

    /**
     * 填写并提交通用表单
     * <p>
     * 请求体示例:
     * <pre>
     * {
     *   "url": "https://example.com/form",
     *   "fields": [
     *     { "by": "css",  "selector": "#name",     "value": "张三" },
     *     { "by": "label","selector": "城市",      "value": "上海市", "action": "select" },
     *     { "by": "css",  "selector": "#agree",    "action": "check" }
     *   ],
     *   "files": [
     *     { "selector": "input[name='avatar']", "filePath": "/Users/xxx/photo.jpg" }
     *   ],
     *   "submitSelector": "button.submit",
     *   "expectUrlContains": "/success"
     * }
     * </pre>
     */
    @PostMapping("/fill-form")
    public FormFillResult fillForm(@RequestBody FormFillRequest request) {
        log.info("收到通用表单填充请求: url={}, fields={}, files={}",
                request.getUrl(),
                request.getFields() != null ? request.getFields().size() : 0,
                request.getFiles() != null ? request.getFiles().size() : 0);
        return formAutomation.fillForm(request);
    }

    /**
     * 截图调试 — 打开页面并截图，用于辅助定位选择器
     */
    @PostMapping("/capture")
    public String capture(
            @RequestParam String url,
            @RequestParam(required = false, defaultValue = "") String selector) {
        return formAutomation.capturePage(url, selector);
    }

    // ========== TikTok 全托管刊登 ==========

    /**
     * TikTok 全托管 — 填写并刊登
     * <p>
     * 请求体示例:
     * <pre>
     * {
     *   "shopName": "测试店铺",
     *   "categoryName": "女装/连衣裙",
     *   "chineseTitle": "夏季新款连衣裙",
     *   "englishTitle": "Summer New Dress",
     *   "brand": "无品牌",
     *   "picSetType": "SpuWithSkc",
     *   "productMainImage": "/Users/xxx/main.jpg",
     *   "productSizeChartImage": "/Users/xxx/size.jpg",
     *   "productDetailImages": ["/Users/xxx/detail1.jpg", "/Users/xxx/detail2.jpg"],
     *   "descriptionImagePaths": ["/Users/xxx/img1.jpg", "/Users/xxx/img2.jpg"],
     *   "manufacturer": "某制造商",
     *   "publish": false
     * }
     * </pre>
     */
    @PostMapping("/tiktok/publish")
    public MabangPublisher.PublishResult tiktokPublish(@RequestBody TikTokPublishRequest request) {
        log.info("收到 TikTok 刊登请求: shop={}, title={}, productImages={}, descriptionImages={}",
                request.getShopName(),
                request.getChineseTitle(),
                request.getDescriptionImagePaths() != null ? request.getDescriptionImagePaths().size() : 0);
        return mabangPublisher.publish(request);
    }

    /**
     * 🩺 诊断店铺下拉框 — 点击后输出 DOM 结构，用于确定正确选择器
     */
    @GetMapping("/tiktok/diagnose-shop")
    public String diagnoseShop() {
        log.info("收到诊断店铺下拉框请求");
        return mabangPublisher.diagnoseShopDropdown();
    }

    /**
     * TikTok 全托管 — 仅预览/截图页面（不提交）
     */
    @PostMapping("/tiktok/preview")
    public String tiktokPreview(@RequestParam(required = false, defaultValue = "") String url) {
        String targetUrl = url != null && !url.isEmpty() ? url :
                "https://www.mabangerp.com/index.php?mod=main.goPublish" +
                        "&url=aHR0cHM6Ly9wdWJsaXNoLm1hYmFuZ2VycC5jb20vcHVibGlzaC11" +
                        "aS8jL3Rpa3Rva0Z1bGxTZXJ2aWNlRGV0YWlsP2NLZXk9TUFCQU5HX0VSU" +
                        "F9QUklWQVRFX0xPR0lOXzQxNDU3Ml83MzU2MzFfUFVCTElTSA==";
        return formAutomation.capturePage(targetUrl, null);
    }
}
