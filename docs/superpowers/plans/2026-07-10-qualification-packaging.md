# 资质合规与产品包装图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从素材包解析制造商、欧盟责任人和包装图，并在马帮发布第 8 步完成选择与上传。

**Architecture:** 资质字段由属性文本解析器产生，包装图由素材包解析器读取；两者经过草稿 JSON 和发布请求映射传递到 `MabangPublisher`。数据库无需新增列，因为草稿正文已经以 JSON 保存。

**Tech Stack:** Java 21, Spring Boot, Lombok, JUnit 5, AssertJ, Playwright Java, MyBatis-Plus。

## Global Constraints

- 属性文件使用 `[资质合规]`、`制造商=...`、`欧盟责任人=...`。
- 素材目录使用 `包装图/`，图片格式沿用 jpg/jpeg/png。
- 不改变现有产品图顺序：产品主图 + 副图；包装图单独上传。
- 下拉框使用页面已确认的 placeholder：`请选择制造商`、`请选择欧盟责任人`。
- 找不到包装图上传控件时必须抛出清晰错误，不能静默跳过。

### Task 1: Extend material parsing

**Files:**
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/material/model/ProductMaterialPackage.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/material/parser/AttributeInfoTextParser.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/material/parser/MaterialPackageParser.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/material/parser/AttributeInfoTextParserTest.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/material/parser/MaterialPackageParserTest.java`

**Interfaces:**
- Produces `ProductMaterialPackage.manufacturer`, `euResponsiblePerson`, and `packageImagePaths`.

- [ ] Add the three fields with list default initialization.
- [ ] Require `[资质合规]` and parse both required values using existing key-value validation.
- [ ] Add `包装图` directory constant and load sorted supported images with the existing `listImages` helper.
- [ ] Add failing tests for parsed values and sorted package image paths.
- [ ] Run `./mvnw -q -Dtest=AttributeInfoTextParserTest,MaterialPackageParserTest test`; verify the new tests fail before implementation and pass after it.
- [ ] Commit `feat: parse qualification and packaging materials`.

### Task 2: Carry fields through drafts and publish requests

**Files:**
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/model/ListingDraft.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/service/ListingDraftFactory.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/playwright/TikTokPublishRequest.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/publish/ListingDraftToTikTokPublishRequestMapper.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/draft/service/ListingDraftFactoryTest.java`

**Interfaces:**
- `ListingDraft` and `TikTokPublishRequest` expose `manufacturer`, `euResponsiblePerson`, and `packageImagePaths`.

- [ ] Add fields to the draft and request models.
- [ ] Map material fields in `ListingDraftFactory.create`.
- [ ] Map draft fields in `ListingDraftToTikTokPublishRequestMapper.map`.
- [ ] Add assertions proving all three fields survive draft creation.
- [ ] Run the focused draft tests; verify pass.
- [ ] Commit `feat: carry qualification data into publish drafts`.

### Task 3: Automate qualification and packaging upload

**Files:**
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/playwright/MabangPublisher.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/playwright/MabangPublisherTest.java`

**Interfaces:**
- `publish` consumes the new request fields and completes the existing form flow.

- [ ] Keep manufacturer and EU responsible person selection using their confirmed placeholders.
- [ ] Add `uploadPackageImages(FrameLocator, List<String>)` that scopes to the form item containing `产品包装图` or `包装图`, then selects its `input[type=file]`.
- [ ] Require exactly one upload input in that scoped area, call `setInputFiles` with all package paths, and log the count.
- [ ] Call the method after product/description image uploads and before variant/qualification completion, with an empty-list no-op.
- [ ] Add unit-level grouping/guard tests for empty paths and package path preservation where the current test style supports it.
- [ ] Run the focused Playwright publisher tests.
- [ ] Commit `feat: upload packaging images during mabang publish`.

### Task 4: Full verification

**Files:**
- No source changes expected.

- [ ] Run `./mvnw test`.
- [ ] Confirm all tests pass and inspect `git diff --check`.
- [ ] Run `git status --short` and report any unrelated pre-existing changes without reverting them.

