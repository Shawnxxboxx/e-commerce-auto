package com.auto.ecommerce.ecommerceauto.playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 马帮 ERP — TikTok 全托管刊登自动化服务。
 * <p>
 * 处理 iframe 切换、表单填充、文件上传等操作。
 * 页面结构：外层框架 → iframe#iframeContent → Vue SPA 表单
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MabangPublisher {

    private final PlaywrightProperties properties;

    private Playwright playwright;
    private Browser browser;

    /** 是否拥有浏览器所有权（共享模式不负责关闭） */
    private boolean ownsBrowser = false;

    /** iframe 选择器 */
    private static final String IFRAME_SELECTOR = "#iframeContent";

    /** TikTok Shop 刊登列表入口 */
    private static final String DEFAULT_PUBLISH_ENTRY_URL =
            "https://www.mabangerp.com/index.php?mod=main.gotoApp&v=v3&menuKey=M001089961&platform=tiktokshop&version=1";

    // ========== 生命周期 ==========

    /**
     * 手动初始化（与全局 Playwright 共用浏览器实例时调用）
     */
    public void initWithBrowser(Browser browser) {
        this.playwright = null;
        this.browser = browser;
        this.ownsBrowser = false;
        log.info("MabangPublisher 已附加到已有浏览器实例");
    }

    /**
     * 独立初始化（单独启动浏览器）
     */
    public void initStandalone() {
        if (browser != null) return;
        log.info("正在启动 Playwright 独立实例...");
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setChannel("chrome");
        browser = playwright.chromium().launch(options);
        this.ownsBrowser = true;
        log.info("Playwright 浏览器已启动");
    }

    /**
     * 连接已开启远程调试端口的 Chrome，复用用户登录态。
     */
    public synchronized void initWithCdp() {
        if (browser != null) return;
        log.info("正在连接 Chrome CDP: {}", properties.getCdpUrl());
        playwright = Playwright.create();
        browser = playwright.chromium().connectOverCDP(properties.getCdpUrl());
        ownsBrowser = true;
        log.info("已连接 Chrome CDP (版本: {})", browser.version());
    }

    /**
     * 释放资源（仅 ownsBrowser=true 时关闭浏览器）
     */
    @PreDestroy
    public void destroy() {
        if (ownsBrowser) {
            if (browser != null) {
                browser.close();
                log.info("浏览器已关闭");
            }
            if (playwright != null) {
                playwright.close();
                log.info("Playwright 已关闭");
            }
        } else {
            log.info("共享模式，跳过浏览器关闭");
        }
    }

    // ========== 刊登流程 ==========

    /**
     * 执行 TikTok 全托管刊登
     *
     * @param request 刊登参数
     * @return 刊登结果
     */
    public PublishResult publish(TikTokPublishRequest request) {
        long startTime = System.currentTimeMillis();

        if (browser == null) {
            try {
                initWithCdp();
            } catch (Exception e) {
                log.error("连接 Chrome CDP 失败", e);
                return PublishResult.failure("浏览器初始化失败，请确认 Chrome 已用 --remote-debugging-port="
                        + properties.getPort() + " 启动: " + e.getMessage(), 0, null);
            }
        }

        try {
            BrowserContext context = browser.contexts().isEmpty()
                    ? browser.newContext()
                    : browser.contexts().get(0);

            // 在已有标签页中查找马帮页面
            Page page = context.newPage();
            FormFrame form = openPublishForm(context, page, request);
            page = form.page();
            FrameLocator formFrame = form.frame();

            // ==================== 1. 选择店铺 ====================
            if (request.getShopName() != null) {
                selectShop(formFrame, request.getShopName());
            }

            // ==================== 2. 选择产品类目 ====================
            if (request.getCategoryName() != null) {
                selectCategory(formFrame, request.getCategoryName());
            }

            // ==================== 3. 基本信息填写 ====================
            // Element UI 结构：每个字段是 .el-form-item，label 文本在 .el-form-item__label，
            // input 本身无 id（只有 label 上挂着 for）。故用 :has() 按 label 文本锁定对应 form-item 的 input。
            if (request.getSourceUrl() != null) {
                formFrame.locator(".el-form-item:has(.el-form-item__label:has-text('来源URL')) input.el-input__inner")
                        .fill(request.getSourceUrl());
                pageWait(300);
            }

            if (request.getChineseTitle() != null) {
                formFrame.locator(".el-form-item:has(.el-form-item__label:has-text('中文标题')) input.el-input__inner")
                        .fill(request.getChineseTitle());
                pageWait(300);
            }

            if (request.getEnglishTitle() != null) {
                // 英文标题没有 <label>，但有独特的" 英语 "prepend 标签，用它锁定。
                formFrame.locator(".el-input-group--prepend")
                        .filter(new Locator.FilterOptions().setHasText("英语"))
                        .locator("input.el-input__inner")
                        .fill(request.getEnglishTitle());
                pageWait(300);
            }

            // ==================== 4 分类属性（原产地/材质/化学物质等）====================
            // 这些字段在选择类目后动态生成，依赖类目已选中。值取自 request.getCategoryAttributes()。
            if (request.getCategoryAttributes() != null && !request.getCategoryAttributes().isEmpty()) {
                fillCategoryAttributes(formFrame, request.getCategoryAttributes());
            }

            // ==================== 5. 商品素材上传 ====================
            // 5a. 选择传图模式
            if (request.getPicSetType() != null) {
                selectPicSetType(formFrame, request.getPicSetType());
            }

            // 5b. 上传产品图（首图、细节图）
            if (request.getProductMainImage() != null
                    || (request.getProductDetailImages() != null && !request.getProductDetailImages().isEmpty())) {
                uploadProductImages(formFrame, request);
            }

            // 5c. 上传描述图（必填，失败中止）
            if (request.getDescriptionImagePaths() != null && !request.getDescriptionImagePaths().isEmpty()) {
                uploadDescriptionImages(formFrame, request.getDescriptionImagePaths());
            }

            // ==================== 7. 变种信息 ====================
            if ((request.getVariantAttributes() != null && !request.getVariantAttributes().isEmpty())
                    || (request.getVariantSkus() != null && !request.getVariantSkus().isEmpty())) {
                fillVariantSkus(formFrame, request);
            }

            // ==================== 7b. 交易信息（变种属性选择后弹出的表格）====================
            if (request.getTransactionInfo() != null && !request.getTransactionInfo().isEmpty()) {
                try {
                    fillTransactionInfo(formFrame, request.getTransactionInfo());
                } catch (Exception e) {
                    log.warn("交易信息填写失败，跳过: {}", e.getMessage());
                }
            }

            // ==================== 8. 资质合规与产品包装图 ====================
            log.info("第8步资质合规数据: manufacturer={}, euResponsiblePerson={}, packageImages={}",
                    request.getManufacturer(), request.getEuResponsiblePerson(),
                    request.getPackageImagePaths() == null ? 0 : request.getPackageImagePaths().size());
            if (request.getPackageImagePaths() != null && !request.getPackageImagePaths().isEmpty()) {
                uploadPackageImages(formFrame, request.getPackageImagePaths());
            }

            if (request.getManufacturer() != null && !request.getManufacturer().isBlank()) {
                selectManufacturer(formFrame, request.getManufacturer());
            }

            if (request.getEuResponsiblePerson() != null && !request.getEuResponsiblePerson().isBlank()) {
                selectEuResponsiblePerson(formFrame, request.getEuResponsiblePerson());
            }

            // ==================== 8. 保存/刊登 ====================
            log.info("表单填写完毕，准备提交...");
            if (request.isPublish()) {
                formFrame.locator("footer button:has-text('保存并刊登')").click();
            } else {
                formFrame.locator("footer button:has-text('保 存')").click();
            }

            pageWait(3000);
            // 关闭成功弹窗会连同当前刊登 Page 一起关闭，必须先保存截图和 URL。
            String screenshotPath = "screenshots/publish-" + System.currentTimeMillis() + ".png";
            String resultUrl = page.url();
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(screenshotPath)).setFullPage(true));
            closeSaveSuccessDialog(page, formFrame);
            pageWait(1000);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("刊登完成，耗时 {}ms", elapsed);
            return PublishResult.success("刊登成功", resultUrl, elapsed, screenshotPath);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("刊登失败 (耗时 {}ms)", elapsed, e);
            return PublishResult.failure("刊登失败: " + e.getMessage(), elapsed, null);
        }
    }

    /**
     * 保存成功后马帮会弹出“刊登信息保存成功”提示，必须关闭弹窗才能结束本次刊登页面。
     * 弹窗通常在表单 iframe 中，兼容部分页面版本把弹窗渲染到外层页面的情况。
     */
    private void closeSaveSuccessDialog(Page page, FrameLocator formFrame) {
        Locator closeButton = formFrame.getByRole(AriaRole.BUTTON,
                new FrameLocator.GetByRoleOptions().setName("关闭此页面").setExact(true));
        if (closeButton.count() > 0 && closeButton.first().isVisible()) {
            closeButton.first().click();
            log.info("已关闭刊登成功提示页面");
            return;
        }

        Locator outerCloseButton = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("关闭此页面").setExact(true));
        if (outerCloseButton.count() > 0 && outerCloseButton.first().isVisible()) {
            outerCloseButton.first().click();
            log.info("已关闭刊登成功提示页面");
            return;
        }

        throw new RuntimeException("保存后未找到“关闭此页面”按钮，无法确认刊登页面已关闭");
    }

    /**
     * 从刊登列表入口点击"新增刊登"，进入具体表单页。
     */
    private FormFrame openPublishForm(BrowserContext context, Page page, TikTokPublishRequest request) {
        String entryUrl = request.getUrl() != null && !request.getUrl().isBlank()
                ? request.getUrl()
                : DEFAULT_PUBLISH_ENTRY_URL;
        log.info("打开马帮 TikTok 刊登入口页: {}", entryUrl);
        page.setDefaultTimeout(properties.getTimeoutMs());
        navigateWithRetry(page, entryUrl);
        pageWait(3000);

        Page clickedPage = clickAddListing(page);
        pageWait(2000);
        return waitForFormFrame(context, clickedPage);
    }

    private Page clickAddListing(Page page) {
        Exception last = null;
        List<Locator> candidates = List.of(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("新增刊登")),
                page.getByText("新增刊登"),
                page.frameLocator(IFRAME_SELECTOR).getByRole(AriaRole.BUTTON,
                        new FrameLocator.GetByRoleOptions().setName("新增刊登")),
                page.frameLocator(IFRAME_SELECTOR).getByText("新增刊登")
        );
        for (Locator candidate : candidates) {
            try {
                Locator target = candidate.first();
                target.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                try {
                    Page popup = page.waitForPopup(
                            new Page.WaitForPopupOptions().setTimeout(5000),
                            target::click);
                    log.info("已点击新增刊登，新标签页: {}", popup.url());
                    return popup;
                } catch (TimeoutError e) {
                    log.info("已点击新增刊登，当前页继续加载");
                    return page;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        throw new RuntimeException("未找到新增刊登按钮" + (last != null ? ": " + last.getMessage() : ""));
    }

    private FormFrame waitForFormFrame(BrowserContext context, Page preferredPage) {
        long deadline = System.currentTimeMillis() + properties.getTimeoutMs();
        List<String> frameSelectors = List.of("iframe[src*='publish']", "iframe#iframeContent");
        Exception last = null;
        log.info("正在等待新增刊登表单加载...");
        while (System.currentTimeMillis() < deadline) {
            for (Page candidatePage : candidatePages(context, preferredPage)) {
                for (String selector : frameSelectors) {
                    try {
                        FrameLocator frame = candidatePage.frameLocator(selector);
                        frame.locator("#box1").waitFor(new Locator.WaitForOptions().setTimeout(1000));
                        log.info("新增刊登表单已加载: page={}, iframe={}", candidatePage.url(), selector);
                        return new FormFrame(candidatePage, frame);
                    } catch (Exception e) {
                        last = e;
                    }
                }
            }
            pageWait(1000);
        }
        log.warn("未找到表单 iframe。当前页面/iframe: {}", describePages(context));
        throw new RuntimeException("新增刊登表单未加载" + (last != null ? ": " + last.getMessage() : ""));
    }

    private List<Page> candidatePages(BrowserContext context, Page preferredPage) {
        List<Page> pages = new ArrayList<>();
        if (preferredPage != null && !preferredPage.isClosed()) {
            pages.add(preferredPage);
        }
        List<Page> all = context.pages();
        for (int i = all.size() - 1; i >= 0; i--) {
            Page page = all.get(i);
            if (!page.isClosed() && page.url().contains("mabangerp.com") && !pages.contains(page)) {
                pages.add(page);
            }
        }
        return pages;
    }

    private String describePages(BrowserContext context) {
        StringBuilder report = new StringBuilder();
        for (Page page : context.pages()) {
            if (page.isClosed()) {
                continue;
            }
            report.append("page=").append(page.url()).append(" frames=[");
            for (Frame frame : page.frames()) {
                report.append(frame.url()).append(", ");
            }
            report.append("]; ");
        }
        return report.toString();
    }

    private record FormFrame(Page page, FrameLocator frame) { }

    // ========== 🩺 诊断 ==========

    /**
     * 诊断店铺下拉框结构：点击输入框后截取页面 DOM 信息，用于确定正确选择器。
     */
    public String diagnoseShopDropdown() {
        Page page = null;
        try {
            if (browser == null) {
                initStandalone();
            }
            BrowserContext context = browser.contexts().isEmpty()
                    ? browser.newContext()
                    : browser.contexts().get(0);
            page = context.newPage();
            page.setViewportSize(1920, 1080);
            page.setDefaultTimeout(properties.getTimeoutMs());

            String url = "https://www.mabangerp.com/index.php?mod=main.goPublish" +
                    "&url=aHR0cHM6Ly9wdWJsaXNoLm1hYmFuZ2VycC5jb20vcHVibGlzaC11" +
                    "aS8jL3Rpa3Rva0Z1bGxTZXJ2aWNlRGV0YWlsP2NLZXk9TUFCQU5HX0VSU" +
                    "F9QUklWQVRFX0xPR0lOXzQxNDU3Ml83MzU2MzFfUFVCTElTSA==";
            page.navigate(url);
            page.waitForSelector(IFRAME_SELECTOR);
            page.waitForTimeout(3000);

            FrameLocator frame = page.frameLocator(IFRAME_SELECTOR);
            StringBuilder report = new StringBuilder();

            // IFrame 内所有可见文本
            report.append("=== IFRAME BODY TEXT ===\n");
            frame.locator("body").allInnerTexts().forEach(t -> report.append(t).append("\n"));

            // 点击店铺输入框
            Locator shopInput = frame.locator("input[placeholder='请选择店铺']");
            if (shopInput.isVisible()) {
                shopInput.click();
                page.waitForTimeout(2000);
            }

            // 截图
            String ssPath = "screenshots/diag-shop-" + System.currentTimeMillis() + ".png";
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(ssPath)).setFullPage(true));
            report.append("\n=== 截图 ===").append(ssPath).append("\n");

            // 页面 body 文本（下拉可能渲染在根节点）
            report.append("\n=== PAGE BODY TEXT ===\n");
            page.locator("body").allInnerTexts().forEach(t -> report.append(t.length() > 2000 ? t.substring(0, 2000) + "..." : t).append("\n"));

            // role=option / 下拉容器 元素
            report.append("\n=== DROPDOWN-RELATED ELEMENTS ===\n");
            page.locator("[role='option'], [role='listbox'], " +
                    ".el-select-dropdown, .ant-select-dropdown, " +
                    ".el-select__popper, .ant-select-popup, " +
                    ".el-popper, .el-scrollbar, " +
                    ".select-dropdown, .dropdown-container").all().forEach(el -> {
                try {
                    String tag = el.evaluate("e => e.tagName").toString();
                    String text = el.innerText().replace("\n", " ").substring(0, Math.min(200, el.innerText().length()));
                    boolean vis = el.isVisible();
                    String clazz = el.getAttribute("class") != null ? el.getAttribute("class") : "";
                    report.append(String.format("  tag=%-8s visible=%-5s class=%s  text='%s'\n", tag, vis, clazz, text));
                } catch (Exception ex) {
                    report.append("  error: ").append(ex.getMessage()).append("\n");
                }
            });

            // iframe 内的 option-like 元素
            report.append("\n=== IFRAME DROPDOWN ELEMENTS ===\n");
            frame.locator("[role='option'], .el-select-dropdown__item, .ant-select-item, " +
                    "li, .el-select-dropdown").all().forEach(el -> {
                try {
                    String text = el.innerText().replace("\n", " ").substring(0, Math.min(200, el.innerText().length()));
                    if (!text.isBlank()) {
                        boolean vis = el.isVisible();
                        report.append(String.format("  visible=%-5s text='%s'\n", vis, text));
                    }
                } catch (Exception ignored) { }
            });

            log.info("🩺 诊断报告:\n{}", report);
            return report.toString();

        } catch (Exception e) {
            log.error("诊断失败", e);
            return "诊断失败: " + e.getMessage();
        } finally {
            if (page != null) page.close();
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 选择店铺（必填：失败抛异常，由 publish 中止流程）
     */
    private void selectShop(FrameLocator frame, String shopName) {
        log.debug("选择店铺: {}", shopName);
        selectElOption(frame, "请选择店铺", shopName);
        pageWait(500);
    }

    /**
     * 选择产品类目（必填：失败抛异常，由 publish 中止流程）。
     * <p>
     * 类目弹窗结构（CDP 实测）：点"选择类目"按钮 → 弹出类目树，节点为
     * label.el-transfer-panel-item，文本形如 "女装(Womenswear)"，有子级时带
     * &lt;i class="fa fa-angle-right has-children"&gt; 箭头。点击箭头会"选中该级 + 展开子级"。
     * 故按类目路径（/ 或 &gt; 分隔）逐级点箭头下钻；某级匹配不到则停在上一级并告警。
     * 注意：该树无"连衣裙"等品类，只有 女装/标码女装/大码女装 这类官方类目节点。
     */
    private void selectCategory(FrameLocator frame, String categoryName) {
        log.debug("选择产品类目: {}", categoryName);
        // 1. 点"选择类目"按钮打开弹窗
        Locator selectBtn = frame.getByRole(AriaRole.BUTTON,
                new FrameLocator.GetByRoleOptions().setName("选择类目"));
        if (!selectBtn.isVisible()) {
            throw new RuntimeException("类目「选择类目」按钮不可见，可能已选过类目: " + categoryName);
        }
        selectBtn.click();
        pageWait(1000);

        // 2. 逐级下钻：在当前可见节点中匹配本级文本，点箭头(选中+展开)或叶子节点
        String[] levels = categoryName.split("[>/]");
        String selected = null;
        for (String lvl : levels) {
            String level = lvl.trim();
            if (level.isEmpty()) continue;
            Locator item = frame.locator("label.el-transfer-panel-item:visible")
                    .filter(new Locator.FilterOptions().setHasText(level))
                    .first();
            try {
                item.waitFor(new Locator.WaitForOptions().setTimeout(4000));
                Locator arrow = item.locator("i.has-children");
                if (arrow.isVisible()) {
                    // 马帮的展开事件绑定在整行 label 上，点击箭头本身不会触发展开。
                    item.click(new Locator.ClickOptions().setForce(true)); // 选中本级并展开子级
                } else {
                    item.click(new Locator.ClickOptions().setForce(true));  // 叶子节点直接选中
                }
                selected = level;
                log.debug("已选类目层级: {}", level);
                pageWait(800);
            } catch (Exception e) {
                // 诊断：dump 当前弹窗内"可见"的类目项文本，定位是弹窗没打开、还是该级确实无此节点。
                List<String> visibleItems;
                try {
                    visibleItems = frame.locator("label.el-transfer-panel-item:visible").allInnerTexts();
                } catch (Exception ignored) {
                    visibleItems = List.of();
                }
                log.warn("类目层级 '{}' 未找到，停留在 '{}'（完整路径: {}）。当前弹窗可见类目项: {}。原因: {}",
                        level, selected, categoryName, visibleItems, e.getMessage());
                throw new RuntimeException("类目层级未找到: " + level + "（完整路径: " + categoryName + "）", e);
            }
        }
        if (selected == null) {
            throw new RuntimeException("未匹配到任何类目层级: " + categoryName
                    + "（请确认类目名为官方类目，如 女装/标码女装）");
        }

        // 3. 点"确定"——类目弹窗 footer 内的 warning 按钮。
        // 按钮文本是"确 定"（中间带空格），getByRole.setName 默认做子串匹配，"确定" 无法命中"确 定"，
        // 故改用按钮 class 定位：确定=el-button--warning(橙)，取消=el-button--default。
        Locator dialog = frame.locator("div.el-dialog:has(label.el-transfer-panel-item)");
        Locator confirmBtn = dialog.locator(".el-dialog__footer button.el-button--warning");
        confirmBtn.click();
        log.info("类目选择完成，最终选中: {}（请求路径: {}）", selected, categoryName);
        pageWait(1000);
    }

    /**
     * 填写分类属性（类目选中后动态出现的属性表单）。
     * <p>
     * 页面结构（CDP 实测）：每个属性是一行 .el-form-item，结构为
     * <pre>
     *   label.el-form-item__label &gt; span "原产地(Region Of Origin)"
     *   .el-select &gt; input.el-input__inner[placeholder="请选择原产地"]
     *   .el-select-dropdown.is-multiple &gt; li.el-select-dropdown__item &gt; span "中国(China)"
     * </pre>
     * 关键点：
     * <ul>
     *   <li>每个下拉的 input placeholder 与 label 文本一一对应（"请选择" + 属性名）。</li>
     *   <li>下拉为<b>多选</b>（is-multiple），点选项是"加标签"动作，不关闭下拉。</li>
     *   <li>选项文本跨下拉重复（如多个下拉都有"否(No)"），所以匹配 .el-select-dropdown__item 时
     *       <b>必须加 :visible</b>，限定到当前打开的那一个下拉，否则会命中其它关闭的下拉报 strict mode。</li>
     * </ul>
     *
     * @param frame      iframe 表单
     * @param attributes key=属性名（label 中文部分，如"原产地"），value=选项完整文本（含括号英文）
     */
    private void fillCategoryAttributes(FrameLocator frame, Map<String, String> attributes) {
        int ok = 0, fail = 0;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String attrName = entry.getKey();
            String optionText = entry.getValue();
            if (attrName == null || attrName.isBlank() || optionText == null || optionText.isBlank()) {
                continue;
            }
            try {
                fillCategoryAttribute(frame, attrName, optionText);
                ok++;
            } catch (Exception e) {
                fail++;
                log.warn("分类属性 [{}] 选择失败，跳过: {}", attrName, e.getMessage());
            }
        }
        log.info("分类属性填写完成: 成功 {} 项, 失败 {} 项", ok, fail);
    }

    /** 分类属性可能是下拉框，也可能是普通文本输入框。 */
    private void fillCategoryAttribute(FrameLocator frame, String attrName, String value) {
        Locator formItem = frame.locator(".el-form-item")
                .filter(new Locator.FilterOptions().setHasText(attrName))
                .first();
        Locator select = formItem.locator(".el-select");
        if (select.count() > 0) {
            selectCategoryAttribute(frame, attrName, value);
            return;
        }

        Locator textInput = formItem.locator("input:not([readonly]), textarea").first();
        if (textInput.count() == 0) {
            throw new RuntimeException("未找到下拉框或文本输入框");
        }
        textInput.fill(value);
        pageWait(300);
        log.debug("已填写文本分类属性 [{}]: {}", attrName, value);
    }

    /**
     * 选择单个分类属性：点击 .el-select 触发器打开下拉 → 在<b>可见</b>下拉中点目标选项 → 按 Escape 收起。
     * <p>
     * Element UI 多选 el-select 结构：
     * <pre>
     *   .el-select
     *     .el-select__tags &gt; input.el-select__input.is-small   ← 搜索/输入框，接收点击
     *     .el-input &gt; input.el-input__inner[placeholder][readonly] ← 显示框，被 .el-select__tags 遮挡
     * </pre>
     * 关键坑：readonly 显示框被上层的 .el-select__tags 盖住，直接点它会被拦截
     * （报 "intercepts pointer events" → 30s 超时）。故点击整个 .el-select 容器，让事件正常冒泡打开下拉。
     *
     * @param frame       iframe 表单
     * @param attrName    属性名（label 内中文部分，如"原产地"，用于拼 placeholder）
     * @param optionText  选项完整文本（如"中国大陆(Mainland China)"）
     */
    private void selectCategoryAttribute(FrameLocator frame, String attrName, String optionText) {
        // 定位该属性对应的 .el-select 容器。优先用 placeholder（与 label 一一对应，最精确），
        // 再从该 input 上溯到祖先 .el-select；找不到则按 label 文本兜底。
        Locator input = frame.locator("input[placeholder='请选择" + attrName + "']");
        Locator trigger;
        if (input.count() > 0) {
            trigger = input.locator("xpath=ancestor::div[contains(@class,'el-select')][1]");
        } else {
            trigger = frame.locator(".el-form-item:has(.el-form-item__label:has-text('" + attrName + "')) .el-select");
        }
        trigger.first().click();
        pageWait(300);

        // 关键：必须 :visible，只匹配当前打开的那个下拉（其它关闭的下拉项也在 DOM 中，
        // 且选项文本跨下拉重复如"否(No)"，不加限定会触发 strict mode 多匹配）
        Locator option = frame.locator(".el-select-dropdown__item:visible")
                .filter(new Locator.FilterOptions().setHasText(optionText))
                .first();
        option.waitFor(new Locator.WaitForOptions().setTimeout(4000));
        option.click();
        pageWait(300);

        // 多选下拉选完不会自动关闭，按 Escape 收起，避免遮挡后续字段
        try {
            frame.locator("body").press("Escape");
        } catch (Exception ignored) {
            // 收起失败不影响数据，忽略
        }
        pageWait(200);
    }

    /**
     * 选择品牌
     */
    private void selectBrand(FrameLocator frame, String brand) {
        log.debug("选择品牌: {}", brand);
        selectElOption(frame, "请选择", brand);
        pageWait(500);
    }

    /**
     * 上传产品视频
     */
    private void uploadVideo(FrameLocator frame, String videoPath) {
        log.debug("上传视频: {}", videoPath);
        // 点击添加视频按钮
        Locator addVideoBtn = frame.getByRole(AriaRole.BUTTON,
                new FrameLocator.GetByRoleOptions().setName("添加视频"));
        if (addVideoBtn.isVisible()) {
            addVideoBtn.click();
            pageWait(1000);
            // 文件上传 input（el-upload 的 input 被隐藏，setInputFiles 可直接触发，不要用 isVisible 判断）
            Locator fileInput = frame.locator("input[type='file']").first();
            fileInput.setInputFiles(Paths.get(videoPath));
            log.debug("视频文件已选择: {}", videoPath);
            pageWait(3000); // 等待上传
        }
    }

    /**
     * 选择传图模式
     * <p>
     * 页面结构：el-radio-group，两个 radio：SpuWithSkc（SPU轮播图+SKC预览）、SpuWithSku（SPU轮播图+SKU预览）。
     * 直接用 role=radio + name 点击对应 label。
     */
    private void selectPicSetType(FrameLocator frame, String picSetType) {
        log.debug("选择传图模式: {}", picSetType);
        String labelText = "SpuWithSkc".equals(picSetType) ? "SPU轮播图+SKC预览" : "SPU轮播图+SKU预览";
        Locator radio = frame.getByRole(AriaRole.RADIO,
                new FrameLocator.GetByRoleOptions().setName(labelText));
        if (radio.isVisible()) {
            radio.click();
            pageWait(300);
            log.debug("已选择传图模式: {} ({})", labelText, picSetType);
        } else {
            log.warn("传图模式 radio 不可见: {}", labelText);
        }
    }

    /**
     * 上传产品图（首图、细节图）
     * <p>
     * 页面按上传顺序填充槽位：首图 → 尺寸图 → 细节图...，所以直接按 productMainImage + productDetailImages 上传。
     */
    private void uploadProductImages(FrameLocator frame, TikTokPublishRequest request) {
        ProductImageGroups groups = productImageGroups(request);
        if (groups.allImages().isEmpty()) {
            return;
        }
        Locator fileInputs = frame.locator(".draggable-box input[type='file']");
        long slotCount = fileInputs.count();
        if (slotCount == 0) {
            throw new RuntimeException("未找到产品图上传槽位 .draggable-box input[type='file']");
        }

        if (groups.mainImage() != null) {
            fileInputs.nth(0).setInputFiles(Paths.get(groups.mainImage()));
            waitForProductImageCount(frame, 1);
        }
        if (groups.sizeImage() != null) {
            fileInputs.nth(1).setInputFiles(Paths.get(groups.sizeImage()));
            waitForProductImageCount(frame, 2);
        }
        if (!groups.detailImages().isEmpty()) {
            for (int i = 0; i < groups.detailImages().size(); i++) {
                // 新增图片控件带 multiple 属性；已上传图片的更换控件没有，不能用普通 last()。
                Locator nextSlot = frame.locator(".draggable-box input[type='file'][multiple]").last();
                if (nextSlot.count() == 0) {
                    throw new RuntimeException("未找到产品图新增上传控件（input[multiple]）");
                }
                nextSlot.setInputFiles(Paths.get(groups.detailImages().get(i)));
                waitForProductImageCount(frame, i + 3);
            }
        }

        int uploaded = waitForProductImageCount(frame, groups.allImages().size());
        log.info("已上传 {} 张产品图，首图={}, 尺寸图={}, 细节图={}",
                uploaded, groups.mainImage(), groups.sizeImage(), groups.detailImages());
    }

    List<String> productImages(TikTokPublishRequest request) {
        List<String> images = new ArrayList<>();
        if (request.getProductMainImage() != null && !request.getProductMainImage().isBlank()) {
            images.add(request.getProductMainImage());
        }
        if (request.getProductDetailImages() != null) {
            images.addAll(request.getProductDetailImages());
        }
        return images;
    }

    ProductImageGroups productImageGroups(TikTokPublishRequest request) {
        List<String> images = productImages(request);
        String mainImage = images.isEmpty() ? null : images.getFirst();
        String sizeImage = images.size() < 2 ? null : images.get(1);
        List<String> detailImages = images.size() <= 2 ? List.of() : images.subList(2, images.size());
        return new ProductImageGroups(mainImage, sizeImage, detailImages, images);
    }

    record ProductImageGroups(String mainImage, String sizeImage, List<String> detailImages, List<String> allImages) { }

    private int waitForProductImageCount(FrameLocator frame, int expected) {
        long deadline = System.currentTimeMillis() + properties.getTimeoutMs();
        int count = 0;
        while (System.currentTimeMillis() < deadline) {
            count = productImageCount(frame);
            if (count >= expected) {
                return count;
            }
            pageWait(1000);
        }
        throw new RuntimeException("产品图上传数量不一致，期望 " + expected + " 张，页面实际 " + count + " 张");
    }

    private int productImageCount(FrameLocator frame) {
        String text = frame.locator("body").innerText();
        Matcher matcher = Pattern.compile("已上传\\s*(\\d+)\\s*张").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /**
     * 上传描述图
     */
    private void uploadDescriptionImages(FrameLocator frame, List<String> imagePaths) {
        log.debug("上传 {} 张描述图", imagePaths.size());
        // 描述图区域是 Element UI 的 el-upload：文件 input[type=file] 始终在 DOM 中但被隐藏，
        // Playwright 的 setInputFiles 可直接对隐藏 input 触发 change 上传，无需点击 "选择图片"。
        // 用 CSS class .description-image-group 精确定位该区域，避免 getByText 链式查找失败。
        Locator fileInput = frame.locator(".description-image-group input[type='file']").first();
        for (String imagePath : imagePaths) {
            fileInput.setInputFiles(Paths.get(imagePath));
            pageWait(1200);
            log.debug("已按顺序选择描述图: {}", imagePath);
        }
        log.debug("已按顺序选择 {} 张描述图，等待上传完成", imagePaths.size());
        pageWait(2000);
    }

    /**
     * 上传资质合规区域的产品包装图。
     * <p>
     * 包装图控件由类目动态渲染，不能依赖固定 id；使用表单项标题限定范围，
     * 避免误触描述图或产品图上传 input。
     */
    private void uploadPackageImages(FrameLocator frame, List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return;
        }

        Locator packagingLabel = frame.getByText("产品包装图(Image of packaging)",
                new FrameLocator.GetByTextOptions().setExact(true));
        Locator uploadInput = packagingLabel
                .locator("xpath=ancestor::*[.//input[@type='file']][1]//input[@type='file']");
        if (uploadInput.count() == 0) {
            // 兼容马帮其他语言或页面版本只展示“包装图”的情况。
            packagingLabel = frame.getByText("包装图", new FrameLocator.GetByTextOptions().setExact(true));
            uploadInput = packagingLabel
                    .locator("xpath=ancestor::*[.//input[@type='file']][1]//input[@type='file']");
        }
        if (uploadInput.count() == 0) {
            throw new RuntimeException("未找到资质合规区域的产品包装图上传控件");
        }
        if (uploadInput.count() > 1) {
            throw new RuntimeException("产品包装图上传控件不唯一，找到 " + uploadInput.count() + " 个");
        }

        for (String imagePath : imagePaths) {
            uploadInput.setInputFiles(Paths.get(imagePath));
            pageWait(1200);
            log.info("已按顺序选择产品包装图: {}", imagePath);
        }
        log.info("已按顺序选择 {} 张产品包装图: {}", imagePaths.size(), imagePaths);
        pageWait(2000);
    }

    /**
     * 填写变种信息。
     * <p>
     * 新版 UI 结构（根据 HTML）：
     * <ol>
     *   <li>变种属性区有若干 {@code natureBlock}，每个标题为属性名（如"颜色""尺码"），
     *       内含 checkbox 列表 + 搜索框 +"添加自定义"按钮。</li>
     *   <li>勾选 checkbox 后，底部 {@code vxe-table} 自动生成组合行。
     *       表格列：色值、预览图（文件上传）、操作。</li>
     * </ol>
     * 优先使用 request.variantAttributes（新版），无则退回到旧的 variantSkus 逻辑。
     */
    private void fillVariantSkus(FrameLocator frame, TikTokPublishRequest request) {
        // === 新版：checkbox 属性组 + vxe-table ===
        if (request.getVariantAttributes() != null && !request.getVariantAttributes().isEmpty()) {
            fillVariantByAttributes(frame, request);
            return;
        }

        // === 旧版：简单 SKU input 行（兼容） ===
        fillVariantBySkuList(frame, request.getVariantSkus());
    }

    /**
     * 新版变种：按属性组选取 + 预览图上表
     */
    private void fillVariantByAttributes(FrameLocator frame, TikTokPublishRequest request) {
        // 1. 遍历每个属性组，勾选对应的 checkbox
        for (Map.Entry<String, List<String>> entry : request.getVariantAttributes().entrySet()) {
            String attrName = entry.getKey();
            List<String> attrValues = entry.getValue();
            log.debug("选择变种属性组 [{}] = {}", attrName, attrValues);

            // 找到该属性对应的 natureBlock（标题文本匹配）
            Locator block = frame.locator(".natureBlock")
                    .filter(new Locator.FilterOptions().setHasText(attrName))
                    .first();

            for (String value : attrValues) {
                // 尝试直接勾选已渲染的 checkbox
                Locator checkbox = exactVariantCheckbox(block, value);
                if (checkbox != null && checkbox.isVisible()) {
                    checkbox.click();
                    pageWait(200);
                    continue;
                }

                // 未渲染 → 在搜索框输入后重试
                Locator searchInput = block.locator("input[placeholder='请输入搜索内容']");
                if (searchInput.isVisible()) {
                    searchInput.fill(value);
                    pageWait(500);
                    Locator filtered = exactVariantCheckbox(block, value);
                    if (filtered != null && filtered.isVisible()) {
                        filtered.click();
                        pageWait(200);
                        searchInput.fill(""); // 清空搜索
                        pageWait(200);
                        continue;
                    }
                }

                // 搜索后仍无 → 通过"添加自定义"创建
                Locator customInput = block.locator("input[placeholder='请输入属性值']");
                if (customInput.isVisible()) {
                    customInput.fill(value);
                    pageWait(200);
                    block.locator("button:has-text('添加自定义')").click();
                    pageWait(300);
                    // 刚添加的可能在 checkbox 列表最后，勾选它
                    Locator newCheckbox = exactVariantCheckbox(block, value);
                    if (newCheckbox != null && newCheckbox.isVisible()) {
                        newCheckbox.click();
                        pageWait(200);
                    }
                }
            }
        }

        // 2. 等待表格生成行
        pageWait(2000);
        Locator tableBody = frame.locator(".vxe-table--body-wrapper tbody");
        long rowCount = tableBody.locator("tr").count();
        log.debug("变种表格已生成 {} 行", rowCount);

        // 3. 上传预览图（如果提供了）
        if (request.getVariantPreviewImages() != null && !request.getVariantPreviewImages().isEmpty()) {
            List<String> images = request.getVariantPreviewImages();
            long rows = tableBody.locator("tr").count();
            for (int i = 0; i < Math.min(images.size(), rows); i++) {
                // 每行的预览图列有 file input
                Locator row = tableBody.locator("tr").nth(i);
                Locator fileInput = row.locator("input[type='file']").first();
                if (fileInput.count() > 0) {
                    fileInput.setInputFiles(Paths.get(images.get(i)));
                    log.debug("上传预览图 {} 到表格第 {} 行", images.get(i), i + 1);
                    pageWait(2000);
                }
            }
        }
    }

    /** 变种选项必须完全匹配，避免“套装”误匹配到“套装1”。 */
    private Locator exactVariantCheckbox(Locator block, String value) {
        for (Locator checkbox : block.locator(".el-checkbox").all()) {
            if (value.equals(checkbox.innerText().trim())) {
                return checkbox;
            }
        }
        return null;
    }

    /**
     * 旧版变种：逐行填写 SKU / 价格 / 库存 input
     */
    private void fillVariantBySkuList(FrameLocator frame, List<TikTokPublishRequest.VariantSku> variants) {
        if (variants == null) return;
        for (int i = 0; i < variants.size(); i++) {
            TikTokPublishRequest.VariantSku variant = variants.get(i);
            log.debug("填写变种 #{}: {}", i + 1, variant.getSku());

            // 如果有多行，可能需要点击 "添加" 按钮
            if (i > 0) {
                Locator addBtn = frame.getByRole(AriaRole.BUTTON,
                        new FrameLocator.GetByRoleOptions().setName("添加"));
                if (addBtn.isVisible()) {
                    addBtn.click();
                    pageWait(500);
                }
            }

            // 填写 SKU
            if (variant.getSku() != null) {
                Locator skuInput = frame.locator("input[placeholder*='SKU']").nth(i);
                if (skuInput.isVisible()) {
                    skuInput.fill(variant.getSku());
                }
            }

            // 填写价格
            if (variant.getPrice() != null) {
                Locator priceInput = frame.locator("input[placeholder*='价格']").nth(i);
                if (priceInput.isVisible()) {
                    priceInput.fill(String.valueOf(variant.getPrice()));
                }
            }

            // 填写库存
            if (variant.getStock() != null) {
                Locator stockInput = frame.locator("input[placeholder*='库存']").nth(i);
                if (stockInput.isVisible()) {
                    stockInput.fill(String.valueOf(variant.getStock()));
                }
            }

            pageWait(300);
        }
    }

    /**
     * 填写交易信息（变种属性选择完成后弹出的表格, id=box7）。
     * <p>
     * 表格结构：
     * <ul>
     *   <li>每行 = 一个颜色</li>
     *   <li>col_26 = 颜色名</li>
     *   <li>col_27 = SKC 输入框（跨 merge-div 共用）</li>
     *   <li>col_28 = 备货模式 select</li>
     *   <li>col_29 = 尺码 merge-div 列表</li>
     *   <li>col_30 ~ col_35 = SKU/价格/库存/尺寸/重量/状态，各按尺码拆 merge-div</li>
     * </ul>
     */
    private void fillTransactionInfo(FrameLocator frame, List<TikTokPublishRequest.TransactionRow> rows) {
        log.debug("填写交易信息，共 {} 条", rows.size());

        // 等待交易信息卡片出现
        frame.locator("#box7").waitFor(new Locator.WaitForOptions().setTimeout(10000));
        pageWait(1000);

        Locator table = frame.locator("#box7 .vxe-table--body-wrapper tbody");
        long rowCount = table.locator("tr").count();
        if (rowCount == 0) {
            log.warn("交易信息表格未生成，跳过");
            return;
        }

        for (int r = 0; r < rowCount; r++) {
            Locator row = table.locator("tr").nth(r);

            // 第1列: 颜色（文字）
            String color = row.locator("td:nth-child(1)").innerText().trim();
            if (color.isEmpty()) continue;

            // 第2列: SKC input
            Locator skcInput = row.locator("td:nth-child(2) input[type='text']").first();

            // 第4列: 尺码 merge-div（确定尺寸数量）
            long sizeCount = row.locator("td:nth-child(4) .merge-div").count();
            if (sizeCount == 0) continue;

            for (int s = 0; s < sizeCount; s++) {
                // 第4列第s个merge-div: 尺码名称
                String size = row.locator("td:nth-child(4) .merge-div").nth(s).innerText().trim();

                TikTokPublishRequest.TransactionRow tr = rows.stream()
                        .filter(t -> color.equals(t.getColor()) && size.equals(t.getSize()))
                        .findFirst().orElse(null);
                if (tr == null) {
                    log.warn("未找到匹配的交易行: color={}, size={}", color, size);
                    continue;
                }

                // SKC — 第2列，只在第一个 merge-div 填写（跨行共用）
                if (s == 0 && tr.getSkc() != null && skcInput.isVisible()) {
                    skcInput.fill(tr.getSkc());
                    log.debug("  填写 SKC [{}] = {}", color, tr.getSkc());
                }

                // 备货模式 — 第3列 el-select，仅在第一个 merge-div 操作
                if (s == 0 && tr.getStockingMode() != null) {
                    // 直接操作 el-select 的 Vue 组件实例设置内部值，
                    // 绕过 UI 下拉面板（popper-append-to-body 搬移到 <body> 不可点击）。
                    row.locator("td:nth-child(3) .el-select").first().evaluate("(el, value) => {" +
                            "  const vm = el.__vue__;" +
                            "  if (!vm) return;" +
                            "  vm.selectedLabel = value;" +
                            "  vm.value = value;" +
                            "  vm.$emit('input', value);" +
                            "  vm.$emit('change', value);" +
                            "}", tr.getStockingMode());
                    log.debug("  填写备货模式 [{}] = {} (via Vue vm)", color, tr.getStockingMode());
                    pageWait(300);
                }

                // 第5~10列各自的字段（按 merge-div 索引 s 对应同索引的尺码）
                Locator skuInput = row.locator("td:nth-child(5) .merge-div").nth(s).locator("input[type='text']").first();
                Locator priceInput = row.locator("td:nth-child(6) .merge-div").nth(s).locator("input[type='text']").first();
                Locator stockInput = row.locator("td:nth-child(7) .merge-div").nth(s).locator("input[type='text']").first();
                Locator lengthInput = row.locator("td:nth-child(8) .merge-div").nth(s).locator("input[placeholder='长']").first();
                Locator widthInput = row.locator("td:nth-child(8) .merge-div").nth(s).locator("input[placeholder='宽']").first();
                Locator heightInput = row.locator("td:nth-child(8) .merge-div").nth(s).locator("input[placeholder='高']").first();
                Locator weightInput = row.locator("td:nth-child(9) .merge-div").nth(s).locator("input[type='text']").first();

                if (tr.getSku() != null) skuInput.fill(tr.getSku());
                if (tr.getPrice() != null) priceInput.fill(String.valueOf(tr.getPrice()));
                if (tr.getStock() != null) stockInput.fill(String.valueOf(tr.getStock()));
                if (tr.getLength() != null) lengthInput.fill(String.valueOf(tr.getLength()));
                if (tr.getWidth() != null) widthInput.fill(String.valueOf(tr.getWidth()));
                if (tr.getHeight() != null) heightInput.fill(String.valueOf(tr.getHeight()));
                if (tr.getWeight() != null) weightInput.fill(String.valueOf(tr.getWeight()));

                // 第10列: 状态开关
                if (tr.getEnabled() != null) {
                    Locator switchEl = row.locator("td:nth-child(10) .merge-div").nth(s).locator(".el-switch");
                    boolean isChecked = switchEl.locator("input[type='checkbox']").isChecked();
                    if (tr.getEnabled() != isChecked) {
                        switchEl.click();
                        pageWait(200);
                    }
                }

                pageWait(200);
            }
        }
        log.debug("交易信息填写完成");
    }

    /**
     * 选择制造商
     */
    private void selectManufacturer(FrameLocator frame, String manufacturer) {
        log.debug("选择制造商: {}", manufacturer);
        selectElOption(frame, "请选择制造商", manufacturer);
        pageWait(500);
    }

    /**
     * 选择欧盟责任人
     */
    private void selectEuResponsiblePerson(FrameLocator frame, String person) {
        log.debug("选择欧盟责任人: {}", person);
        selectElOption(frame, "请选择欧盟责任人", person);
        pageWait(500);
    }

    /**
     * 通用 el-select 选择：点开输入框→等可见选项→点击。
     * 带重试（首次点击偶发不触发下拉打开，已通过 CDP 实测确认点 input 可打开）。
     * 失败抛异常，由调用方决定是否中止流程。
     */
    private void selectElOption(FrameLocator frame, String inputPlaceholder, String text) {
        Locator input = frame.locator("input[placeholder='" + inputPlaceholder + "']");
        // 只匹配 el-select 下拉项（避免命中页面其它 <li>），按文本过滤
        Locator item = frame.locator(".el-select-dropdown__item")
                .filter(new Locator.FilterOptions().setHasText(text))
                .first();
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                input.click();
            } catch (Exception e) {
                last = e;
            }
            try {
                // 等待目标项变为可见（下拉打开后该项才可见）
                item.waitFor(new Locator.WaitForOptions().setTimeout(3000));
                item.click();
                log.debug("已选择 [{}] = {}", inputPlaceholder, text);
                return;
            } catch (Exception e) {
                last = e;
                log.warn("下拉未打开或未找到项 '{}' (第{}次)，重试...", text, attempt);
                pageWait(500);
            }
        }
        throw new RuntimeException("选择下拉项失败: placeholder=" + inputPlaceholder + ", text=" + text
                + (last != null ? " (" + last.getMessage() + ")" : ""));
    }

    private void pageWait(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 带重试的导航：新建标签页后立即 navigate 偶发 ERR_ADDRESS_INVALID，重试一次即可。
     */
    private void navigateWithRetry(Page page, String url) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                page.navigate(url);
                return;
            } catch (Exception e) {
                log.warn("导航失败 (第{}次): {} - {}", attempt, url, e.getMessage());
                if (attempt == maxAttempts) {
                    throw e;
                }
                pageWait(1500);
            }
        }
    }

    // ========== 结果封装 ==========

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static class PublishResult {
        private boolean success;
        private String message;
        private String finalUrl;
        private long elapsedMs;
        private String screenshotPath;

        public static PublishResult success(String message, String finalUrl, long elapsedMs, String screenshotPath) {
            return PublishResult.builder()
                    .success(true)
                    .message(message)
                    .finalUrl(finalUrl)
                    .elapsedMs(elapsedMs)
                    .screenshotPath(screenshotPath)
                    .build();
        }

        public static PublishResult failure(String message, long elapsedMs, String screenshotPath) {
            return PublishResult.builder()
                    .success(false)
                    .message(message)
                    .elapsedMs(elapsedMs)
                    .screenshotPath(screenshotPath)
                    .build();
        }
    }
}
