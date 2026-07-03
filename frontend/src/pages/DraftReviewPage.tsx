import { Alert, Button, Card, Descriptions, Empty, Form, Input, Space, Table, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { getListingDraft, publishListingDraft } from '../api/client';
import type {
  ListingDraftResponse,
  ListingDraftPreview,
  ListingDraftTransactionRow,
} from '../api/types';
import { ImagePathPreview } from '../components/ImagePathPreview';

interface DraftReviewPageProps {
  draftId: string | null;
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

const statusText: Record<string, string> = {
  GENERATING: 'AI生成中',
  GENERATED: '待人工审核',
  PUBLISHING: '上架中',
  PUBLISHED: '已上架',
  FAILED: '失败',
};

export function DraftReviewPage({ draftId }: DraftReviewPageProps) {
  const [response, setResponse] = useState<ListingDraftResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const draft = response?.draft ?? null;
  const errors = draft ? validateDraft(draft) : [];

  const loadDraft = async (id: string) => {
    const next = await getListingDraft(id);
    setResponse(next);
    return next;
  };

  useEffect(() => {
    if (!draftId) return;
    let disposed = false;
    let timer: number | undefined;

    const poll = async () => {
      try {
        setLoading(true);
        const next = await loadDraft(draftId);
        if (!disposed && next.status === 'GENERATING') {
          timer = window.setTimeout(poll, 2000);
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : '草稿加载失败');
      } finally {
        if (!disposed) setLoading(false);
      }
    };

    poll();
    return () => {
      disposed = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [draftId]);

  const publishDraft = async () => {
    if (!draftId) return;
    setPublishing(true);
    try {
      const result = await publishListingDraft(draftId);
      if (result.success) {
        message.success(result.message || '上架完成');
      } else {
        message.error(result.message || '上架失败');
      }
      await loadDraft(draftId);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '上架失败');
    } finally {
      setPublishing(false);
    }
  };

  if (!draftId) return <Empty description="请先在素材解析页点击 AI生成" />;
  if (!draft) return <Empty description={loading ? '草稿加载中' : '暂无草稿'} />;

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Alert
        showIcon
        type={response?.status === 'FAILED' || errors.length > 0 ? 'warning' : 'success'}
        message={statusText[response?.status ?? ''] ?? response?.status}
        description={response?.lastErrorMessage || (errors.length > 0 ? errors.join('；') : '草稿基础信息完整，可人工审核后上架。')}
        action={
          <Button
            type="primary"
            loading={publishing}
            disabled={response?.status !== 'GENERATED' || errors.length > 0}
            onClick={publishDraft}
          >
            上架
          </Button>
        }
      />

      <Card title="模板快照">
        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="草稿 ID">{response?.draftId}</Descriptions.Item>
          <Descriptions.Item label="状态">{statusText[response?.status ?? ''] ?? response?.status}</Descriptions.Item>
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
        <Form key={draft.draftId || response?.draftId} layout="vertical" initialValues={draft}>
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
