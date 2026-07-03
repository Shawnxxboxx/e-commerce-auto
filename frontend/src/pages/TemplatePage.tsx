import { useEffect, useState } from 'react';
import { Button, Drawer, Form, Input, Space, Table, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { createTemplate, listTemplates, updateTemplate } from '../api/client';
import type { SopTemplate, SopTemplateUpdateRequest } from '../api/types';

interface TemplatePageProps {
  selectedTemplate: SopTemplate | null;
  onSelectTemplate: (template: SopTemplate) => void;
}

type TemplateFormValues = Pick<
  SopTemplate,
  'name' | 'titlePrompt' | 'mainImagePrompt'
>;

function formatTime(value?: string): string {
  return value ? new Date(value).toLocaleString() : '-';
}

export default function TemplatePage({ selectedTemplate, onSelectTemplate }: TemplatePageProps) {
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
      const saved = editing
        ? await updateTemplate(editing.id, {
            name: values.name,
            titlePrompt: values.titlePrompt,
            mainImagePrompt: values.mainImagePrompt,
          } satisfies SopTemplateUpdateRequest)
        : await createTemplate(values);

      message.success('模板已保存');
      closeDrawer();
      await refreshTemplates();
      onSelectTemplate(saved);
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
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '更新时间',
      dataIndex: 'gmtModifiedTime',
      key: 'gmtModifiedTime',
      render: formatTime,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, template) => (
        <Space>
          <Button type="link" onClick={() => onSelectTemplate(template)}>
            选择
          </Button>
          <Button type="link" onClick={() => openEditDrawer(template)}>
            编辑
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Text type="secondary">
          当前模板：{selectedTemplate?.name ?? '未选择'}
        </Typography.Text>
        <Button type="primary" onClick={openCreateDrawer}>
          新增模板
        </Button>
      </Space>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={templates}
        pagination={false}
      />

      <Drawer
        title={editing ? '编辑模板' : '新增模板'}
        width={520}
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
          <Form.Item label="name" name="name" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            label="titlePrompt"
            name="titlePrompt"
            rules={[{ required: true, message: '请输入标题提示词' }]}
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item
            label="mainImagePrompt"
            name="mainImagePrompt"
            rules={[{ required: true, message: '请输入主图提示词' }]}
          >
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Drawer>
    </Space>
  );
}
