import { useEffect, useRef, useState, type InputHTMLAttributes } from 'react';
import { Button, Descriptions, Empty, Progress, Select, Space, Table, Tabs, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { generateListingDraft, listTemplates, uploadMaterialPackage, type SelectedMaterialFile } from '../api/client';
import type { MaterialTransactionRow, ProductMaterialPackage, SopTemplate } from '../api/types';
import { ImagePathPreview } from '../components/ImagePathPreview';

interface MaterialPageProps {
  material: ProductMaterialPackage | null;
  selectedTemplate: SopTemplate | null;
  onTemplateSelect: (template: SopTemplate | null) => void;
  onMaterialParsed: (material: ProductMaterialPackage) => void;
  onDraftStarted: (draftId: string) => void;
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

const MAX_UPLOAD_SIZE = 50 * 1024 * 1024;
const IMAGE_DIRECTORIES = new Set(['主图', '副图', '尺码表', '包装图']);

function directoryNameOf(file: File): string {
  return file.webkitRelativePath.split('/')[0] || '素材包';
}

function allowedRelativePath(file: File): string | null {
  const segments = file.webkitRelativePath.split('/').filter(Boolean);
  const relativePath = segments.slice(1);

  if (segments.length < 2 || segments.some((segment) => segment.startsWith('.'))) return null;
  if (relativePath.length === 1 && relativePath[0] === '属性信息.txt') return relativePath[0];
  if (relativePath.length !== 2 || !IMAGE_DIRECTORIES.has(relativePath[0])) return null;

  return /\.(jpe?g|png)$/i.test(relativePath[1]) ? relativePath.join('/') : null;
}

function formattedSize(size: number): string {
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function MaterialPage({
  material,
  selectedTemplate,
  onTemplateSelect,
  onMaterialParsed,
  onDraftStarted,
}: MaterialPageProps) {
  const [templates, setTemplates] = useState<SopTemplate[]>([]);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState<SelectedMaterialFile[]>([]);
  const [directoryName, setDirectoryName] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const directoryInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setTemplateLoading(true);
    listTemplates()
      .then(setTemplates)
      .catch((error) => message.error(error instanceof Error ? error.message : '模板列表加载失败'))
      .finally(() => setTemplateLoading(false));
  }, []);

  const selectDirectory = (files: File[]) => {
    const filesToUpload = files.flatMap((file): SelectedMaterialFile[] => {
      const relativePath = allowedRelativePath(file);
      return relativePath ? [{ file, relativePath }] : [];
    });
    const totalSize = filesToUpload.reduce((sum, { file }) => sum + file.size, 0);

    if (directoryInputRef.current) directoryInputRef.current.value = '';
    setUploadProgress(0);
    if (totalSize > MAX_UPLOAD_SIZE) {
      setSelectedFiles([]);
      setDirectoryName(null);
      message.error('素材包不能超过 50MB');
      return;
    }
    if (filesToUpload.length === 0) {
      setSelectedFiles([]);
      setDirectoryName(null);
      message.warning('未找到可上传的属性信息或素材图片');
      return;
    }

    setSelectedFiles(filesToUpload);
    setDirectoryName(directoryNameOf(files[0]));
  };

  const uploadMaterial = async () => {
    if (!directoryName || selectedFiles.length === 0) return;
    setUploading(true);
    try {
      const parsed = await uploadMaterialPackage(directoryName, selectedFiles, setUploadProgress);
      onMaterialParsed(parsed);
      setUploadProgress(100);
      message.success('素材包上传并解析完成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '素材包上传失败');
    } finally {
      setUploading(false);
    }
  };

  const startAiGeneration = async () => {
    if (!selectedTemplate || !material?.materialPackageId) {
      message.warning('请先选择 SOP 模板并解析素材包');
      return;
    }
    setGenerating(true);
    try {
      const draft = await generateListingDraft(selectedTemplate.id, material.materialPackageId);
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

      <input
        ref={directoryInputRef}
        type="file"
        multiple
        hidden
        {...({ webkitdirectory: '' } as InputHTMLAttributes<HTMLInputElement>)}
        onChange={(event) => selectDirectory(Array.from(event.target.files ?? []))}
      />
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        <Space wrap>
          <Button onClick={() => directoryInputRef.current?.click()}>选择目录</Button>
          <Button type="primary" loading={uploading} disabled={selectedFiles.length === 0} onClick={uploadMaterial}>
            上传并解析
          </Button>
        </Space>
        {directoryName && (
          <Typography.Text type="secondary">
            {directoryName} · {selectedFiles.length} 个文件 · {formattedSize(selectedFiles.reduce((sum, { file }) => sum + file.size, 0))}
          </Typography.Text>
        )}
        {directoryName && <Progress percent={uploadProgress} size="small" />}
      </Space>

      {!material ? (
        <Typography.Text type="secondary">请先选择 SOP 模板，再上传本地素材包目录。</Typography.Text>
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
                    <ImagePathPreview materialPackageId={material.materialPackageId} paths={material.mainImageSourcePaths} emptyText="暂无主图来源" />
                    <Typography.Title level={5}>副图/描述图</Typography.Title>
                    <ImagePathPreview materialPackageId={material.materialPackageId} paths={material.detailImagePaths} emptyText="暂无副图/描述图" />
                    <Typography.Title level={5}>尺码表</Typography.Title>
                    <ImagePathPreview materialPackageId={material.materialPackageId} paths={sizeChartPaths} emptyText="暂无尺码表" />
                    <Typography.Title level={5}>产品包装图</Typography.Title>
                    <ImagePathPreview materialPackageId={material.materialPackageId} paths={material.packageImagePaths ?? []} emptyText="暂无产品包装图" />
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
