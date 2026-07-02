import type { ReactNode } from 'react';
import { AppstoreOutlined, DatabaseOutlined, FileTextOutlined } from '@ant-design/icons';
import { Layout, Menu, Tag, Typography } from 'antd';

export type AppPageKey = 'templates' | 'materials' | 'drafts';

interface AppShellProps {
  page: AppPageKey;
  onPageChange: (page: AppPageKey) => void;
  children: ReactNode;
}

const pageTitles: Record<AppPageKey, string> = {
  templates: '模板管理',
  materials: '素材解析',
  drafts: '草稿审核',
};

const menuItems = [
  { key: 'templates', icon: <FileTextOutlined />, label: '模板管理' },
  { key: 'materials', icon: <DatabaseOutlined />, label: '素材解析' },
  { key: 'drafts', icon: <AppstoreOutlined />, label: '草稿审核' },
];

export default function AppShell({ page, onPageChange, children }: AppShellProps) {
  return (
    <Layout className="app-shell">
      <Layout.Sider width={220} theme="light">
        <div className="app-logo">电商自动化</div>
        <Menu
          mode="inline"
          selectedKeys={[page]}
          items={menuItems}
          onClick={({ key }) => onPageChange(key as AppPageKey)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <Typography.Title level={4} style={{ margin: 0 }}>
            {pageTitles[page]}
          </Typography.Title>
          <Tag color="blue">本机工作台</Tag>
        </Layout.Header>
        <Layout.Content className="app-content">{children}</Layout.Content>
      </Layout>
    </Layout>
  );
}
