# 资质合规与产品包装图设计

## 目标

从素材包的 `属性信息.txt` 读取制造商和欧盟责任人，并读取 `包装图/` 目录，将数据随草稿保存，发布时填入马帮资质合规下拉框并上传包装图。

## 素材约定

```text
素材包/
├── 主图/
├── 副图/
├── 尺码表/
├── 包装图/
└── 属性信息.txt
```

属性文件新增：

```text
[资质合规]
制造商=制造商名称
欧盟责任人=欧盟责任人名称
```

该分段为必需分段，两个字段必须有值；包装图目录也按现有图片目录规则处理，缺失或为空时解析失败。

## 数据流

`AttributeInfoTextParser` -> `ProductMaterialPackage` -> `ListingDraftFactory` -> `ListingDraft` JSON -> `ListingDraftToTikTokPublishRequestMapper` -> `TikTokPublishRequest` -> `MabangPublisher.publish`。

草稿 JSON 已经是数据库存储边界，因此不增加数据库列；新增字段会随草稿 JSON 一起持久化。

## 页面自动化

资质合规使用已确认的 placeholder：`请选择制造商` 和 `请选择欧盟责任人`。

包装图采用“包装图”文本所在表单区域内的 `input[type=file]` 定位，避免依赖动态 id；上传前检查目标控件存在，上传后记录文件数量。若页面因类目或网络状态未渲染包装图区域，发布失败并输出清晰错误，不静默跳过。

## 验证

- 解析器测试覆盖资质字段和包装图排序。
- 草稿工厂测试覆盖字段传递。
- 发布请求映射测试覆盖资质和包装图。
- Maven 全量测试通过。
