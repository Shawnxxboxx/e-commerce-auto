import { Alert, Button, Card, Descriptions, Drawer, Empty, Form, Input, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { getListingDraft, listListingDrafts, publishListingDraft } from '../api/client';
import type { ListingDraftPreview, ListingDraftResponse, ListingDraftTransactionRow } from '../api/types';
import { ImagePathPreview } from '../components/ImagePathPreview';

interface DraftReviewPageProps {
  draftId: string | null;
}

interface DraftSearchValues {
  keyword?: string;
  status?: string;
}

const statusText: Record<string, string> = {
  GENERATING: 'AI生成中',
  GENERATED: '待人工审核',
  PUBLISHING: '上架中',
  PUBLISHED: '已上架',
  FAILED: '失败',
};

const statusColor: Record<string, string> = {
  GENERATING: 'processing',
  GENERATED: 'blue',
  PUBLISHING: 'processing',
  PUBLISHED: 'green',
  FAILED: 'red',
};

function validateDraft(draft: ListingDraftPreview): string[] {
  const errors: string[] = [];
  if (!draft.chineseTitle) errors.push('缺少中文标题');
  if (!draft.productMainImage) errors.push('缺少产品主图');
  if (draft.descriptionImagePaths.length === 0) errors.push('缺少描述图');
  if (draft.transactionInfo.length === 0) errors.push('缺少交易信息');
  return errors;
}

function canPublish(record: ListingDraftResponse) {
  // 状态只用于展示，不限制再次上架；仍需保证草稿基础数据完整。
  return validateDraft(record.draft).length === 0;
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

function DraftDetail({
  response,
  publishing,
  onPublish,
}: {
  response: ListingDraftResponse;
  publishing: boolean;
  onPublish: (draftId: string) => void;
}) {
  const draft = response.draft;
  const errors = validateDraft(draft);
  const sizeChartPaths = draft.variantPreviewImages?.length
    ? draft.variantPreviewImages
    : draft.productSizeChartImage
      ? [draft.productSizeChartImage]
      : [];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        showIcon
        type={response.status === 'FAILED' || errors.length > 0 ? 'warning' : 'success'}
        message={statusText[response.status] ?? response.status}
        description={response.lastErrorMessage || (errors.length > 0 ? errors.join('；') : '草稿基础信息完整，可人工审核后上架。')}
        action={
          <Button type="primary" loading={publishing} disabled={!canPublish(response)} onClick={() => onPublish(response.draftId)}>
            上架
          </Button>
        }
      />

      <Card title="模板快照">
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="草稿 ID">{response.draftId}</Descriptions.Item>
          <Descriptions.Item label="状态">{statusText[response.status] ?? response.status}</Descriptions.Item>
          <Descriptions.Item label="模板名称">{draft.templateName ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="素材包">{draft.materialPackagePath ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="标题提示词" span={2}>
            {draft.titlePromptSnapshot ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="主图提示词" span={2}>
            {draft.mainImagePromptSnapshot ?? '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="基础信息">
        <Form key={draft.draftId || response.draftId} layout="vertical" initialValues={draft}>
          <Space style={{ width: '100%' }} size="middle" align="start">
            <Form.Item name="shopName" label="店铺" style={{ flex: 1 }}>
              <Input readOnly />
            </Form.Item>
            <Form.Item name="categoryName" label="类目" style={{ flex: 1 }}>
              <Input readOnly />
            </Form.Item>
            <Form.Item name="brand" label="品牌" style={{ flex: 1 }}>
              <Input readOnly />
            </Form.Item>
          </Space>
          <Space style={{ width: '100%' }} size="middle" align="start">
            <Form.Item name="manufacturer" label="制造商" style={{ flex: 1 }}>
              <Input readOnly />
            </Form.Item>
            <Form.Item name="euResponsiblePerson" label="欧盟责任人" style={{ flex: 1 }}>
              <Input readOnly />
            </Form.Item>
          </Space>
          <Form.Item name="sourceUrl" label="来源 URL">
            <Input readOnly />
          </Form.Item>
          <Form.Item name="chineseTitle" label="中文标题">
            <Input readOnly />
          </Form.Item>
          <Form.Item name="englishTitle" label="英文标题">
            <Input readOnly />
          </Form.Item>
        </Form>
      </Card>

      <Card title="图片审核">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Title level={5}>产品主图</Typography.Title>
          <ImagePathPreview paths={draft.productMainImage ? [draft.productMainImage] : []} />
          <Typography.Title level={5}>尺码表</Typography.Title>
          <ImagePathPreview paths={sizeChartPaths} emptyText="暂无尺码表" />
          <Typography.Title level={5}>描述图</Typography.Title>
          <ImagePathPreview paths={draft.descriptionImagePaths} />
          <Typography.Title level={5}>产品包装图</Typography.Title>
          <ImagePathPreview paths={draft.packageImagePaths ?? []} emptyText="暂无产品包装图" />
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

export function DraftReviewPage({ draftId }: DraftReviewPageProps) {
  const [form] = Form.useForm<DraftSearchValues>();
  const [records, setRecords] = useState<ListingDraftResponse[]>([]);
  const [selected, setSelected] = useState<ListingDraftResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [publishingId, setPublishingId] = useState<string | null>(null);
  const [filters, setFilters] = useState<DraftSearchValues>({});
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const loadPage = async (page = pagination.current, size = pagination.pageSize, nextFilters = filters) => {
    setLoading(true);
    try {
      const result = await listListingDrafts(page, size, nextFilters);
      setRecords(result.records);
      setPagination({ current: result.page, pageSize: result.size, total: result.total });
      return result.records;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '草稿列表加载失败');
      return [];
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPage();
  }, []);

  useEffect(() => {
    if (!draftId) return;
    getListingDraft(draftId)
      .then(setSelected)
      .catch((error) => message.error(error instanceof Error ? error.message : '草稿加载失败'));
  }, [draftId]);

  const publishDraft = async (nextDraftId: string) => {
    setPublishingId(nextDraftId);
    try {
      const result = await publishListingDraft(nextDraftId);
      result.success ? message.success(result.message || '上架完成') : message.error(result.message || '上架失败');
      const detail = await getListingDraft(nextDraftId);
      setSelected((current) => (current?.draftId === nextDraftId ? detail : current));
      await loadPage(pagination.current, pagination.pageSize, filters);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上架失败');
    } finally {
      setPublishingId(null);
    }
  };

  const columns: ColumnsType<ListingDraftResponse> = [
    {
      title: '草稿',
      dataIndex: 'draftId',
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.draft.chineseTitle || record.draftId}</Typography.Text>
          <Typography.Text type="secondary">{record.draftId}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '模板',
      render: (_, record) => record.draft.templateName || record.draft.templateId || '-',
    },
    {
      title: '店铺',
      render: (_, record) => record.draft.shopName || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (status: string) => <Tag color={statusColor[status] ?? 'default'}>{statusText[status] ?? status}</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      render: (value: string) => value || '-',
    },
    {
      title: '操作',
      width: 160,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => setSelected(record)}>
            详情
          </Button>
          <Button
            size="small"
            type="primary"
            loading={publishingId === record.draftId}
            disabled={!canPublish(record)}
            onClick={() => publishDraft(record.draftId)}
          >
            上架
          </Button>
        </Space>
      ),
    },
  ];

  const handleTableChange = (next: TablePaginationConfig) => {
    loadPage(next.current ?? 1, next.pageSize ?? 10, filters);
  };

  const searchDrafts = (values: DraftSearchValues) => {
    const nextFilters = {
      keyword: values.keyword?.trim(),
      status: values.status,
    };
    setFilters(nextFilters);
    loadPage(1, pagination.pageSize, nextFilters);
  };

  const resetSearch = () => {
    form.resetFields();
    setFilters({});
    loadPage(1, pagination.pageSize, {});
  };

  return (
    <>
      <Form form={form} layout="inline" onFinish={searchDrafts} style={{ marginBottom: 16 }}>
        <Form.Item name="keyword">
          <Input allowClear placeholder="草稿ID/标题/模板/店铺" style={{ width: 260 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 160 }}
            options={Object.entries(statusText).map(([value, label]) => ({ value, label }))}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={resetSearch}>重置</Button>
          </Space>
        </Form.Item>
      </Form>

      <Table
        rowKey="draftId"
        loading={loading}
        columns={columns}
        dataSource={records}
        pagination={pagination}
        onChange={handleTableChange}
        scroll={{ x: true }}
      />

      <Drawer
        width="80%"
        title="草稿详情"
        open={!!selected}
        onClose={() => setSelected(null)}
        extra={
          selected ? (
            <Button
              type="primary"
              loading={publishingId === selected.draftId}
              disabled={!canPublish(selected)}
              onClick={() => publishDraft(selected.draftId)}
            >
              上架
            </Button>
          ) : null
        }
      >
        {selected ? (
          <DraftDetail response={selected} publishing={publishingId === selected.draftId} onPublish={publishDraft} />
        ) : (
          <Empty description="请选择草稿" />
        )}
      </Drawer>
    </>
  );
}

export default DraftReviewPage;
