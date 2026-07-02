# SOP 上架工作台前端最小可用版本实施计划

> **给执行代理的说明：** 实施本计划时必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`。任务步骤使用 checkbox（`- [ ]`）跟踪进度。

**目标：** 在项目根目录新增 `frontend/`，实现 React + Ant Design 工作台，覆盖 SOP 模板管理、素材包解析、图片预览和草稿审核壳子。

**架构：** 后端继续提供 REST API，前端通过 Vite 代理访问 `/api`。本轮新增一个后端本机图片只读预览接口，解决浏览器无法直接读取本机绝对路径图片的问题。前端草稿数据先保存在页面状态中，不落库，不接真实 CodexExec。

**技术栈：** Java 21、Spring Boot 4.1、JUnit 5、React、TypeScript、Vite、Ant Design、Ant Design Icons。

---

## 文件结构

本计划会新增或修改这些文件：

```text
src/main/java/com/auto/ecommerce/ecommerceauto/localfile/controller/LocalFileController.java
src/main/java/com/auto/ecommerce/ecommerceauto/localfile/service/LocalImageFileService.java
src/test/java/com/auto/ecommerce/ecommerceauto/localfile/service/LocalImageFileServiceTest.java

frontend/package.json
frontend/index.html
frontend/vite.config.ts
frontend/tsconfig.json
frontend/tsconfig.node.json
frontend/src/main.tsx
frontend/src/App.tsx
frontend/src/styles.css
frontend/src/api/client.ts
frontend/src/api/types.ts
frontend/src/components/AppShell.tsx
frontend/src/components/ImagePathPreview.tsx
frontend/src/pages/TemplatePage.tsx
frontend/src/pages/MaterialPage.tsx
frontend/src/pages/DraftReviewPage.tsx
```

## Task 1：新增后端本机图片预览接口

**文件：**
- 新增：`src/main/java/com/auto/ecommerce/ecommerceauto/localfile/service/LocalImageFileService.java`
- 新增：`src/main/java/com/auto/ecommerce/ecommerceauto/localfile/controller/LocalFileController.java`
- 测试：`src/test/java/com/auto/ecommerce/ecommerceauto/localfile/service/LocalImageFileServiceTest.java`

- [ ] **Step 1：先写失败测试**

创建 `LocalImageFileServiceTest.java`，覆盖两类行为：

- `.jpg` 图片可以读取，并返回 `image/jpeg`。
- `.txt` 等非图片后缀会抛出 `IllegalArgumentException`，错误信息包含 `仅支持 jpg、jpeg、png 图片`。

测试结构：

```java
class LocalImageFileServiceTest {

    @TempDir
    Path tempDir;

    private final LocalImageFileService service = new LocalImageFileService();

    @Test
    void readsSupportedImageFile() throws Exception {
        Path image = tempDir.resolve("main.jpg");
        Files.writeString(image, "image");

        LocalImageFileService.LocalImageFile result = service.readImage(image.toString());

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.bytes()).isEqualTo("image".getBytes());
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        Path text = tempDir.resolve("note.txt");
        Files.writeString(text, "text");

        assertThatThrownBy(() -> service.readImage(text.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 jpg、jpeg、png 图片");
    }
}
```

- [ ] **Step 2：运行测试确认失败**

运行：

```bash
./mvnw -Dtest=LocalImageFileServiceTest test
```

预期：

```text
Compilation failure: package ...localfile.service does not exist
```

- [ ] **Step 3：实现 `LocalImageFileService`**

实现要求：

- `readImage(String rawPath)` 接收本机文件路径。
- 空路径抛出 `图片路径不能为空`。
- 不存在或不是普通文件时抛出 `图片文件不存在: <path>`。
- 仅允许 `.jpg`、`.jpeg`、`.png`。
- 返回 record：`LocalImageFile(String contentType, byte[] bytes)`。
- `.jpg` 和 `.jpeg` 返回 `image/jpeg`，`.png` 返回 `image/png`。

- [ ] **Step 4：实现 `LocalFileController`**

接口：

```text
GET /api/local-files/image?path=<absolutePath>
```

实现要求：

- 调用 `LocalImageFileService.readImage(path)`。
- 使用 `ResponseEntity<byte[]>` 返回图片字节。
- 设置 `Content-Type`。
- 设置 5 分钟本地缓存。

- [ ] **Step 5：运行后端测试**

运行：

```bash
./mvnw -Dtest=LocalImageFileServiceTest test
./mvnw test
```

预期：

```text
BUILD SUCCESS
```

- [ ] **Step 6：提交**

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/localfile src/test/java/com/auto/ecommerce/ecommerceauto/localfile
git commit -m "feat: add local image preview endpoint"
```

## Task 2：搭建 React + Ant Design 前端工程

**文件：**
- 新增：`frontend/package.json`
- 新增：`frontend/index.html`
- 新增：`frontend/vite.config.ts`
- 新增：`frontend/tsconfig.json`
- 新增：`frontend/tsconfig.node.json`
- 新增：`frontend/src/main.tsx`
- 新增：`frontend/src/App.tsx`
- 新增：`frontend/src/styles.css`

- [ ] **Step 1：创建 `frontend/package.json`**

要求：

- 使用 Vite。
- 使用 React 18。
- 使用 Ant Design 5。
- 脚本包含：

```json
{
  "scripts": {
    "dev": "vite --host 127.0.0.1",
    "build": "tsc -b && vite build",
    "preview": "vite preview --host 127.0.0.1"
  }
}
```

依赖：

```text
@ant-design/icons
antd
react
react-dom
```

开发依赖：

```text
@types/react
@types/react-dom
@vitejs/plugin-react
typescript
vite
```

- [ ] **Step 2：创建 Vite 和 TypeScript 配置**

`frontend/vite.config.ts` 要求：

- 使用 `@vitejs/plugin-react`。
- 开发端口固定为 `5173`。
- 代理 `/api` 到 `http://localhost:8080`。

`frontend/tsconfig.json` 要求：

- 开启 `strict`。
- 使用 `react-jsx`。
- `include` 指向 `src`。

`frontend/tsconfig.node.json` 要求：

- 覆盖 `vite.config.ts`。
- `module` 使用 `ESNext`。

- [ ] **Step 3：创建入口文件**

`frontend/index.html`：

- 设置 `lang="zh-CN"`。
- 标题为 `SOP 上架工作台`。
- 挂载点为 `#root`。

`frontend/src/main.tsx`：

- 使用 `ReactDOM.createRoot`。
- 使用 Ant Design `ConfigProvider`。
- locale 使用 `antd/locale/zh_CN`。
- 引入 `./styles.css`。

`frontend/src/App.tsx`：

- 保存当前页面：`templates`、`materials`、`drafts`。
- 保存当前选中的 `SopTemplate`。
- 保存当前解析出的 `ProductMaterialPackage`。
- 根据当前页面渲染 `TemplatePage`、`MaterialPage`、`DraftReviewPage`。

- [ ] **Step 4：创建基础样式**

`frontend/src/styles.css` 要求：

- `html`、`body`、`#root` 高度为 100%。
- 页面背景使用浅灰工作台风格。
- `.app-shell`、`.app-header`、`.app-content` 固定布局。
- `.image-grid` 使用自适应网格展示图片。
- `.image-preview img` 使用 1:1 比例和 `object-fit: cover`。

- [ ] **Step 5：安装前端依赖**

运行：

```bash
cd frontend
npm install
```

预期：

```text
added ... packages
```

- [ ] **Step 6：提交**

```bash
git add frontend/package.json frontend/package-lock.json frontend/index.html frontend/vite.config.ts frontend/tsconfig.json frontend/tsconfig.node.json frontend/src/main.tsx frontend/src/App.tsx frontend/src/styles.css
git commit -m "feat: scaffold react workbench"
```

## Task 3：新增前端 API 类型和请求封装

**文件：**
- 新增：`frontend/src/api/types.ts`
- 新增：`frontend/src/api/client.ts`

- [ ] **Step 1：创建 API 类型**

`frontend/src/api/types.ts` 需要定义：

```text
SopTemplate
MaterialTransactionRow
ProductMaterialPackage
ListingDraftPreview
```

字段要求：

- `SopTemplate` 对应后端模板实体：`templateId`、`name`、`titlePrompt`、`mainImagePrompt`、`createTime`、`updateTime`。
- `ProductMaterialPackage` 对应后端素材解析结果：产品信息、分类属性、变种属性、主图路径、副图路径、尺码表路径、交易行。
- `ListingDraftPreview` 是前端本地草稿壳：基础信息、标题、图片、属性、交易信息。

- [ ] **Step 2：创建 API client**

`frontend/src/api/client.ts` 需要提供：

```ts
listTemplates(): Promise<SopTemplate[]>
createTemplate(template: SopTemplate): Promise<SopTemplate>
updateTemplate(templateId: string, template: Omit<SopTemplate, 'templateId'>): Promise<SopTemplate>
parseMaterialPackage(materialPackagePath: string): Promise<ProductMaterialPackage>
localImageUrl(path: string): string
```

请求规则：

- 使用原生 `fetch`。
- 默认发送 `Content-Type: application/json`。
- 非 2xx 响应读取 body 文本并抛出 `Error`。
- `localImageUrl` 返回 `/api/local-files/image?path=<encodedPath>`。

- [ ] **Step 3：运行构建确认当前缺组件失败**

运行：

```bash
cd frontend
npm run build
```

预期：

```text
Cannot find module './components/AppShell'
```

这个失败是预期的，因为页面组件会在后续任务创建。

## Task 4：实现应用外壳和模板管理页

**文件：**
- 新增：`frontend/src/components/AppShell.tsx`
- 新增：`frontend/src/pages/TemplatePage.tsx`

- [ ] **Step 1：实现 `AppShell`**

要求：

- 使用 Ant Design `Layout`。
- 左侧 `Sider` 宽度为 220。
- 菜单项：
  - 模板管理
  - 素材解析
  - 草稿审核
- 顶部 `Header` 展示当前页面标题。
- 右侧显示 `本机工作台` 标签。
- 导出类型：`AppPageKey = 'templates' | 'materials' | 'drafts'`。

- [ ] **Step 2：实现 `TemplatePage`**

要求：

- 进入页面时调用 `listTemplates()`。
- 表格字段：模板 ID、名称、更新时间、操作。
- 操作包含：选择、编辑。
- 新增模板按钮打开抽屉。
- 编辑模板也使用抽屉。
- 表单字段：
  - `templateId`
  - `name`
  - `titlePrompt`
  - `mainImagePrompt`
- 新增调用 `createTemplate()`。
- 编辑调用 `updateTemplate()`。
- 保存成功后刷新列表，并更新当前选中模板。

- [ ] **Step 3：运行构建确认下一缺口**

运行：

```bash
cd frontend
npm run build
```

预期：

```text
Cannot find module './pages/MaterialPage'
```

- [ ] **Step 4：提交**

```bash
git add frontend/src/components/AppShell.tsx frontend/src/pages/TemplatePage.tsx frontend/src/App.tsx
git commit -m "feat: add template workbench page"
```

## Task 5：实现素材解析页和图片预览组件

**文件：**
- 新增：`frontend/src/components/ImagePathPreview.tsx`
- 新增：`frontend/src/pages/MaterialPage.tsx`

- [ ] **Step 1：实现 `ImagePathPreview`**

要求：

- 入参：`paths: string[]`、`emptyText?: string`。
- 无图片时显示 Ant Design `Empty`。
- 有图片时使用 `.image-grid` 展示。
- 图片 `src` 使用 `localImageUrl(path)`。
- caption 显示文件名。
- 图片加载失败不阻塞页面。

- [ ] **Step 2：实现 `MaterialPage`**

要求：

- 顶部表单输入素材包绝对路径。
- 点击 `解析素材包` 调用 `parseMaterialPackage()`。
- 解析成功后调用 `onMaterialParsed(parsed)`。
- 解析失败使用 `message.error()` 展示后端错误。
- 有素材结果时展示：
  - 产品信息 `Descriptions`
  - 主图来源图片
  - 副图 / 描述图
  - 尺码表
  - 分类属性
  - 变种属性
  - 交易信息表格
- 有素材结果时启用 `进入草稿审核` 按钮。

- [ ] **Step 3：运行构建确认下一缺口**

运行：

```bash
cd frontend
npm run build
```

预期：

```text
Cannot find module './pages/DraftReviewPage'
```

- [ ] **Step 4：提交**

```bash
git add frontend/src/components/ImagePathPreview.tsx frontend/src/pages/MaterialPage.tsx frontend/src/api
git commit -m "feat: add material parsing page"
```

## Task 6：实现草稿审核页

**文件：**
- 新增：`frontend/src/pages/DraftReviewPage.tsx`

- [ ] **Step 1：实现 `DraftReviewPage`**

要求：

- 入参：
  - `template: SopTemplate | null`
  - `material: ProductMaterialPackage | null`
- 没有素材解析结果时显示空状态。
- 有素材时构造本地 `ListingDraftPreview`：
  - `shopName` 使用素材店铺。
  - `categoryName` 使用素材类目。
  - `sourceUrl` 使用素材来源 URL。
  - `chineseTitle` 使用 `productName`。
  - `englishTitle` 留空。
  - `brand` 使用素材品牌。
  - `productMainImage` 使用第一张主图。
  - `productSizeChartImage` 使用尺码表路径。
  - `descriptionImagePaths` 使用全部副图。
  - `categoryAttributes` 原样带入。
  - `variantAttributes` 将字符串按英文逗号拆成数组。
  - `transactionInfo` 使用素材交易行。
- 校验并显示：
  - 缺少中文标题
  - 缺少产品主图
  - 缺少描述图
  - 缺少交易信息
- 展示模板快照、基础信息表单、图片审核区、交易信息表格。

- [ ] **Step 2：运行前端构建**

运行：

```bash
cd frontend
npm run build
```

预期：

```text
✓ built in ...
```

- [ ] **Step 3：提交**

```bash
git add frontend/src/pages/DraftReviewPage.tsx
git commit -m "feat: add draft review page"
```

## Task 7：最终验证和本地启动

**文件：**
- 只有验证发现问题时才修改相关文件。

- [ ] **Step 1：运行后端测试**

运行：

```bash
./mvnw test
```

预期：

```text
BUILD SUCCESS
```

- [ ] **Step 2：运行前端构建**

运行：

```bash
cd frontend
npm run build
```

预期：

```text
✓ built in ...
```

- [ ] **Step 3：启动后端**

运行：

```bash
./mvnw spring-boot:run
```

预期：

```text
Started ECommerceAutoApplication
```

- [ ] **Step 4：启动前端**

运行：

```bash
cd frontend
npm run dev
```

预期：

```text
Local: http://127.0.0.1:5173/
```

- [ ] **Step 5：浏览器冒烟验证**

打开：

```text
http://127.0.0.1:5173/
```

检查：

- 模板管理页可以看到空表或真实模板列表。
- 新增/编辑模板表单可以打开。
- 素材解析页可以提交路径，并展示错误或解析结果。
- 图片预览区域不会破坏页面布局。
- 草稿审核页无素材时显示空状态，有素材后显示草稿壳。

- [ ] **Step 6：提交验证修复**

如果验证时做了修复：

```bash
git add <changed-files>
git commit -m "fix: polish frontend workbench verification"
```

如果没有修复，不创建空提交。
