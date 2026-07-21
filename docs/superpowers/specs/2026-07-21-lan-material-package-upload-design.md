# 局域网素材包上传与生命周期设计

## 背景与目标

当前素材解析依赖用户输入素材包绝对路径，前后端运行在同一台电脑时可用，但局域网用户浏览器中的本机路径无法被服务器访问。

本次改造目标：

- 用户在浏览器中选择自己电脑上的素材包目录。
- 浏览器将有效素材一次上传到服务器，并保留约定目录结构。
- 服务器生成唯一 `materialPackageId`，保存、解析素材，并与草稿绑定。
- AI 生成、图片预览和上架都读取服务器上的素材文件。
- 草稿审核支持物理删除草稿，并同步清除对应素材包。
- 单个素材包总大小不超过 50MB。

## 范围

本次包含：

- 素材目录选择、上传进度与服务端解析。
- 素材包数据库记录及唯一 ID。
- 草稿与素材包 ID 绑定。
- 基于素材包 ID 的图片预览、AI 生成和发布读取。
- 草稿物理删除及素材目录清理。
- 现有路径型草稿的兼容读取。

本次不包含：

- 浏览器端 ZIP 压缩。
- 素材包版本管理、共享或独立素材库页面。
- 定时清理未绑定素材包。
- 分片上传和断点续传。50MB 限制下使用单次 multipart 请求即可。

## 用户流程

1. 用户在“素材解析”页面选择 SOP 模板。
2. 用户点击“选择素材包”，浏览器打开本机目录选择器。
3. 前端筛选有效文件，展示目录名、文件数量和总大小。
4. 用户点击“上传并解析”。前端一次提交 multipart 请求并展示上传进度。
5. 后端生成 `materialPackageId`，校验并保存素材，调用现有解析器。
6. 前端展示解析信息与服务器图片预览。
7. 用户点击“AI 生成”，请求仅携带 `templateId` 和 `materialPackageId`。
8. 草稿生成后持续绑定该素材包，后续上架从服务器路径读取文件。
9. 用户在草稿列表或详情点击“删除”，二次确认后物理删除草稿、素材记录和素材目录。

## 素材包内容

只上传以下有效内容：

```text
素材包/
├── 属性信息.txt
├── 主图/
│   └── *.{jpg,jpeg,png}
├── 副图/
│   └── *.{jpg,jpeg,png}
├── 尺码表/
│   └── *.{jpg,jpeg,png}
└── 包装图/
    └── *.{jpg,jpeg,png}
```

忽略 `.DS_Store`、隐藏文件、其他文档和客户端已有的 `AI生成/`。服务器上的 AI 结果仍写入对应素材包目录下的 `AI生成/`。

前端筛选用于改善体验，后端必须重复执行白名单和大小校验，不能信任客户端结果。

## 上传协议

接口：

```http
POST /api/material-packages
Content-Type: multipart/form-data
```

请求字段：

- `files`：所选有效文件列表。
- `relativePaths`：与 `files` 顺序一一对应的相对路径列表。
- `originalDirectoryName`：用户选择的顶层目录名。

响应为解析后的素材对象，并新增：

```json
{
  "materialPackageId": "material-<uuid>",
  "originalDirectoryName": "商品素材",
  "fileCount": 12,
  "totalSize": 10485760
}
```

前端使用 `XMLHttpRequest` 发送 FormData，以获得原生上传进度事件，不新增上传依赖。

AI 生成接口调整为：

```json
{
  "templateId": 1,
  "materialPackageId": "material-<uuid>"
}
```

新流程不再接受客户端或服务器绝对路径。

## 服务端存储

正式目录：

```text
${MATERIAL_STORAGE_ROOT:./data/material-packages}/{materialPackageId}/
```

上传时先写入同一存储根目录内的临时目录：

```text
${MATERIAL_STORAGE_ROOT}/.tmp/{materialPackageId}/
```

处理顺序：

1. 生成服务端 UUID。
2. 校验文件数、相对路径、扩展名、单文件和累计大小。
3. 将文件写入临时目录。
4. 校验必需目录和 `属性信息.txt`，调用现有 `MaterialPackageParser`。
5. 原子移动到正式目录。
6. 用正式路径重新解析并写入数据库。
7. 返回解析结果。

任一步失败都删除临时目录，不创建素材数据库记录。

## 安全校验

- 累计上传大小上限为 50MB，Spring multipart 请求上限略高于业务上限以便返回业务错误。
- 相对路径必须是相对路径，规范化后不能包含 `..`，解析后的目标必须仍位于当前素材包目录内。
- 只允许 `属性信息.txt` 和约定四个目录内的 `jpg/jpeg/png` 文件。
- 不允许同一相对路径重复上传。
- 不使用原始目录名构造服务器路径；服务器目录只使用生成的 ID。
- 图片下载接口只能通过 `materialPackageId + 受控相对路径` 访问，不接受绝对路径。

## 数据模型

新增表：

```sql
CREATE TABLE material_package (
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

`listing_draft` 新增：

```sql
ALTER TABLE listing_draft
  ADD COLUMN material_package_id VARCHAR(100) NULL,
  ADD UNIQUE INDEX uk_listing_draft_material_package_id (material_package_id);
```

第一版不增加数据库外键，避免 MySQL 外键与磁盘删除组成难以恢复的跨资源事务；服务层负责绑定校验和删除顺序。

`ProductMaterialPackage` 新增 `materialPackageId`、原始目录名、文件数量和总大小。其内部图片字段仍保存服务器绝对路径，供后端 AI 和 Playwright 使用；前端类型不直接展示这些绝对路径，而是通过图片预览 URL 获取内容。

旧数据继续使用 `material_package_path`。新上传流程同时将服务器路径写入该字段，保持现有 AI 和发布代码平稳迁移；所有新接口和前端状态只使用 `materialPackageId`。

## 后端组件

- `MaterialPackageStorageService`：生成 ID、校验相对路径、写入临时目录、原子移动和删除目录。
- `MaterialPackageService`：协调上传、解析、数据库写入和素材查询。
- `MaterialPackageController`：提供上传和图片读取接口。
- `ListingDraftService`：通过素材 ID 创建草稿，并提供联动删除。
- `ListingDraftGenerationWorker`：通过素材记录取得服务器目录后重新解析。

继续复用 `MaterialPackageParser`、`AttributeInfoTextParser` 和当前草稿工厂，不建立额外解析抽象。

## 图片预览

接口：

```http
GET /api/material-packages/{materialPackageId}/files?path=主图/1.jpg
```

后端校验 ID 和相对路径后返回文件流。前端收到解析结果时，将图片相对路径映射为该接口 URL。不得把服务器绝对路径暴露给局域网客户端。

## 草稿删除

接口：

```http
DELETE /api/listing-drafts/{draftId}
```

删除规则：

- `GENERATING` 和 `PUBLISHING` 状态禁止删除，避免异步任务在删除后继续写回。
- 其他状态均允许删除。
- 草稿审核列表和详情均显示“删除”按钮，并要求二次确认。
- 一个素材包第一版只绑定一个草稿；创建草稿时校验素材包未被其他草稿占用。

删除顺序：

1. 查询并锁定草稿和素材记录。
2. 将素材目录原子移动到存储根目录内的 `.trash/{materialPackageId}` 隔离区。
3. 在数据库事务中物理删除草稿记录和素材记录。
4. 数据库提交成功后递归删除隔离目录；数据库回滚时将目录移回原位。

移动到隔离区失败时不删除数据库记录，并返回明确错误，用户可重试。数据库提交后若隔离目录清理失败，草稿对用户仍视为已删除，同时记录错误；该目录不再可被业务访问，后续启动时或下一次清理时重试。第一版不引入跨资源事务框架。

## 前端改造

素材解析页：

- 删除路径输入框和 macOS 服务端目录选择接口调用。
- 使用隐藏的目录文件输入框，由“选择素材包”按钮触发。
- 展示目录名、有效文件数量、总大小和上传进度。
- 超过 50MB 或缺少 `属性信息.txt` 时在上传前提示。
- 上传成功后保存 `materialPackageId` 和解析结果。
- AI 生成按钮只提交模板 ID 与素材包 ID。

草稿审核页：

- 列表操作和详情操作新增删除按钮。
- 删除前显示草稿标题和“将同时删除服务器素材”的确认文案。
- 删除成功后刷新分页列表；若当前详情被删除，则关闭详情。

## 错误处理

- 上传超限：返回“素材包不能超过 50MB”。
- 路径非法或文件类型不支持：返回具体相对路径。
- 素材格式错误：保留现有中文解析错误，并清理临时文件。
- 素材 ID 不存在：返回 404。
- 素材已绑定草稿：拒绝重复生成。
- 草稿正在生成或上架：返回 409，前端展示状态冲突提示。
- 删除磁盘失败：保留数据库数据并返回失败，不显示删除成功。

## 测试与验收

后端自动化测试：

- 正常目录上传后保留目录结构、生成唯一 ID 并解析成功。
- 拒绝超过 50MB、非法扩展名、绝对路径、`..` 路径和重复路径。
- 上传或解析失败会清理临时目录且不写数据库。
- 素材 ID 能创建草稿，异步生成能读取服务器素材。
- 图片接口只能读取当前素材包目录内文件。
- 删除草稿会物理删除草稿、素材记录和磁盘目录。
- `GENERATING`、`PUBLISHING` 草稿拒绝删除。
- 旧路径型草稿仍可查询和上架。

前端验证：

- 局域网另一台电脑可以选择本机目录并上传。
- 上传进度、50MB 限制和错误提示正确。
- 解析后的主图、副图、尺码表和包装图均能预览。
- AI 生成和上架不依赖客户端路径。
- 草稿列表和详情删除操作一致，删除后数据和素材均不可访问。
