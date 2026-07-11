import { useEffect, useState } from 'react';
import { Button, Descriptions, Empty, Form, Input, Select, Space, Table, Tabs, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { chooseLocalDirectory, generateListingDraft, listTemplates, parseMaterialPackage } from '../api/client';
import type { MaterialTransactionRow, ProductMaterialPackage, SopTemplate } from '../api/types';
import { ImagePathPreview } from '../components/ImagePathPreview';

interface MaterialPageProps {
  material: ProductMaterialPackage | null;
  selectedTemplate: SopTemplate | null;
  onTemplateSelect: (template: SopTemplate | null) => void;
  onMaterialParsed: (material: ProductMaterialPackage) => void;
  onDraftStarted: (draftId: string) => void;
}

interface ParseFormValues {
  materialPackagePath: string;
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

const transactionColumns: ColumnsType<MaterialTransactionRow> = [
  {
    title: '颜色',
    dataIndex: 'color',
    key: 'color',
    render: (value: string) => value || '-',
  },
  {
    title: '商品货号(SKC)',
    dataIndex: 'skc',
    key: 'skc',
    render: (value: string) => value || '-',
  },
  {
    title: '备货模式',
    dataIndex: 'stockingMode',
    key: 'stockingMode',
    render: (value: string) => value || '-',
  },
  {
    title: '尺码',
    dataIndex: 'specification',
    key: 'specification',
    render: (value: string) => value || '-',
  },
  {
    title: 'SKU货号',
    dataIndex: 'sku',
    key: 'sku',
    render: (value: string) => value || '-',
  },
  {
    title: '不含税价(CNY)',
    dataIndex: 'price',
    key: 'price',
  },
  {
    title: '库存',
    dataIndex: 'stock',
    key: 'stock',
  },
  {
    title: '长',
    dataIndex: 'length',
    key: 'length',
  },
  {
    title: '宽',
    dataIndex: 'width',
    key: 'width',
  },
  {
    title: '高',
    dataIndex: 'height',
    key: 'height',
  },
  {
    title: '重量(g)',
    dataIndex: 'weightGram',
    key: 'weightGram',
  },
];

export function MaterialPage({
  material,
  selectedTemplate,
  onTemplateSelect,
  onMaterialParsed,
  onDraftStarted,
}: MaterialPageProps) {
  const [templates, setTemplates] = useState<SopTemplate[]>([]);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [choosingDirectory, setChoosingDirectory] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [form] = Form.useForm<ParseFormValues>();

  useEffect(() => {
    setTemplateLoading(true);
    listTemplates()
      .then(setTemplates)
      .catch((error) => message.error(error instanceof Error ? error.message : '模板列表加载失败'))
      .finally(() => setTemplateLoading(false));
  }, []);

  const chooseDirectory = async () => {
    setChoosingDirectory(true);
    try {
      const { path } = await chooseLocalDirectory();
      form.setFieldValue('materialPackagePath', path);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '选择目录失败');
    } finally {
      setChoosingDirectory(false);
    }
  };

  const parseMaterial = async (values: ParseFormValues) => {
    setParsing(true);
    try {
      const parsed = await parseMaterialPackage(values.materialPackagePath.trim());
      onMaterialParsed(parsed);
      message.success('素材包解析完成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '素材包解析失败');
    } finally {
      setParsing(false);
    }
  };

  const startAiGeneration = async () => {
    if (!selectedTemplate || !material?.materialPackagePath) {
      message.warning('请先选择 SOP 模板并解析素材包');
      return;
    }
    setGenerating(true);
    try {
      const draft = await generateListingDraft(selectedTemplate.id, material.materialPackagePath);
      message.success('AI 生成任务已开始');
      onDraftStarted(draft.draftId);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'AI 生成任务启动失败');
    } finally {
      setGenerating(false);
    }
  };

  const sizeChartPaths = material?.sizeChartImagePaths?.length
    ? material.sizeChartImagePaths
    : material?.sizeChartImagePath
      ? [material.sizeChartImagePath]
      : [];

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

      <Form form={form} layout="inline" onFinish={parseMaterial}>
        <Form.Item
          name="materialPackagePath"
          rules={[{ required: true, whitespace: true, message: '请选择或输入素材包目录' }]}
          style={{ flex: 1 }}
        >
          <Input placeholder="素材包目录路径" />
        </Form.Item>
        <Form.Item>
          <Button loading={choosingDirectory} onClick={chooseDirectory}>
            选择目录
          </Button>
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={parsing}>
            解析素材包
          </Button>
        </Form.Item>
      </Form>

      {!material ? (
        <Typography.Text type="secondary">请先选择 SOP 模板，再选择或输入本地素材包目录并解析。</Typography.Text>
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
            <Descriptions.Item label="制造商">{material.manufacturer || '-'}</Descriptions.Item>
            <Descriptions.Item label="欧盟责任人">{material.euResponsiblePerson || '-'}</Descriptions.Item>
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
                    <Typography.Title level={5}>产品包装图</Typography.Title>
                    <ImagePathPreview paths={material.packageImagePaths ?? []} emptyText="暂无产品包装图" />
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

          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button type="primary" size="large" loading={generating} disabled={!selectedTemplate} onClick={startAiGeneration}>
              AI生成
            </Button>
          </Space>
        </>
      )}
    </Space>
  );
}

export default MaterialPage;
