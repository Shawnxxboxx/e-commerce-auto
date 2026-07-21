# 局域网素材包上传实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让局域网用户从浏览器选择本机素材目录，上传到服务器解析并通过唯一素材包 ID 与草稿绑定，同时支持物理删除草稿和素材目录。

**Architecture:** 浏览器将白名单文件和相对路径作为单次 multipart 请求上传；后端在受控存储根目录内完成校验、临时写入、解析和持久化。草稿只接收 `materialPackageId`，后端内部解析为服务器路径供 Codex 和 Playwright 使用；删除使用隔离目录加数据库事务实现可恢复的跨资源操作。

**Tech Stack:** Java 21、Spring Boot 4、Spring MVC multipart、MyBatis-Plus、MySQL、React 18、TypeScript、Ant Design、JUnit 5。

## Global Constraints

- 单个素材包总大小不超过 50MB。
- 只接受 `属性信息.txt` 与 `主图/`、`副图/`、`尺码表/`、`包装图/` 下的 `jpg/jpeg/png`。
- 所有客户端相对路径都必须防止绝对路径和目录穿越。
- 新流程不接受客户端绝对路径；服务器绝对路径不暴露给前端。
- 不增加 ZIP、分片上传、断点续传或跨资源事务依赖。
- `GENERATING`、`PUBLISHING` 草稿不可删除；其他状态允许物理删除。
- 保留旧路径型草稿的查询和上架兼容。

---

### Task 1: 安全素材存储服务

**Files:**
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/config/MaterialStorageProperties.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/service/MaterialPackageStorageService.java`
- Create: `src/test/java/com/auto/ecommerce/ecommerceauto/material/service/MaterialPackageStorageServiceTest.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: `List<MultipartFile> files`、`List<String> relativePaths`、`String materialPackageId`。
- Produces: `Path store(String materialPackageId, List<MultipartFile> files, List<String> relativePaths)`、`Path resolvePackage(String materialPackageId)`、`Path resolveFile(String materialPackageId, String relativePath)`、`void deletePackage(String materialPackageId)`、`Path quarantine(String materialPackageId)`、`void restore(String materialPackageId)`、`void purgeQuarantine(String materialPackageId)`。

- [ ] **Step 1: 写安全路径和 50MB 限制的失败测试**

```java
@TempDir Path tempDir;

@Test
void storesAllowedFilesAndPreservesRelativePaths() {
    var service = new MaterialPackageStorageService(properties(tempDir));
    Path stored = service.store("material-1",
            List.of(file("属性信息.txt", "[产品信息]"), file("1.jpg", "image")),
            List.of("属性信息.txt", "主图/1.jpg"));
    assertThat(stored.resolve("属性信息.txt")).exists();
    assertThat(stored.resolve("主图/1.jpg")).exists();
}

@ParameterizedTest
@ValueSource(strings = {"../secret.txt", "/tmp/secret.txt", "主图/../../secret.jpg", "其他/1.jpg"})
void rejectsUnsafeOrUnsupportedPaths(String relativePath) {
    assertThatThrownBy(() -> service.store("material-1", List.of(file("x", "x")), List.of(relativePath)))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void rejectsPackageLargerThanFiftyMegabytes() {
    MultipartFile oversized = new MockMultipartFile("files", "1.jpg", "image/jpeg", new byte[50 * 1024 * 1024 + 1]);
    assertThatThrownBy(() -> service.store("material-1", List.of(oversized), List.of("主图/1.jpg")))
            .hasMessageContaining("50MB");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=MaterialPackageStorageServiceTest test`

Expected: FAIL，类 `MaterialPackageStorageService` 不存在。

- [ ] **Step 3: 实现最小安全存储服务**

```java
@ConfigurationProperties(prefix = "material.storage")
public record MaterialStorageProperties(Path root, long maxPackageSize) {}
```

```java
private Path checkedTarget(Path packageRoot, String relativePath) {
    Path relative = Path.of(relativePath).normalize();
    if (relative.isAbsolute() || relative.startsWith("..") || !isAllowed(relative)) {
        throw new IllegalArgumentException("非法素材路径: " + relativePath);
    }
    Path target = packageRoot.resolve(relative).normalize();
    if (!target.startsWith(packageRoot)) {
        throw new IllegalArgumentException("素材路径越界: " + relativePath);
    }
    return target;
}
```

实现要求：

- 校验 `files.size() == relativePaths.size()`、相对路径不重复、累计 `MultipartFile.getSize()` 不超过 `52_428_800` 字节。
- 先写 `${root}/.tmp/{id}`，成功后使用 `Files.move` 移入 `${root}/{id}`；失败递归清理临时目录。
- 隔离目录固定为 `${root}/.trash/{id}`，不能由调用方传入任意路径。
- `application.properties` 增加：

```properties
material.storage.root=${MATERIAL_STORAGE_ROOT:./data/material-packages}
material.storage.max-package-size=52428800
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=55MB
```

- [ ] **Step 4: 运行存储测试**

Run: `./mvnw -q -Dtest=MaterialPackageStorageServiceTest test`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/material/config src/main/java/com/auto/ecommerce/ecommerceauto/material/service/MaterialPackageStorageService.java src/test/java/com/auto/ecommerce/ecommerceauto/material/service/MaterialPackageStorageServiceTest.java src/main/resources/application.properties
git commit -m "feat: add secure material package storage"
```

### Task 2: 素材包数据库与上传解析接口

**Files:**
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/entity/MaterialPackageEntity.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/mapper/MaterialPackageMapper.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/service/MaterialPackageService.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/dto/MaterialPackageResponse.java`
- Create: `src/test/java/com/auto/ecommerce/ecommerceauto/material/service/MaterialPackageServiceTest.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/material/controller/MaterialPackageController.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/material/model/ProductMaterialPackage.java`
- Modify: `docs/sql/e-commerce-schema.sql`

**Interfaces:**
- Consumes: Task 1 `MaterialPackageStorageService` 和现有 `MaterialPackageParser`。
- Produces: `MaterialPackageResponse upload(String originalDirectoryName, List<MultipartFile> files, List<String> relativePaths)`、`MaterialPackageEntity require(String materialPackageId)`、`Path packagePath(String materialPackageId)`。

- [ ] **Step 1: 写上传解析和失败清理测试**

```java
@Test
void uploadsParsesAndPersistsMaterialPackage() {
    when(storage.store(anyString(), anyList(), anyList())).thenReturn(packagePath);
    when(parser.parse(packagePath)).thenReturn(parsedMaterial());
    MaterialPackageResponse result = service.upload("眼镜素材", files, paths);
    assertThat(result.getMaterialPackageId()).startsWith("material-");
    verify(mapper).insert(argThat(entity -> entity.getParsedJson() != null));
}

@Test
void removesStoredDirectoryWhenParsingFails() {
    when(storage.store(anyString(), anyList(), anyList())).thenReturn(packagePath);
    when(parser.parse(packagePath)).thenThrow(new IllegalArgumentException("缺少 属性信息.txt"));
    assertThatThrownBy(() -> service.upload("错误素材", files, paths)).isInstanceOf(IllegalArgumentException.class);
    verify(storage).deletePackage(anyString());
    verifyNoInteractions(mapper);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=MaterialPackageServiceTest test`

Expected: FAIL，素材服务和实体不存在。

- [ ] **Step 3: 添加数据表与实体**

```sql
CREATE TABLE IF NOT EXISTS material_package (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_package_id VARCHAR(100) NOT NULL UNIQUE,
  original_directory_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(1000) NOT NULL,
  parsed_json JSON NOT NULL,
  file_count INT NOT NULL,
  total_size BIGINT NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);
```

实体字段与表字段一一对应，Mapper 直接继承 `BaseMapper<MaterialPackageEntity>`。

- [ ] **Step 4: 实现上传服务与 multipart Controller**

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public MaterialPackageResponse upload(
        @RequestPart String originalDirectoryName,
        @RequestPart List<MultipartFile> files,
        @RequestPart List<String> relativePaths) {
    return service.upload(originalDirectoryName, files, relativePaths);
}
```

`MaterialPackageService.upload` 生成 `material-` + UUID，调用存储、解析、设置素材 ID/目录名/文件统计，序列化 parsed JSON 并插入数据库。异常时删除正式目录。`MaterialPackageResponse` 复制解析字段，但把所有图片绝对路径转换为相对于素材包根目录的路径；内部 `parsed_json` 继续保存服务器绝对路径供 AI 和 Playwright 使用。

- [ ] **Step 5: 运行测试并提交**

Run: `./mvnw -q -Dtest=MaterialPackageServiceTest,MaterialPackageParserTest test`

Expected: PASS。

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/material src/test/java/com/auto/ecommerce/ecommerceauto/material/service docs/sql/e-commerce-schema.sql
git commit -m "feat: upload and persist material packages"
```

### Task 3: 素材图片安全预览接口

**Files:**
- Create: `src/test/java/com/auto/ecommerce/ecommerceauto/material/controller/MaterialPackageControllerTest.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/material/controller/MaterialPackageController.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/material/service/MaterialPackageService.java`

**Interfaces:**
- Consumes: Task 1 `resolveFile`、Task 2 `require`。
- Produces: `Resource file(String materialPackageId, String relativePath)` 和 `GET /api/material-packages/{id}/files?path=...`。

- [ ] **Step 1: 写文件读取与越界失败测试**

```java
mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "主图/1.jpg"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_JPEG));

mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "../../secret.txt"))
        .andExpect(status().isBadRequest());
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=MaterialPackageControllerTest test`

Expected: FAIL，文件接口返回 404。

- [ ] **Step 3: 实现受控文件响应**

```java
@GetMapping("/{materialPackageId}/files")
public ResponseEntity<Resource> file(@PathVariable String materialPackageId, @RequestParam String path) {
    MaterialFile materialFile = service.file(materialPackageId, path);
    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(materialFile.contentType()))
            .body(new FileSystemResource(materialFile.path()));
}
```

只允许读取已存在素材记录所属目录内的白名单图片；使用 `Files.probeContentType`，无法识别时按扩展名返回对应图片类型。

- [ ] **Step 4: 运行测试并提交**

Run: `./mvnw -q -Dtest=MaterialPackageControllerTest,MaterialPackageStorageServiceTest test`

Expected: PASS。

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/material src/test/java/com/auto/ecommerce/ecommerceauto/material/controller
git commit -m "feat: serve material package images"
```

### Task 4: 草稿绑定素材包 ID

**Files:**
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/dto/GenerateListingDraftRequest.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/entity/ListingDraftEntity.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/model/ListingDraft.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/service/ListingDraftService.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/service/ListingDraftGenerationWorker.java`
- Create: `src/test/java/com/auto/ecommerce/ecommerceauto/draft/service/ListingDraftServiceTest.java`
- Modify: `docs/sql/e-commerce-schema.sql`

**Interfaces:**
- Consumes: Task 2 `MaterialPackageService.require` 和 `packagePath`。
- Produces: `GenerateListingDraftRequest { Long templateId; String materialPackageId; }`，草稿实体/model 的 `materialPackageId` 字段。

- [ ] **Step 1: 写 ID 创建草稿与重复绑定失败测试**

```java
@Test
void startsGenerationFromMaterialPackageId() {
    request.setTemplateId(1L);
    request.setMaterialPackageId("material-1");
    when(materialService.require("material-1")).thenReturn(materialEntity(packagePath));
    ListingDraftResponse response = service.startGeneration(request);
    assertThat(response.getDraft().getMaterialPackageId()).isEqualTo("material-1");
    verify(worker).generate(response.getDraftId());
}

@Test
void rejectsAlreadyBoundMaterialPackage() {
    when(draftMapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.startGeneration(request)).hasMessageContaining("已生成草稿");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=ListingDraftServiceTest test`

Expected: FAIL，请求和草稿缺少 `materialPackageId`。

- [ ] **Step 3: 修改表和草稿创建链路**

```sql
ALTER TABLE listing_draft
  ADD COLUMN material_package_id VARCHAR(100) NULL,
  ADD UNIQUE INDEX uk_listing_draft_material_package_id (material_package_id);
```

`startGeneration` 对新请求只校验素材 ID，通过素材实体取得服务器路径并调用现有解析器。实体同时保存 `materialPackageId` 和服务器 `materialPackagePath`；Worker 优先使用 ID 查询路径，ID 为空时回退旧路径。

- [ ] **Step 4: 运行草稿测试和现有 AI 测试**

Run: `./mvnw -q -Dtest=ListingDraftServiceTest,ListingDraftFactoryTest,CodexDraftAiGeneratorTest test`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/draft src/test/java/com/auto/ecommerce/ecommerceauto/draft/service docs/sql/e-commerce-schema.sql
git commit -m "feat: bind drafts to material packages"
```

### Task 5: 草稿与素材联动物理删除

**Files:**
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/controller/ListingDraftController.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/service/ListingDraftService.java`
- Modify: `src/main/java/com/auto/ecommerce/ecommerceauto/material/service/MaterialPackageService.java`
- Modify: `src/test/java/com/auto/ecommerce/ecommerceauto/draft/service/ListingDraftServiceTest.java`

**Interfaces:**
- Consumes: Task 1 隔离/恢复/清理方法与 Task 2 素材 Mapper。
- Produces: `void delete(String draftId)` 和 `DELETE /api/listing-drafts/{draftId}`。

- [ ] **Step 1: 写状态限制和物理删除测试**

```java
@ParameterizedTest
@EnumSource(value = ListingDraftStatus.class, names = {"GENERATING", "PUBLISHING"})
void rejectsDeletingActiveDraft(ListingDraftStatus status) {
    when(draftMapper.selectOne(any())).thenReturn(entity(status, "material-1"));
    assertThatThrownBy(() -> service.delete("draft-1")).hasMessageContaining("不能删除");
}

@Test
void quarantinesFilesAndDeletesDraftAndMaterial() {
    when(draftMapper.selectOne(any())).thenReturn(entity(ListingDraftStatus.FAILED, "material-1"));
    service.delete("draft-1");
    verify(storage).quarantine("material-1");
    verify(draftMapper).deleteById(anyLong());
    verify(materialMapper).delete(any());
    verify(storage).purgeQuarantine("material-1");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw -q -Dtest=ListingDraftServiceTest test`

Expected: FAIL，`delete` 不存在。

- [ ] **Step 3: 实现删除编排**

```java
@DeleteMapping("/{draftId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable String draftId) {
    service.delete(draftId);
}
```

Service 在确认状态后先隔离目录，再在 `TransactionTemplate` 中删除草稿和素材记录；事务异常调用 `restore` 后重抛；提交成功后调用 `purgeQuarantine`，清理失败记录错误但不恢复业务数据。旧路径型草稿仅删除草稿记录，不允许根据任意旧绝对路径递归删除目录。

- [ ] **Step 4: 运行测试并提交**

Run: `./mvnw -q -Dtest=ListingDraftServiceTest test`

Expected: PASS。

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/draft src/main/java/com/auto/ecommerce/ecommerceauto/material src/test/java/com/auto/ecommerce/ecommerceauto/draft/service/ListingDraftServiceTest.java
git commit -m "feat: delete drafts with material packages"
```

### Task 6: 前端目录上传、预览和草稿删除

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/pages/MaterialPage.tsx`
- Modify: `frontend/src/components/ImagePathPreview.tsx`
- Modify: `frontend/src/pages/DraftReviewPage.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: Task 2 multipart 上传、Task 3 图片接口、Task 4 ID 生成请求、Task 5 DELETE 接口。
- Produces: `uploadMaterialPackage(selection, onProgress)`、`deleteListingDraft(draftId)` 和目录选择 UI。

- [ ] **Step 1: 修改前端类型和 API**

```ts
export interface SelectedMaterialFile {
  file: File;
  relativePath: string;
}

export function uploadMaterialPackage(
  directoryName: string,
  files: SelectedMaterialFile[],
  onProgress: (percent: number) => void,
): Promise<ProductMaterialPackage> {
  const form = new FormData();
  form.append('originalDirectoryName', directoryName);
  files.forEach(({ file, relativePath }) => {
    form.append('files', file);
    form.append('relativePaths', relativePath);
  });
  return multipartRequest('/api/material-packages', form, onProgress);
}
```

`multipartRequest` 使用 `XMLHttpRequest`，不要设置 `Content-Type`，让浏览器生成 boundary。`generateListingDraft` 参数改为 `(templateId, materialPackageId)`；新增 `deleteListingDraft`。

- [ ] **Step 2: 实现目录选择和 50MB 前端校验**

```tsx
<input
  ref={directoryInputRef}
  type="file"
  multiple
  hidden
  {...({ webkitdirectory: '' } as React.InputHTMLAttributes<HTMLInputElement>)}
  onChange={(event) => selectDirectory(Array.from(event.target.files ?? []))}
/>
```

`selectDirectory` 从 `file.webkitRelativePath` 取得相对路径，移除顶层目录名后只保留约定白名单；累计大小超过 `50 * 1024 * 1024` 时清空选择并提示。页面展示目录名、文件数、MB 大小和 Ant Design `Progress`；按钮文案为“上传并解析”。

- [ ] **Step 3: 改图片预览与 AI 生成**

后端响应中的素材图片字段改为相对路径；`ImagePathPreview` 新增 `materialPackageId`，图片 URL 使用：

```ts
export function materialImageUrl(materialPackageId: string, relativePath: string) {
  return `/api/material-packages/${encodeURIComponent(materialPackageId)}/files?path=${encodeURIComponent(relativePath)}`;
}
```

AI 生成按钮使用 `material.materialPackageId`，不再检查或发送 `materialPackagePath`。

- [ ] **Step 4: 增加草稿删除交互**

列表和详情按钮均使用：

```tsx
<Popconfirm
  title="确认删除草稿？"
  description="将同时永久删除服务器上的素材包，此操作不可恢复。"
  onConfirm={() => deleteDraft(record.draftId)}
>
  <Button danger icon={<DeleteOutlined />}>删除</Button>
</Popconfirm>
```

`GENERATING`、`PUBLISHING` 时禁用删除。成功后刷新当前分页；删除当前详情时关闭详情。

- [ ] **Step 5: 构建验证并提交**

Run: `cd frontend && npm run build`

Expected: TypeScript 与 Vite 构建成功。

```bash
git add frontend/src
git commit -m "feat: upload local material directories"
```

### Task 7: 集成验证与数据库迁移说明

**Files:**
- Modify: `README.md`
- Modify: `docs/sql/e-commerce-schema.sql`

**Interfaces:**
- Consumes: Tasks 1-6 的完整流程。
- Produces: 可复制执行的升级 SQL、服务器存储配置与局域网验证步骤。

- [ ] **Step 1: 补充运行配置和升级 SQL**

README 记录：

```bash
export MATERIAL_STORAGE_ROOT=/absolute/server/path/material-packages
```

并明确执行 `material_package` 建表及 `listing_draft.material_package_id` 升级 SQL，服务器进程必须对存储根目录有读写权限。

- [ ] **Step 2: 运行后端全量测试**

Run: `./mvnw test`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 运行前端构建**

Run: `cd frontend && npm run build`

Expected: `vite build` 成功。

- [ ] **Step 4: 手工局域网验收**

从另一台电脑访问前端，验证：

- 选择包含四类图片目录和 `属性信息.txt` 的本机目录。
- 上传进度到 100%，解析字段和图片均正常。
- AI 生成后草稿绑定素材 ID，服务器重启后仍可预览和上架。
- 删除草稿后列表记录、`material_package` 记录和服务器目录都消失。

- [ ] **Step 5: 提交文档**

```bash
git add README.md docs/sql/e-commerce-schema.sql
git commit -m "docs: document material upload deployment"
```
