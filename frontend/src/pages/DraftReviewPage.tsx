import { Alert, Card, Descriptions, Empty, Form, Input, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo } from 'react';
import type {
  ListingDraftPreview,
  ListingDraftTransactionRow,
  ProductMaterialPackage,
  SopTemplate,
} from '../api/types';
import { ImagePathPreview } from '../components/ImagePathPreview';

interface DraftReviewPageProps {
  template: SopTemplate | null;
  material: ProductMaterialPackage | null;
}

function splitVariantValue(value: string): string[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function createDraft(material: ProductMaterialPackage): ListingDraftPreview {
  return {
    shopName: material.shopName,
    categoryName: material.categoryName,
    sourceUrl: material.sourceUrl,
    chineseTitle: material.productName,
    englishTitle: '',
    brand: material.brand,
    productMainImage: material.mainImageSourcePaths[0],
    productSizeChartImage: material.sizeChartImagePath,
    descriptionImagePaths: material.detailImagePaths,
    categoryAttributes: material.categoryAttributes,
    variantAttributes: Object.fromEntries(
      Object.entries(material.variantAttributes).map(([key, value]) => [key, splitVariantValue(value)]),
    ),
    transactionInfo: material.transactionRows.map((row) => ({
      color: row.color,
      size: row.specification,
      stockingMode: row.stockingMode,
      skc: row.skc,
      sku: row.sku,
      price: row.price,
      stock: row.stock,
      length: row.length,
      width: row.width,
      height: row.height,
      weightGram: row.weightGram,
      enabled: true,
    })),
  };
}

function validateDraft(draft: ListingDraftPreview): string[] {
  const errors: string[] = [];
  if (!draft.chineseTitle) errors.push('缺少中文标题');
  if (!draft.productMainImage) errors.push('缺少产品主图');
  if (draft.descriptionImagePaths.length === 0) errors.push('缺少描述图');
  if (draft.transactionInfo.length === 0) errors.push('缺少交易信息');
  return errors;
}

function renderAttributes(attributes: Record<string, string | string[]>) {
  const entries = Object.entries(attributes);
  if (entries.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无属性" />;
  }

  return (
    <Descriptions bordered size="small" column={1}>
      {entries.map(([key, value]) => (
        <Descriptions.Item key={key} label={key}>
          {Array.isArray(value) ? value.join('、') : value || '-'}
        </Descriptions.Item>
      ))}
    </Descriptions>
  );
}

const transactionColumns: ColumnsType<ListingDraftTransactionRow> = [
  { title: '颜色', dataIndex: 'color' },
  { title: '规格', dataIndex: 'size' },
  { title: 'SKU', dataIndex: 'sku' },
  { title: '价格', dataIndex: 'price' },
  { title: '库存', dataIndex: 'stock' },
  { title: '重量g', dataIndex: 'weightGram' },
];

export function DraftReviewPage({ template, material }: DraftReviewPageProps) {
  const draft = useMemo(() => (material ? createDraft(material) : null), [material]);
  const errors = draft ? validateDraft(draft) : [];

  if (!draft) {
    return <Empty description="请先在素材解析页解析一个素材包" />;
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        showIcon
        type={errors.length > 0 ? 'warning' : 'success'}
        message={errors.length > 0 ? '草稿还需要补充信息' : '草稿基础信息完整'}
        description={errors.length > 0 ? errors.join('；') : '当前草稿可进入后续 AI 生成和人工审核流程。'}
      />

      <Card title="模板快照">
        {template ? (
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="模板 ID">{template.id}</Descriptions.Item>
            <Descriptions.Item label="模板名称">{template.name}</Descriptions.Item>
            <Descriptions.Item label="标题提示词" span={2}>
              {template.titlePrompt}
            </Descriptions.Item>
            <Descriptions.Item label="主图提示词" span={2}>
              {template.mainImagePrompt}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <Typography.Text type="secondary">未选择模板，当前仅展示素材草稿壳。</Typography.Text>
        )}
      </Card>

      <Card title="基础信息">
        <Form key={material?.materialPackagePath || material?.productName} layout="vertical" initialValues={draft}>
          <Space style={{ width: '100%' }} size="middle" align="start">
            <Form.Item name="shopName" label="店铺" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="categoryName" label="类目" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
            <Form.Item name="brand" label="品牌" style={{ flex: 1 }}>
              <Input />
            </Form.Item>
          </Space>
          <Form.Item name="sourceUrl" label="来源 URL">
            <Input />
          </Form.Item>
          <Form.Item name="chineseTitle" label="中文标题">
            <Input />
          </Form.Item>
          <Form.Item name="englishTitle" label="英文标题">
            <Input placeholder="后续由 CodexExec 生成" />
          </Form.Item>
        </Form>
      </Card>

      <Card title="图片审核">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Title level={5}>产品主图</Typography.Title>
          <ImagePathPreview paths={draft.productMainImage ? [draft.productMainImage] : []} />
          <Typography.Title level={5}>尺码表</Typography.Title>
          <ImagePathPreview paths={draft.productSizeChartImage ? [draft.productSizeChartImage] : []} />
          <Typography.Title level={5}>描述图</Typography.Title>
          <ImagePathPreview paths={draft.descriptionImagePaths} />
        </Space>
      </Card>

      <Card title="属性">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Title level={5}>分类属性</Typography.Title>
          {renderAttributes(draft.categoryAttributes)}
          <Typography.Title level={5}>变种属性</Typography.Title>
          {renderAttributes(draft.variantAttributes)}
        </Space>
      </Card>

      <Card title="交易信息">
        <Table
          rowKey={(row) => row.sku || `${row.color}-${row.size}`}
          columns={transactionColumns}
          dataSource={draft.transactionInfo}
          pagination={false}
          scroll={{ x: true }}
        />
      </Card>
    </Space>
  );
}

export default DraftReviewPage;
