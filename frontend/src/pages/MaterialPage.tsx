import { useEffect, useState } from 'react';
import { Button, Descriptions, Empty, Select, Space, Table, Tabs, Typography, Upload, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload/interface';
import { listTemplates, parseMaterialPackageFiles } from '../api/client';
import type { MaterialTransactionRow, ProductMaterialPackage, SopTemplate } from '../api/types';
import { ImagePathPreview } from '../components/ImagePathPreview';

interface MaterialPageProps {
  material: ProductMaterialPackage | null;
  selectedTemplate: SopTemplate | null;
  onTemplateSelect: (template: SopTemplate | null) => void;
  onMaterialParsed: (material: ProductMaterialPackage) => void;
  onReviewDraft: () => void;
}

function entriesOf(attributes: Record<string, string>) {
  return Object.entries(attributes).filter(([key]) => key);
}

function renderAttributes(attributes: Record<string, string>, emptyText: string) {
  const entries = entriesOf(attributes);

  if (entries.length === 0) {
    return <Empty description={emptyText} />;
  }

  return (
    <Descriptions bordered size="small" column={1}>
      {entries.map(([label, value]) => (
        <Descriptions.Item key={label} label={label}>
          {value || '-'}
        </Descriptions.Item>
      ))}
    </Descriptions>
  );
}

function formatSize(row: MaterialTransactionRow): string {
  const values = [row.length, row.width, row.height].filter((value) => value !== undefined && value !== null);
  return values.length > 0 ? values.join(' x ') : '-';
}

const transactionColumns: ColumnsType<MaterialTransactionRow> = [
  {
    title: '颜色',
    dataIndex: 'color',
    key: 'color',
    render: (value: string) => value || '-',
  },
  {
    title: '规格',
    dataIndex: 'specification',
    key: 'specification',
    render: (value: string) => value || '-',
  },
  {
    title: 'SKU',
    dataIndex: 'sku',
    key: 'sku',
    render: (value: string) => value || '-',
  },
  {
    title: '价格',
    dataIndex: 'price',
    key: 'price',
  },
  {
    title: '库存',
    dataIndex: 'stock',
    key: 'stock',
  },
  {
    title: '尺寸',
    key: 'size',
    render: (_, row) => formatSize(row),
  },
  {
    title: '重量g',
    dataIndex: 'weightGram',
    key: 'weightGram',
  },
];

const { Dragger } = Upload;

export function MaterialPage({
  material,
  selectedTemplate,
  onTemplateSelect,
  onMaterialParsed,
  onReviewDraft,
}: MaterialPageProps) {
  const [templates, setTemplates] = useState<SopTemplate[]>([]);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [parsing, setParsing] = useState(false);

  useEffect(() => {
    setTemplateLoading(true);
    listTemplates()
      .then(setTemplates)
      .catch((error) => message.error(error instanceof Error ? error.message : '模板列表加载失败'))
      .finally(() => setTemplateLoading(false));
  }, []);

  const parseMaterial = async () => {
    const files = fileList
      .map((file) => file.originFileObj)
      .filter(Boolean)
      .map((file) => file as File);
    if (files.length === 0) {
      message.warning('请先选择素材包目录');
      return;
    }

    setParsing(true);
    try {
      const parsed = await parseMaterialPackageFiles(files);
      onMaterialParsed(parsed);
      message.success('素材包解析完成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '素材包解析失败');
    } finally {
      setParsing(false);
    }
  };

  const sizeChartPaths = material?.sizeChartImagePath ? [material.sizeChartImagePath] : [];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <Typography.Text type="secondary">上架模板</Typography.Text>
        <Select
          allowClear
          showSearch
          loading={templateLoading}
          placeholder="请选择 SOP 模板"
          value={selectedTemplate?.id}
          optionFilterProp="label"
          style={{ minWidth: 320 }}
          options={templates.map((template) => ({
            value: template.id,
            label: template.name,
          }))}
          onChange={(id) => onTemplateSelect(templates.find((template) => template.id === id) ?? null)}
        />
      </Space>

      <Dragger
        directory
        multiple
        fileList={fileList}
        beforeUpload={() => false}
        onChange={({ fileList: nextFileList }) => setFileList(nextFileList)}
      >
        <Typography.Text>点击或拖入素材包目录</Typography.Text>
        <br />
        <Typography.Text type="secondary">目录内需要包含 主图 / 副图 / 尺码表 / 属性信息.txt</Typography.Text>
      </Dragger>

      <Space>
        <Button type="primary" loading={parsing} disabled={fileList.length === 0} onClick={parseMaterial}>
          解析素材包
        </Button>
        <Button onClick={onReviewDraft} disabled={!material || !selectedTemplate}>
          进入草稿审核
        </Button>
      </Space>

      {!material ? (
        <Typography.Text type="secondary">请先选择 SOP 模板，再选择本地素材包目录并解析。</Typography.Text>
      ) : (
        <>
          <Descriptions bordered column={2} size="small" title="产品信息">
            <Descriptions.Item label="SOP 模板" span={2}>
              {selectedTemplate?.name ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="产品名称">{material.productName || '-'}</Descriptions.Item>
            <Descriptions.Item label="店铺">{material.shopName || '-'}</Descriptions.Item>
            <Descriptions.Item label="类目">{material.categoryName || '-'}</Descriptions.Item>
            <Descriptions.Item label="品牌">{material.brand || '-'}</Descriptions.Item>
            <Descriptions.Item label="来源 URL" span={2}>
              {material.sourceUrl ? (
                <Typography.Link href={material.sourceUrl} target="_blank" rel="noreferrer">
                  {material.sourceUrl}
                </Typography.Link>
              ) : (
                '-'
              )}
            </Descriptions.Item>
          </Descriptions>

          <Tabs
            items={[
              {
                key: 'images',
                label: '图片',
                children: (
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Typography.Title level={5}>主图来源</Typography.Title>
                    <ImagePathPreview paths={material.mainImageSourcePaths} emptyText="暂无主图来源" />
                    <Typography.Title level={5}>副图/描述图</Typography.Title>
                    <ImagePathPreview paths={material.detailImagePaths} emptyText="暂无副图/描述图" />
                    <Typography.Title level={5}>尺码表</Typography.Title>
                    <ImagePathPreview paths={sizeChartPaths} emptyText="暂无尺码表" />
                  </Space>
                ),
              },
              {
                key: 'attributes',
                label: '属性',
                children: (
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Typography.Title level={5}>分类属性</Typography.Title>
                    {renderAttributes(material.categoryAttributes, '暂无分类属性')}
                    <Typography.Title level={5}>变种属性</Typography.Title>
                    {renderAttributes(material.variantAttributes, '暂无变种属性')}
                  </Space>
                ),
              },
              {
                key: 'transactions',
                label: '交易信息',
                children: (
                  <Table
                    rowKey={(row, index) => row.sku || row.skc || String(index)}
                    columns={transactionColumns}
                    dataSource={material.transactionRows}
                    pagination={false}
                    scroll={{ x: true }}
                  />
                ),
              },
            ]}
          />
        </>
      )}
    </Space>
  );
}

export default MaterialPage;
