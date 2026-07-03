import { useEffect, useState } from 'react';
import { Button, Descriptions, Drawer, Form, Input, Space, Table, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { createTemplate, listTemplates, updateTemplate } from '../api/client';
import type { SopTemplate, SopTemplateUpdateRequest } from '../api/types';

type TemplateFormValues = Pick<
  SopTemplate,
  'name' | 'titlePrompt' | 'mainImagePrompt'
>;

function formatTime(value?: string): string {
  return value ? new Date(value).toLocaleString() : '-';
}

export default function TemplatePage() {
  const [templates, setTemplates] = useState<SopTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<SopTemplate | null>(null);
  const [form] = Form.useForm<TemplateFormValues>();

  const refreshTemplates = async () => {
    setLoading(true);
    try {
      setTemplates(await listTemplates());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '模板列表加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refreshTemplates();
  }, []);

  const openCreateDrawer = () => {
    setEditing(null);
    form.resetFields();
    setDrawerOpen(true);
  };

  const openEditDrawer = (template: SopTemplate) => {
    setEditing(template);
    form.setFieldsValue(template);
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditing(null);
    form.resetFields();
  };

  const saveTemplate = async (values: TemplateFormValues) => {
    setSaving(true);
    try {
      if (editing) {
        await updateTemplate(editing.id, {
          name: values.name,
          titlePrompt: values.titlePrompt,
          mainImagePrompt: values.mainImagePrompt,
        } satisfies SopTemplateUpdateRequest);
      } else {
        await createTemplate(values);
      }

      message.success('模板已保存');
      closeDrawer();
      await refreshTemplates();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '模板保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<SopTemplate> = [
    {
      title: '模板 ID',
      dataIndex: 'id',
      key: 'id',
      width: 100,
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 180,
    },
    {
      title: '标题提示词',
      dataIndex: 'titlePrompt',
      key: 'titlePrompt',
      render: (value: string) => (
        <Typography.Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>
          {value}
        </Typography.Paragraph>
      ),
    },
    {
      title: '主图提示词',
      dataIndex: 'mainImagePrompt',
      key: 'mainImagePrompt',
      render: (value: string) => (
        <Typography.Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>
          {value}
        </Typography.Paragraph>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'gmtModifiedTime',
      key: 'gmtModifiedTime',
      render: formatTime,
      width: 180,
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_, template) => (
        <Button type="link" onClick={() => openEditDrawer(template)}>
          编辑
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Button type="primary" onClick={openCreateDrawer}>
          新增模板
        </Button>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={templates}
        pagination={false}
        scroll={{ x: 960 }}
        expandable={{
          expandedRowRender: (template) => (
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="模板 ID">{template.id}</Descriptions.Item>
              <Descriptions.Item label="名称">{template.name}</Descriptions.Item>
              <Descriptions.Item label="标题提示词">
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                  {template.titlePrompt}
                </Typography.Paragraph>
              </Descriptions.Item>
              <Descriptions.Item label="主图提示词">
                <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                  {template.mainImagePrompt}
                </Typography.Paragraph>
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatTime(template.gmtCreateTime)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatTime(template.gmtModifiedTime)}</Descriptions.Item>
            </Descriptions>
          ),
        }}
      />

      <Drawer
        title={editing ? '编辑模板' : '新增模板'}
        width={760}
        open={drawerOpen}
        onClose={closeDrawer}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={closeDrawer}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => form.submit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" onFinish={saveTemplate}>
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            label="标题提示词"
            name="titlePrompt"
            rules={[{ required: true, message: '请输入标题提示词' }]}
          >
            <Input.TextArea rows={10} showCount />
          </Form.Item>
          <Form.Item
            label="主图提示词"
            name="mainImagePrompt"
            rules={[{ required: true, message: '请输入主图提示词' }]}
          >
            <Input.TextArea rows={10} showCount />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}
