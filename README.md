# E-commerce Auto

## 素材包部署

在启动后端服务前，为服务器上的素材包目录设置绝对路径：

```bash
export MATERIAL_STORAGE_ROOT=/absolute/server/path/material-packages
```

运行服务的操作系统账户必须对该目录及其子目录拥有读取、写入、创建、重命名和删除权限。服务会在此根目录下创建素材包目录、`.tmp` 临时目录和 `.trash` 隔离目录；生产环境应将目录所有者设置为服务账户，并避免将其作为静态 Web 根目录暴露。

单个素材包总大小最多为 50MB。后端将单文件限制为 50MB，并将 multipart 请求限制为 55MB；前端也会在上传前校验 50MB 上限。

### 已有数据库升级

已有部署启动新版本前，先在目标数据库中执行完整的[数据库脚本](docs/sql/e-commerce-schema.sql)。其中的 `CREATE TABLE IF NOT EXISTS material_package` 可重复执行；脚本仅在 `listing_draft.material_package_id` 列及其唯一索引尚不存在时执行 `ALTER TABLE`，因此不要另行执行旧版的无条件 `ALTER TABLE` 语句。

### 局域网验收

1. 从另一台局域网电脑访问前端，选择本机包含 `主图/`、`副图/`、`尺码表/`、`包装图/` 和 `属性信息.txt` 的素材目录。
2. 上传进度应到达 100%，并确认解析出的属性字段和四类图片预览均正常。
3. 使用该素材包生成 AI 草稿，确认草稿已绑定素材 ID；重启服务器后，草稿仍可预览并可上架。
4. 删除该草稿，确认草稿列表记录、`material_package` 数据库记录和服务器上的素材包目录均已消失。
