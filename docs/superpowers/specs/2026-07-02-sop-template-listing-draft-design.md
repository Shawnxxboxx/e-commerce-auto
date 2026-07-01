# SOP Template Listing Draft Design

## Goal

Build a template-driven product listing workflow for TikTok Shop full-service publishing through Mabang. The current browser automation remains the final publishing executor. The new work adds SOP template management, product material parsing, AI-assisted draft generation through `codex exec`, human review, and conversion into the existing `TikTokPublishRequest`.

The first version uses MySQL for persistence and MyBatis-Plus for mapper/service plumbing. `codex exec` is the temporary AI engine so the workflow can run before a dedicated model API key is available. The AI integration must be hidden behind an interface so it can later be replaced by OpenAI API or another model provider.

## Scope

In scope:

- SOP template CRUD.
- Product material package parsing.
- A readable `属性信息.txt` format.
- Listing draft generation through `codex exec`.
- Human-editable listing drafts.
- Draft approval and publishing through the existing `MabangPublisher.publish`.
- MySQL schema and MyBatis-Plus module boundaries.

Out of scope for the first version:

- Full automatic image generation through `gpt-image-2` or another image API.
- Multi-user permission control.
- Template version tables.
- Complex reporting and analytics.
- Replacing the existing Mabang browser automation.

## Current Context

The existing project is a Spring Boot application with Playwright automation. `MabangPublisher.publish(TikTokPublishRequest request)` already accepts most fields needed for Mabang TikTok full-service listing:

- shop and category
- category attributes
- source URL
- Chinese and English titles
- product images, size chart image, detail images, description images
- variant attributes and preview images
- transaction rows with SKU, price, stock, dimensions, and weight
- final save/publish action

The new design treats `MabangPublisher` as the final executor. It should not know about SOP templates, raw material folders, `codex exec`, or human review state.

## Architecture

The workflow has five layers:

```text
SOP template
-> product material package
-> AI draft generation
-> human draft review
-> Mabang browser publishing
```

Main components:

- `SopTemplate`: lightweight template managed by the frontend.
- `ProductMaterialPackage`: parsed representation of one product folder.
- `ListingAiGenerator`: interface for AI draft generation.
- `CodexExecListingAiGenerator`: first implementation using `codex exec`.
- `ListingDraft`: full editable draft shown to the operator.
- `ListingDraftToTikTokPublishRequestMapper`: converts approved drafts to `TikTokPublishRequest`.
- `MabangPublisher`: existing browser automation executor.

## SOP Template Model

The first version keeps templates intentionally small:

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

Template behavior:

- `titlePrompt` is one prompt that asks AI to generate both Chinese and English title data.
- `mainImagePrompt` describes how to create or prepare the main product image.
- There is no online/offline status. Templates are directly editable.
- Listing drafts store prompt snapshots so later template edits do not change historical draft meaning.

Listing drafts must persist:

```text
templateId
templateName
titlePromptSnapshot
mainImagePromptSnapshot
```

## MySQL Schema

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

`draft_json` stores the full `ListingDraft`. This keeps the first implementation small and flexible. If later queries need to filter by SKU, title, attributes, or publish result, detail tables can be added without changing the draft workflow.

## MyBatis-Plus Modules

Recommended package layout:

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
- existing MabangPublisher
```

Mapper shape:

```java
@Mapper
public interface SopTemplateMapper extends BaseMapper<SopTemplateEntity> {
}

@Mapper
public interface ListingDraftMapper extends BaseMapper<ListingDraftEntity> {
}
```

Required dependencies:

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

## Product Material Package

Each product is stored as a folder with fixed subdirectories:

```text
product-materials/
  product-name/
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

Directory rules:

- `主图/` is used as source material for main image generation or preparation.
- `副图/` is used directly as description images. All images are included and sorted by filename.
- `尺码表/` supplies the product size chart image and size-related image material.
- `属性信息.txt` contains structured text parsed into product, attribute, variant, and transaction data.

Supported image extensions in the first version:

```text
.jpg
.jpeg
.png
```

## Attribute Info Text Format

The text format must be easy for operators to edit and easy for Java to parse.

Rules:

- Empty lines are ignored.
- Lines beginning with `#` are comments.
- `[section]` starts a section.
- Normal fields use `key=value`.
- Tables use `|` as the delimiter.
- Image references use filenames relative to their fixed directory, not absolute paths.

Example:

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

Parser output:

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

The parser should fail fast when required sections or table columns are missing.

## Listing Draft Model

`ListingDraft` is the editable object returned by AI and reviewed by the operator.

Recommended structure:

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

Draft statuses:

```text
GENERATED
REVIEWING
APPROVED
PUBLISHED
FAILED
```

Publishing is allowed only when status is `APPROVED`.

## Codex Exec Integration

`ListingAiGenerator` hides the AI provider:

```java
public interface ListingAiGenerator {
    ListingDraft generateDraft(SopTemplateEntity template, ProductMaterialPackage materialPackage);
}
```

First implementation:

```text
CodexExecListingAiGenerator
```

Future replacements:

```text
OpenAiListingAiGenerator
DashScopeListingAiGenerator
LocalModelListingAiGenerator
```

The prompt builder passes Codex:

- fixed system SOP rules
- `template.titlePrompt`
- `template.mainImagePrompt`
- parsed `ProductMaterialPackage`
- image path lists
- JSON schema instructions for `ListingDraft`

Codex responsibilities in the first version:

- Generate Chinese and English product names.
- Generate Chinese and English listing titles.
- Generate `mainImagePrompt` for the product.
- Preserve parsed category attributes, variants, and transaction rows unless the prompt explicitly asks for normalization.
- Produce risk review notes.
- Output strict JSON compatible with `ListingDraft`.

Codex does not directly generate the real JPG/PNG main image in the first version. It prepares the prompt and image task data. Operators place the final main image in the expected path before approval, or a later image API implementation can create it automatically.

## Image Handling

Main product image:

```text
主图/ source images
-> template.mainImagePrompt
-> draft.imageInfo.mainImagePrompt
-> finalMainImagePath
```

In the first version, `finalMainImagePath` may be manually prepared. The draft cannot be approved until that file exists.

Description images:

```text
副图/ all images sorted by filename
-> draft.imageInfo.descriptionImagePaths
-> TikTokPublishRequest.descriptionImagePaths
```

Size chart:

```text
属性信息.txt [变种属性] 尺码表图片
-> 尺码表/{filename}
```

If `尺码表图片` is omitted, use the first image in `尺码表/` sorted by filename.

## Draft To Publish Mapping

Approved drafts are converted to `TikTokPublishRequest`:

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
-> save draft in Mabang for human confirmation
```

The generated publish request should be stored in `listing_draft.publish_request_json` before invoking `MabangPublisher.publish`.

## API Design

SOP template APIs:

```text
GET  /api/sop-templates
POST /api/sop-templates
GET  /api/sop-templates/{templateId}
PUT  /api/sop-templates/{templateId}
```

Material package parsing:

```text
POST /api/material-packages/parse

{
  "materialPackagePath": "/Users/.../product-materials/黑白剪刀"
}
```

Draft APIs:

```text
POST /api/listing-drafts/generate
GET  /api/listing-drafts/{draftId}
PUT  /api/listing-drafts/{draftId}
POST /api/listing-drafts/{draftId}/approve
POST /api/listing-drafts/{draftId}/publish
```

Draft generation request:

```json
{
  "templateId": "tiktok-fullservice-mabang-v1",
  "materialPackagePath": "/Users/.../product-materials/黑白剪刀"
}
```

## Frontend Flow

First version pages:

```text
Template Management
- template list
- create template
- edit template
- no delete in the first version to avoid accidental loss

Generate Draft
- select template
- input material package path
- parse material package
- preview parsed material
- run AI draft generation

Draft Review
- edit titles
- review main image prompt
- check final main image path
- review description images and size chart
- review category attributes
- review variants and transaction rows
- approve
- publish to Mabang
```

The UI should be practical and dense. This is an operator tool, not a marketing page.

## Validation And Error Handling

Material package validation:

- Missing `主图/`.
- Missing `副图/`.
- Missing `尺码表/`.
- Missing `属性信息.txt`.
- Empty required image directory.
- Unsupported image extension.
- Missing required text sections.
- Invalid transaction table header.
- Transaction row column count mismatch.

AI generation validation:

- `codex` command not found.
- `codex exec` timeout.
- Output is not valid JSON.
- Output does not match `ListingDraft` schema.
- Required fields are empty.

Approval validation:

- Chinese title is empty.
- English title is empty.
- Category attributes are empty.
- Final main image file does not exist.
- Description images are empty.
- Transaction rows are missing price, stock, dimensions, or weight.
- Variant attributes do not match transaction rows.

Publish validation:

- Draft is not `APPROVED`.
- Chrome CDP is not connected.
- Mabang page is not logged in.
- Shop or category cannot be selected.
- Required page fields cannot be located.
- Image upload fails.
- Mabang save draft action fails.

Errors are persisted to `listing_draft`:

```text
last_error_type
last_error_message
publish_screenshot_path
```

## Testing Strategy

Unit tests:

- `AttributeInfoTextParser` parses all sections.
- Parser rejects missing sections and invalid transaction headers.
- Material package scanner sorts images by filename.
- `SopTemplateService` creates and updates templates.
- `ListingDraftToTikTokPublishRequestMapper` maps all fields.
- Draft approval validation blocks missing final main image.
- Codex output parser rejects invalid JSON.

Integration tests:

- MyBatis-Plus mapper can insert and read `sop_template`.
- MyBatis-Plus mapper can insert and read `listing_draft`.
- Draft generation persists template prompt snapshots.

Manual acceptance:

- Real `codex exec` generation using a sample material package.
- Real browser publish through `MabangPublisher.publish`, because it depends on Chrome login state and Mabang page structure.

## Implementation Order

1. Add MyBatis-Plus and MySQL configuration.
2. Add `sop_template` table and template CRUD.
3. Add material package parser and `属性信息.txt` parser.
4. Add `ListingDraft` model and `listing_draft` table.
5. Add `ListingDraftToTikTokPublishRequestMapper`.
6. Add draft validation and approval.
7. Add `CodexExecListingAiGenerator`.
8. Add draft generation API.
9. Add publish API that calls `MabangPublisher.publish`.
10. Add frontend pages for template management, generation, and review.

## Decisions

- SOP templates store only name, title prompt, and main image prompt.
- Template online/offline status is not used.
- One title prompt generates both Chinese and English title data.
- `属性信息.txt` is the structured source for parsed category, variant, and transaction information.
- Product description images come directly from all images in `副图/`.
- Main image generation is abstracted, but first version only creates prompt/task data and requires a final image path before approval.
- MySQL is used from the first version.
- MyBatis-Plus is used for mapper and basic CRUD.
- Draft JSON is stored as JSON in MySQL to avoid over-modeling the first version.
- Existing `MabangPublisher.publish` remains the final executor.
