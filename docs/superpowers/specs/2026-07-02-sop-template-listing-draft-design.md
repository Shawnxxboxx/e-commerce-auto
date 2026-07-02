# SOP 模板与上架草稿设计

## 目标

为 TikTok Shop 全托管商品上架建设一套“模板驱动”的生成与审核流程。现有浏览器自动化继续作为最终上架执行器；新增能力负责 SOP 模板管理、商品素材包解析、通过 `codex exec` 生成上架草稿、人工审核草稿，并将审核后的草稿转换为现有的 `TikTokPublishRequest`。

第一版使用 MySQL 存储数据，使用 MyBatis-Plus 实现 Mapper 和基础 CRUD。由于目前没有单独购买模型 API key，AI 生成先通过 `codex exec` 实现；该能力必须通过接口隔离，后续可以替换为 OpenAI API 或其他模型服务。

## 范围

第一版包含：

- SOP 模板增删改查中的新增、查询、编辑。
- 商品素材包解析。
- 易于人工填写和代码解析的 `属性信息.txt` 格式。
- 通过 `codex exec` 生成上架草稿。
- 草稿人工审核与编辑。
- 审核后调用现有 `MabangPublisher.publish` 保存马帮草稿。
- MySQL 表结构和 MyBatis-Plus 模块边界。

第一版不包含：

- 通过 `gpt-image-2` 或其他图片 API 全自动生成真实商品主图。
- 多用户权限管理。
- 模板版本表。
- 复杂报表和数据分析。
- 重写现有马帮浏览器自动化。

## 当前项目上下文

当前项目是 Spring Boot 应用，已经包含 Playwright 浏览器自动化。`MabangPublisher.publish(TikTokPublishRequest request)` 已经覆盖马帮 TikTok 全托管上架所需的大部分字段：

- 店铺和类目。
- 分类属性。
- 来源 URL。
- 中文标题和英文标题。
- 产品首图、尺码表图、细节图、描述图。
- 变种属性和变种预览图。
- SKU、价格、库存、尺寸、重量等交易信息。
- 保存或刊登动作。

新设计中，`MabangPublisher` 只作为最终执行器。它不关心 SOP 模板、原始素材目录、`codex exec`、草稿审核状态。

## 总体架构

整体流程分为五层：

```text
SOP 模板
-> 商品素材包
-> AI 草稿生成
-> 人工草稿审核
-> 马帮浏览器上架
```

主要组件：

- `SopTemplate`：前端可管理的轻量 SOP 模板。
- `ProductMaterialPackage`：一个商品素材目录解析后的结构化对象。
- `ListingAiGenerator`：AI 草稿生成接口。
- `CodexExecListingAiGenerator`：第一版 AI 生成实现，底层调用 `codex exec`。
- `ListingDraft`：前端展示和人工编辑的完整上架草稿。
- `ListingDraftToTikTokPublishRequestMapper`：将审核通过的草稿转换为 `TikTokPublishRequest`。
- `MabangPublisher`：已有马帮浏览器自动化执行器。

## SOP 模板模型

第一版模板保持轻量：

```text
SopTemplate
- id
- templateId
- name
- titlePrompt
- mainImagePrompt
- createTime
- updateTime
```

模板规则：

- `titlePrompt` 是一份标题提示词，用来要求 AI 同时生成中文和英文标题信息。
- `mainImagePrompt` 用来描述主图生成或主图处理要求。
- 不设置上线/下线状态，模板可直接编辑并被选择使用。
- 草稿生成时必须保存模板提示词快照，避免模板后续修改影响历史草稿的可追溯性。

草稿中必须保存：

```text
templateId
templateName
titlePromptSnapshot
mainImagePromptSnapshot
```

## MySQL 表结构

本地 MySQL 使用数据库：

```sql
CREATE DATABASE IF NOT EXISTS `e-commerce` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `e-commerce`;
```

注意：数据库名 `e-commerce` 包含连字符，SQL 脚本中引用数据库名时需要使用反引号。

Spring Boot 连接配置示例：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/e-commerce?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=你的本地用户名
spring.datasource.password=你的本地密码
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

### sop_template

```sql
CREATE TABLE sop_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_id VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  title_prompt TEXT NOT NULL,
  main_image_prompt TEXT NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);
```

### listing_draft

```sql
CREATE TABLE listing_draft (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  draft_id VARCHAR(100) NOT NULL UNIQUE,
  template_id VARCHAR(100) NOT NULL,
  template_name VARCHAR(200) NOT NULL,
  title_prompt_snapshot TEXT NOT NULL,
  main_image_prompt_snapshot TEXT NOT NULL,
  material_package_path VARCHAR(1000) NOT NULL,
  status VARCHAR(50) NOT NULL,
  draft_json JSON NOT NULL,
  publish_request_json JSON NULL,
  last_error_type VARCHAR(100) NULL,
  last_error_message TEXT NULL,
  publish_screenshot_path VARCHAR(1000) NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);
```

`draft_json` 保存完整 `ListingDraft`。这样第一版不需要拆很多明细表，后续如果要按 SKU、标题、属性或发布结果查询，再逐步增加明细表。

## MyBatis-Plus 模块设计

推荐包结构：

```text
template
- controller/SopTemplateController
- service/SopTemplateService
- service/impl/SopTemplateServiceImpl
- mapper/SopTemplateMapper
- entity/SopTemplateEntity
- dto/SopTemplateCreateRequest
- dto/SopTemplateUpdateRequest

material
- model/ProductMaterialPackage
- parser/MaterialPackageParser
- parser/AttributeInfoTextParser

draft
- controller/ListingDraftController
- service/ListingDraftService
- service/impl/ListingDraftServiceImpl
- mapper/ListingDraftMapper
- entity/ListingDraftEntity
- model/ListingDraft
- dto/GenerateListingDraftRequest
- dto/UpdateListingDraftRequest

ai
- ListingAiGenerator
- CodexExecListingAiGenerator
- CodexPromptBuilder
- ListingDraftSchemaValidator

publish
- ListingDraftToTikTokPublishRequestMapper
- 复用已有 MabangPublisher
```

Mapper 形式：

```java
@Mapper
public interface SopTemplateMapper extends BaseMapper<SopTemplateEntity> {
}

@Mapper
public interface ListingDraftMapper extends BaseMapper<ListingDraftEntity> {
}
```

需要新增依赖：

```xml
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
  <version>3.5.x</version>
</dependency>

<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
</dependency>
```

## 商品素材包结构

每个商品使用一个独立目录，目录结构固定：

```text
product-materials/
  商品名称/
    主图/
      1.jpg
      2.jpg
    副图/
      2.jpg
      3.jpg
      4.jpg
    尺码表/
      size.jpg
    属性信息.txt
```

目录规则：

- `主图/`：作为主图生成或主图处理的来源素材。
- `副图/`：直接作为描述图使用，全部图片按文件名排序后进入草稿。
- `尺码表/`：提供商品尺码表图片，也可作为规格相关图片素材。
- `属性信息.txt`：保存产品信息、分类属性、变种属性和交易信息。

第一版支持图片格式：

```text
.jpg
.jpeg
.png
```

## 属性信息.txt 格式约定

文本格式需要同时满足“运营好填写”和“代码好解析”。

解析规则：

- 空行忽略。
- `#` 开头的行作为注释。
- `[分段名]` 表示一个分段。
- 普通字段使用 `key=value`。
- 表格使用 `|` 分隔。
- 图片只写相对于固定目录的文件名，不写绝对路径。

示例：

```text
[产品信息]
产品名称=黑白剪刀
来源URL=
店铺=xxx店铺
类目=家用工具/厨房工具
品牌=无品牌

[分类属性]
使用=家用
材质=不锈钢
是否含有化学物质=否
是否含有致癌物质=否
含电池=否
产品类型=其他
原产地=中国

[变种属性]
颜色=套装1
规格=均码
尺码表图片=size.jpg

[交易信息]
颜色|规格|备货模式|SKC货号|SKU货号|不含税价|库存|长|宽|高|重量g
套装1|均码|JIT备货|黑白剪刀|黑白剪刀-套装1-均码|5|999|21|9|2|150
```

解析结果：

```text
ProductMaterialPackage
- materialPackagePath
- productName
- sourceUrl
- shopName
- categoryName
- brand
- categoryAttributes
- variantAttributes
- sizeChartImagePath
- transactionRows
- mainImageSourcePaths
- detailImagePaths
```

如果缺少必需分段或交易信息表头不符合约定，解析器应该直接失败并返回明确错误。

## ListingDraft 草稿模型

`ListingDraft` 是 AI 输出和人工审核的核心对象。

推荐结构：

```text
ListingDraft
- draftId
- templateId
- templateName
- titlePromptSnapshot
- mainImagePromptSnapshot
- materialPackagePath
- status

basicInfo
- shopName
- categoryName
- sourceUrl
- brand

titleInfo
- productNameCn
- productNameEn
- chineseTitle
- englishTitle
- titleReviewNotes

categoryAttributes
- Map<String, String>

imageInfo
- mainImageSourcePaths
- mainImagePrompt
- finalMainImagePath
- productSizeChartImage
- productDetailImages
- descriptionImagePaths
- imageReviewNotes

variantInfo
- variantAttributes
- variantPreviewImages

transactionRows
- color
- size
- skc
- stockingMode
- sku
- price
- stock
- length
- width
- height
- weight
- enabled

riskReview
- riskWords
- unsupportedClaims
- brandRisk
- imageRisk
- aiReviewNotes

publishResult
- publishRequestSnapshot
- publishScreenshotPath
- publishErrorMessage
```

草稿状态：

```text
GENERATED
REVIEWING
APPROVED
PUBLISHED
FAILED
```

只有状态为 `APPROVED` 的草稿才能调用上架。

## Codex Exec 集成

使用接口隔离 AI 提供方：

```java
public interface ListingAiGenerator {
    ListingDraft generateDraft(SopTemplateEntity template, ProductMaterialPackage materialPackage);
}
```

第一版实现：

```text
CodexExecListingAiGenerator
```

后续可替换为：

```text
OpenAiListingAiGenerator
DashScopeListingAiGenerator
LocalModelListingAiGenerator
```

`CodexPromptBuilder` 需要传给 Codex：

- 系统固定 SOP 规则。
- `template.titlePrompt`。
- `template.mainImagePrompt`。
- 已解析的 `ProductMaterialPackage`。
- 主图、副图、尺码表图片路径清单。
- `ListingDraft` JSON 输出要求。

第一版 Codex 负责：

- 生成中文产品名称和英文产品名称。
- 生成中文标题和英文标题。
- 结合模板主图提示词生成本商品的 `mainImagePrompt`。
- 保留解析出的分类属性、变种属性和交易信息，除非提示词要求做格式归一化。
- 生成风险检查说明。
- 输出符合 `ListingDraft` 结构的 JSON。

第一版 Codex 不直接生成真实 JPG/PNG 主图。它只生成主图提示词和图片任务信息。运营需要在审核前准备最终主图文件，后续也可以接入图片 API 自动生成。

## 图片处理规则

主图：

```text
主图/ 来源图片
-> template.mainImagePrompt
-> draft.imageInfo.mainImagePrompt
-> finalMainImagePath
```

第一版 `finalMainImagePath` 可以由人工准备。该文件不存在时，草稿不能审核通过。

描述图：

```text
副图/ 中全部图片按文件名排序
-> draft.imageInfo.descriptionImagePaths
-> TikTokPublishRequest.descriptionImagePaths
```

尺码表：

```text
属性信息.txt [变种属性] 尺码表图片
-> 尺码表/{filename}
```

如果没有填写 `尺码表图片`，默认取 `尺码表/` 目录中按文件名排序后的第一张图片。

## 草稿到上架请求的映射

审核通过后，`ListingDraft` 转换为 `TikTokPublishRequest`：

```text
ListingDraft.basicInfo.shopName
-> TikTokPublishRequest.shopName

ListingDraft.basicInfo.categoryName
-> TikTokPublishRequest.categoryName

ListingDraft.categoryAttributes
-> TikTokPublishRequest.categoryAttributes

ListingDraft.basicInfo.sourceUrl
-> TikTokPublishRequest.sourceUrl

ListingDraft.titleInfo.chineseTitle
-> TikTokPublishRequest.chineseTitle

ListingDraft.titleInfo.englishTitle
-> TikTokPublishRequest.englishTitle

ListingDraft.basicInfo.brand
-> TikTokPublishRequest.brand

ListingDraft.imageInfo.finalMainImagePath
-> TikTokPublishRequest.productMainImage

ListingDraft.imageInfo.productSizeChartImage
-> TikTokPublishRequest.productSizeChartImage

ListingDraft.imageInfo.productDetailImages
-> TikTokPublishRequest.productDetailImages

ListingDraft.imageInfo.descriptionImagePaths
-> TikTokPublishRequest.descriptionImagePaths

ListingDraft.variantInfo.variantAttributes
-> TikTokPublishRequest.variantAttributes

ListingDraft.variantInfo.variantPreviewImages
-> TikTokPublishRequest.variantPreviewImages

ListingDraft.transactionRows
-> TikTokPublishRequest.transactionInfo

publish=false
-> 在马帮保存草稿，等待人工最终确认
```

调用 `MabangPublisher.publish` 前，需要将生成的发布请求保存到 `listing_draft.publish_request_json`。

## API 设计

SOP 模板接口：

```text
GET  /api/sop-templates
POST /api/sop-templates
GET  /api/sop-templates/{templateId}
PUT  /api/sop-templates/{templateId}
```

素材包解析接口：

```text
POST /api/material-packages/parse

{
  "materialPackagePath": "/Users/.../product-materials/黑白剪刀"
}
```

草稿接口：

```text
POST /api/listing-drafts/generate
GET  /api/listing-drafts/{draftId}
PUT  /api/listing-drafts/{draftId}
POST /api/listing-drafts/{draftId}/approve
POST /api/listing-drafts/{draftId}/publish
```

草稿生成请求：

```json
{
  "templateId": "tiktok-fullservice-mabang-v1",
  "materialPackagePath": "/Users/.../product-materials/黑白剪刀"
}
```

## 前端页面流程

第一版页面：

```text
模板管理
- 模板列表
- 新建模板
- 编辑模板
- 第一版不做删除，避免误删模板

生成草稿
- 选择模板
- 输入素材包路径
- 解析素材包
- 预览解析结果
- 执行 AI 草稿生成

草稿审核
- 编辑标题
- 查看主图提示词
- 检查最终主图路径
- 查看副图和尺码表
- 查看分类属性
- 查看变种和交易信息
- 审核通过
- 发布到马帮
```

页面应偏操作台风格，信息密度适中，优先服务运营反复使用，不做营销页。

## 校验与错误处理

素材包校验：

- 缺少 `主图/`。
- 缺少 `副图/`。
- 缺少 `尺码表/`。
- 缺少 `属性信息.txt`。
- 必需图片目录为空。
- 图片格式不支持。
- 缺少必需文本分段。
- 交易信息表头不符合约定。
- 交易信息行列数不匹配。

AI 生成校验：

- 找不到 `codex` 命令。
- `codex exec` 超时。
- 输出不是合法 JSON。
- 输出不符合 `ListingDraft` 结构。
- 必填字段为空。

审核校验：

- 中文标题为空。
- 英文标题为空。
- 分类属性为空。
- 最终主图文件不存在。
- 描述图为空。
- 交易信息缺少价格、库存、尺寸或重量。
- 变种属性和交易行不匹配。

发布校验：

- 草稿不是 `APPROVED` 状态。
- Chrome CDP 未连接。
- 马帮页面未登录。
- 店铺或类目无法选择。
- 必填页面字段无法定位。
- 图片上传失败。
- 马帮保存草稿失败。

错误需要持久化到 `listing_draft`：

```text
last_error_type
last_error_message
publish_screenshot_path
```

## 测试策略

单元测试：

- `AttributeInfoTextParser` 能解析所有分段。
- 解析器能拒绝缺失分段和错误交易表头。
- 素材包扫描器能按文件名排序图片。
- `SopTemplateService` 能创建和更新模板。
- `ListingDraftToTikTokPublishRequestMapper` 能映射所有字段。
- 草稿审核校验能拦截缺少最终主图的情况。
- Codex 输出解析器能拒绝非法 JSON。

集成测试：

- MyBatis-Plus Mapper 能写入和读取 `sop_template`。
- MyBatis-Plus Mapper 能写入和读取 `listing_draft`。
- 草稿生成会保存模板提示词快照。

人工验收：

- 使用示例素材包真实执行一次 `codex exec` 草稿生成。
- 使用 `MabangPublisher.publish` 真实保存一次马帮草稿，因为该流程依赖 Chrome 登录态和马帮页面结构。

## 实施顺序

1. 增加 MyBatis-Plus 和 MySQL 配置。
2. 创建 `sop_template` 表和模板 CRUD。
3. 实现素材包解析器和 `属性信息.txt` 解析器。
4. 创建 `ListingDraft` 模型和 `listing_draft` 表。
5. 实现 `ListingDraftToTikTokPublishRequestMapper`。
6. 实现草稿校验和审核通过逻辑。
7. 实现 `CodexExecListingAiGenerator`。
8. 实现草稿生成接口。
9. 实现调用 `MabangPublisher.publish` 的发布接口。
10. 实现模板管理、草稿生成、草稿审核前端页面。

## 已确认决策

- SOP 模板只保存模板名称、标题提示词、主图提示词。
- 不使用模板上线/下线状态。
- 一份标题提示词同时生成中文和英文标题数据。
- `属性信息.txt` 是分类属性、变种属性和交易信息的结构化来源。
- 商品描述图直接使用 `副图/` 目录下全部图片。
- 主图生成能力先做抽象，第一版只生成提示词和任务数据，审核前必须有最终主图路径。
- 第一版使用 MySQL。
- Mapper 和基础 CRUD 使用 MyBatis-Plus。
- 草稿主体以 JSON 形式存入 MySQL，避免第一版过度拆表。
- 现有 `MabangPublisher.publish` 保持最终执行器定位。
