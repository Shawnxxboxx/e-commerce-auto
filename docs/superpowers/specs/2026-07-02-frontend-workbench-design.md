# SOP 上架工作台前端设计

## 目标

在项目根目录新增 `frontend/`，使用 React + Ant Design 实现一个内部上架工作台。第一版前端聚焦三个动作：

1. 管理 SOP 模板。
2. 解析本机商品素材包并预览结构化结果。
3. 基于解析结果生成一个可人工审核的草稿壳，为后续接入 `codex exec` 生成标题和主图做准备。

前端第一版不直接执行马帮发布，也不实现真实 AI 生成。它先把模板、素材和草稿审核的页面骨架跑通，确保后续 CodexExec 和发布接口接入时不用重做交互结构。

## 已确认方向

用户已确认采用工作台式布局，而不是步骤向导。

推荐原因：

- 这是内部运营工具，使用频率高于一次性新手引导。
- 模板、素材、草稿需要频繁来回检查，工作台比向导更高效。
- 后续增加发布记录、失败重试、批量任务时，左侧导航扩展更自然。

## 技术选型

- React
- TypeScript
- Vite
- Ant Design
- Ant Design Icons
- 原生 `fetch` 封装轻量 API client

目录放置：

```text
frontend/
  package.json
  index.html
  vite.config.ts
  tsconfig.json
  src/
    main.tsx
    App.tsx
    api/client.ts
    api/types.ts
    pages/TemplatePage.tsx
    pages/MaterialPage.tsx
    pages/DraftReviewPage.tsx
    components/AppShell.tsx
    components/ImagePathPreview.tsx
    styles.css
```

Vite 开发代理：

```text
/api -> http://localhost:8080
```

## 页面结构

### AppShell

使用 Ant Design `Layout`：

- 左侧 `Sider`：模板管理、素材解析、草稿审核。
- 顶部 `Header`：当前模块标题和后端连接状态。
- 主内容 `Content`：按页面切换。

视觉风格偏操作台：

- 信息密度适中，不做营销式大图 hero。
- 表格、表单、抽屉、提示消息为主。
- 色彩克制，使用 Ant Design 默认主色搭配灰白背景。
- 卡片只用于单个功能块，不嵌套卡片。

### 模板管理

目标：管理 `SopTemplate`。

功能：

- 拉取 `GET /api/sop-templates` 显示模板列表。
- 新增模板：`POST /api/sop-templates`。
- 编辑模板：`PUT /api/sop-templates/{templateId}`。
- 使用抽屉或弹窗编辑字段。

字段：

- `templateId`
- `name`
- `titlePrompt`
- `mainImagePrompt`
- `createTime`
- `updateTime`

交互：

- 左侧表格展示模板 ID、名称、更新时间。
- 右侧或抽屉展示提示词编辑表单。
- 保存后刷新列表并显示成功消息。

### 素材解析

目标：输入本机素材包路径，展示后端解析结果。

功能：

- 输入素材包绝对路径。
- 调用 `POST /api/material-packages/parse`。
- 展示产品基础信息、分类属性、变种属性、交易信息。
- 展示主图、副图、尺码表图片列表。

图片预览：

浏览器不能安全地直接读取任意本机绝对路径。为了让预览可用，后端需要新增一个只读图片预览接口：

```text
GET /api/local-files/image?path=<absolutePath>
```

约束：

- 仅支持 `.jpg`、`.jpeg`、`.png`。
- 只返回存在的普通文件。
- 第一版作为本机内部工具使用，不做公网访问假设。

前端 `ImagePathPreview` 使用这个接口渲染缩略图；加载失败时显示文件名和路径。

### 草稿审核

目标：先让运营看到“最终将上架的数据长什么样”，并可以编辑关键字段。

第一版输入来源：

- 模板管理中选中的模板。
- 素材解析得到的 `ProductMaterialPackage`。

第一版草稿生成方式：

- 不调用真实 CodexExec。
- 以解析结果构造一个本地草稿预览：
  - 中文标题先用 `productName` 占位。
  - 英文标题为空，等待后续 AI 生成。
  - 主图先取 `mainImageSourcePaths[0]`。
  - 描述图使用全部 `detailImagePaths`。
  - 尺码表图使用 `sizeChartImagePath`。
  - 分类属性、变种属性、交易信息直接带入。

页面区域：

- 基础信息表单：店铺、类目、品牌、来源 URL、中文标题、英文标题。
- 图片区：产品主图、尺码表、描述图。
- 属性区：分类属性和变种属性。
- 交易信息表格：SKU、价格、库存、尺寸、重量。
- 校验结果：显示缺标题、缺图、缺交易信息等问题。

后续接入：

- `POST /api/listing-drafts/generate`：由 CodexExec 生成标题和主图。
- `POST /api/listing-drafts/{draftId}/publish`：审核后调用马帮自动化。

## 数据流

```text
前端模板管理
-> /api/sop-templates
-> MySQL sop_template

前端素材解析
-> /api/material-packages/parse
-> ProductMaterialPackage
-> 前端草稿壳
-> 人工审核
```

后续完整数据流：

```text
SopTemplate + ProductMaterialPackage
-> CodexExecListingAiGenerator
-> ListingDraft
-> 人工审核
-> ListingDraftToTikTokPublishRequestMapper
-> MabangPublisher.publish
```

## 错误处理

前端统一处理 API 错误：

- HTTP 非 2xx：显示 `message.error`。
- 素材路径为空：表单内提示。
- 素材包缺目录或 `属性信息.txt` 格式错误：展示后端中文错误信息。
- 图片预览失败：缩略图位置显示文件名，不阻塞整体解析。
- 后端未启动：Header 显示连接异常，并在请求失败时提示。

## 测试策略

前端第一版测试保持轻量：

- `npm run build` 确认 TypeScript 和 Vite 构建通过。
- 用浏览器打开工作台，检查三页基本交互。
- 使用一个临时素材包路径手动验证解析结果渲染。

后端补充测试：

- 图片预览接口只接受支持的图片后缀。
- 非文件路径或不存在路径返回错误。

## 不做事项

第一版不做：

- 登录和权限。
- 多用户隔离。
- 批量任务队列。
- 真实 CodexExec 生成。
- 自动发布按钮。
- 模板版本管理。
- 复杂图片编辑器。

这些能力放到后续迭代，避免前端第一版过重。
