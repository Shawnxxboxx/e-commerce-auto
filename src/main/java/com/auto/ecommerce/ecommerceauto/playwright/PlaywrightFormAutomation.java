package com.auto.ecommerce.ecommerceauto.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Paths;
import java.util.List;

/**
 * Playwright 表单自动化服务。
 * <p>
 * 通过 CDP 连接到用户当前正在使用的 Chrome 浏览器，复用其 Cookie/登录态，
 * 然后执行表单填写、文件上传、提交等操作。
 * <p>
 * <b>前置条件：</b>先以调试模式启动 Chrome：
 * <pre>
 *   # Mac
 *   /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222
 *   # Windows
 *   "C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222
 *   # Linux
 *   google-chrome --remote-debugging-port=9222
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "playwright.enabled", havingValue = "true", matchIfMissing = true)
public class PlaywrightFormAutomation {

    private final PlaywrightProperties properties;
    private final MabangPublisher mabangPublisher;

    private Playwright playwright;
    private Browser browser;

    @PostConstruct
    public void init() {
        log.info("正在启动 Playwright 并连接到 Chrome CDP: {}", properties.getCdpUrl());
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().connectOverCDP(properties.getCdpUrl());
            log.info("成功连接到 Chrome (版本: {})", browser.version());

            // 将浏览器实例共享给 MabangPublisher
            mabangPublisher.initWithBrowser(browser);
            log.info("MabangPublisher 已关联到同一浏览器实例");
        } catch (Exception e) {
            log.error("连接 Chrome 失败，请确保已启动: {} --remote-debugging-port={}",
                    "Chrome", properties.getPort(), e);
            throw new IllegalStateException(
                    "无法连接到 Chrome CDP。请先以 --remote-debugging-port="
                            + properties.getPort() + " 启动 Chrome", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (browser != null) {
            browser.close();
            log.info("Chrome 连接已关闭");
        }
        if (playwright != null) {
            playwright.close();
            log.info("Playwright 已关闭");
        }
    }

    /**
     * 检查 CDP 连接是否正常
     */
    public boolean isConnected() {
        return browser != null && browser.contexts().size() > 0;
    }

    /**
     * 执行完整的表单填写流程：打开页面 → 填字段 → 传文件 → 提交 → 等待结果
     */
    public FormFillResult fillForm(FormFillRequest request) {
        long startTime = System.currentTimeMillis();

        // 使用已有浏览器上下文（复用 Chrome 现有 Cookie/登录态）
        BrowserContext context = browser.contexts().get(0);
        Page page = context.newPage();
        page.setViewportSize(1920, 1080);

        try {
            // 配置隐式等待超时
            page.setDefaultTimeout(properties.getTimeoutMs());

            // 1. 导航到目标页面
            log.info("导航到: {}", request.getUrl());
            page.navigate(request.getUrl());

            // 2. 填写字段
            if (request.getFields() != null && !request.getFields().isEmpty()) {
                fillFields(page, request.getFields());
            }

            // 3. 上传文件
            if (request.getFiles() != null && !request.getFiles().isEmpty()) {
                uploadFiles(page, request.getFiles());
            }

            // 4. 提交
            log.info("点击提交按钮: {}", request.getSubmitSelector());
            page.click(request.getSubmitSelector());

            // 5. 等待提交结果
            if (request.getExpectUrlContains() != null && !request.getExpectUrlContains().isEmpty()) {
                page.waitForURL("**" + request.getExpectUrlContains() + "**");
                log.info("页面已跳转至: {}", page.url());
            }
            if (request.getExpectSelector() != null && !request.getExpectSelector().isEmpty()) {
                page.waitForSelector(request.getExpectSelector());
                log.info("目标元素已出现: {}", request.getExpectSelector());
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("表单提交成功，耗时 {}ms", elapsed);

            return FormFillResult.success(page.url(), elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("表单处理失败 (耗时 {}ms)", elapsed, e);

            // 失败时截图辅助排查
            String screenshotPath = takeScreenshot(page, "error-" + System.currentTimeMillis());
            return FormFillResult.failure(e.getMessage(), elapsed, screenshotPath);

        } finally {
            // 只关闭当前页面，不关闭 context（避免关闭已有标签页）
            page.close();
        }
    }

    /**
     * 仅打开页面并截图（用于调试定位选择器）
     */
    public String capturePage(String url, String selector) {
        BrowserContext context = browser.contexts().get(0);
        Page page = context.newPage();
        page.setViewportSize(1920, 1080);
        try {
            page.setDefaultTimeout(properties.getTimeoutMs());
            page.navigate(url);

            if (selector != null && !selector.isEmpty()) {
                page.waitForSelector(selector);
            }

            String path = takeScreenshot(page, "capture-" + System.currentTimeMillis());
            log.info("页面截图已保存: {}", path);
            return path;
        } finally {
            page.close();
        }
    }

    // ========== 内部方法 ==========

    private void fillFields(Page page, List<FormFillRequest.FieldFill> fields) {
        for (FormFillRequest.FieldFill field : fields) {
            Locator locator = resolveLocator(page, field);

            switch (field.getAction()) {
                case "fill":
                    locator.fill(field.getValue());
                    log.debug("填充字段 [{}] <- {}", field.getSelector(), field.getValue());
                    break;
                case "select":
                    locator.selectOption(field.getValue());
                    log.debug("选择下拉框 [{}] <- {}", field.getSelector(), field.getValue());
                    break;
                case "check":
                    locator.check();
                    log.debug("勾选 [{}]", field.getSelector());
                    break;
                case "uncheck":
                    locator.uncheck();
                    log.debug("取消勾选 [{}]", field.getSelector());
                    break;
                case "click":
                    locator.click();
                    log.debug("点击 [{}]", field.getSelector());
                    break;
                default:
                    log.warn("未知的 action: {}", field.getAction());
            }
        }
    }

    private void uploadFiles(Page page, List<FormFillRequest.FileUpload> files) {
        for (FormFillRequest.FileUpload file : files) {
            page.locator(file.getSelector()).setInputFiles(Paths.get(file.getFilePath()));
            log.debug("上传文件 [{}] <- {}", file.getSelector(), file.getFilePath());
        }
    }

    /**
     * 根据 by 类型解析定位器
     */
    private Locator resolveLocator(Page page, FormFillRequest.FieldFill field) {
        return switch (field.getBy()) {
            case "label" -> page.getByLabel(field.getSelector());
            case "text"  -> page.getByText(field.getSelector());
            case "role"  -> page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(field.getSelector()));
            default      -> page.locator(field.getSelector());  // css
        };
    }

    private String takeScreenshot(Page page, String name) {
        String path = "screenshots/" + name + ".png";
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get(path))
                .setFullPage(true));
        return path;
    }
}
