# SOP Frontend Workbench MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a React + Ant Design workbench under `frontend/` for SOP template management, material package parsing, image preview, and draft review scaffolding.

**Architecture:** Keep backend and frontend loosely coupled through REST APIs. Add one backend read-only image endpoint so browser previews can display local material images. Frontend state stays local for this MVP: parsed material data can be promoted into a draft preview without persisting draft records yet.

**Tech Stack:** Java 21, Spring Boot 4.1, JUnit 5, React, TypeScript, Vite, Ant Design, Ant Design Icons.

---

## File Structure

Create or modify these files:

```text
src/main/java/com/auto/ecommerce/ecommerceauto/localfile/controller/LocalFileController.java
src/main/java/com/auto/ecommerce/ecommerceauto/localfile/service/LocalImageFileService.java
src/test/java/com/auto/ecommerce/ecommerceauto/localfile/service/LocalImageFileServiceTest.java

frontend/package.json
frontend/index.html
frontend/vite.config.ts
frontend/tsconfig.json
frontend/tsconfig.node.json
frontend/src/main.tsx
frontend/src/App.tsx
frontend/src/styles.css
frontend/src/api/client.ts
frontend/src/api/types.ts
frontend/src/components/AppShell.tsx
frontend/src/components/ImagePathPreview.tsx
frontend/src/pages/TemplatePage.tsx
frontend/src/pages/MaterialPage.tsx
frontend/src/pages/DraftReviewPage.tsx
```

## Task 1: Add Backend Local Image Preview Endpoint

**Files:**
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/localfile/service/LocalImageFileService.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/localfile/controller/LocalFileController.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/localfile/service/LocalImageFileServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `LocalImageFileServiceTest.java`:

```java
package com.auto.ecommerce.ecommerceauto.localfile.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageFileServiceTest {

    @TempDir
    Path tempDir;

    private final LocalImageFileService service = new LocalImageFileService();

    @Test
    void readsSupportedImageFile() throws Exception {
        Path image = tempDir.resolve("main.jpg");
        Files.writeString(image, "image");

        LocalImageFileService.LocalImageFile result = service.readImage(image.toString());

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.bytes()).isEqualTo("image".getBytes());
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        Path text = tempDir.resolve("note.txt");
        Files.writeString(text, "text");

        assertThatThrownBy(() -> service.readImage(text.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 jpg、jpeg、png 图片");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./mvnw -Dtest=LocalImageFileServiceTest test
```

Expected:

```text
Compilation failure: package ...localfile.service does not exist
```

- [ ] **Step 3: Implement local image service**

Create `LocalImageFileService.java`:

```java
package com.auto.ecommerce.ecommerceauto.localfile.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class LocalImageFileService {

    public LocalImageFile readImage(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("图片路径不能为空");
        }
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("图片文件不存在: " + path);
        }
        String contentType = contentType(path);
        try {
            return new LocalImageFile(contentType, Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("读取图片失败: " + path, e);
        }
    }

    private String contentType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        throw new IllegalArgumentException("仅支持 jpg、jpeg、png 图片: " + path);
    }

    public record LocalImageFile(String contentType, byte[] bytes) {
    }
}
```

- [ ] **Step 4: Implement controller**

Create `LocalFileController.java`:

```java
package com.auto.ecommerce.ecommerceauto.localfile.controller;

import com.auto.ecommerce.ecommerceauto.localfile.service.LocalImageFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/local-files")
@RequiredArgsConstructor
public class LocalFileController {

    private final LocalImageFileService service;

    @GetMapping("/image")
    public ResponseEntity<byte[]> image(@RequestParam String path) {
        LocalImageFileService.LocalImageFile image = service.readImage(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .body(image.bytes());
    }
}
```

- [ ] **Step 5: Run backend tests**

Run:

```bash
./mvnw -Dtest=LocalImageFileServiceTest test
./mvnw test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/localfile src/test/java/com/auto/ecommerce/ecommerceauto/localfile
git commit -m "feat: add local image preview endpoint"
```

## Task 2: Scaffold React + Ant Design Frontend

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/styles.css`

- [ ] **Step 1: Create package metadata**

Create `frontend/package.json`:

```json
{
  "name": "e-commerce-auto-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite --host 127.0.0.1",
    "build": "tsc -b && vite build",
    "preview": "vite preview --host 127.0.0.1"
  },
  "dependencies": {
    "@ant-design/icons": "^5.6.1",
    "antd": "^5.22.5",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.4",
    "typescript": "^5.6.3",
    "vite": "^6.0.1"
  }
}
```

- [ ] **Step 2: Create Vite and TypeScript config**

Create `frontend/vite.config.ts`:

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
```

Create `frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["DOM", "DOM.Iterable", "ES2020"],
    "allowJs": false,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "forceConsistentCasingInFileNames": true,
    "module": "ESNext",
    "moduleResolution": "Node",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx"
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

Create `frontend/tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "composite": true,
    "module": "ESNext",
    "moduleResolution": "Node",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 3: Create entry files**

Create `frontend/index.html`:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>SOP 上架工作台</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

Create `frontend/src/main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN}>
      <App />
    </ConfigProvider>
  </React.StrictMode>,
);
```

Create `frontend/src/App.tsx`:

```tsx
import { useState } from 'react';
import AppShell, { AppPageKey } from './components/AppShell';
import TemplatePage from './pages/TemplatePage';
import MaterialPage from './pages/MaterialPage';
import DraftReviewPage from './pages/DraftReviewPage';
import { ProductMaterialPackage, SopTemplate } from './api/types';

export default function App() {
  const [page, setPage] = useState<AppPageKey>('templates');
  const [selectedTemplate, setSelectedTemplate] = useState<SopTemplate | null>(null);
  const [material, setMaterial] = useState<ProductMaterialPackage | null>(null);

  return (
    <AppShell page={page} onPageChange={setPage}>
      {page === 'templates' && (
        <TemplatePage
          selectedTemplate={selectedTemplate}
          onSelectTemplate={setSelectedTemplate}
        />
      )}
      {page === 'materials' && (
        <MaterialPage
          material={material}
          onMaterialParsed={setMaterial}
          onReviewDraft={() => setPage('drafts')}
        />
      )}
      {page === 'drafts' && (
        <DraftReviewPage template={selectedTemplate} material={material} />
      )}
    </AppShell>
  );
}
```

- [ ] **Step 4: Add base styles**

Create `frontend/src/styles.css` with stable shell dimensions and Ant Design-friendly defaults:

```css
html,
body,
#root {
  height: 100%;
  margin: 0;
}

body {
  background: #f5f7fb;
  color: #1f2937;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.app-shell {
  min-height: 100%;
}

.app-logo {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

.app-content {
  padding: 20px;
}

.page-grid {
  display: grid;
  gap: 16px;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.image-preview {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #ffffff;
}

.image-preview img {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  display: block;
}

.image-preview-caption {
  padding: 8px;
  font-size: 12px;
  color: #4b5563;
  word-break: break-all;
}
```

- [ ] **Step 5: Install dependencies**

Run from `frontend/`:

```bash
npm install
```

Expected:

```text
added ... packages
```

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/index.html frontend/vite.config.ts frontend/tsconfig.json frontend/tsconfig.node.json frontend/src/main.tsx frontend/src/App.tsx frontend/src/styles.css
git commit -m "feat: scaffold react workbench"
```

## Task 3: Add API Types and Client

**Files:**
- Create: `frontend/src/api/types.ts`
- Create: `frontend/src/api/client.ts`

- [ ] **Step 1: Create shared API types**

Create `frontend/src/api/types.ts`:

```ts
export interface SopTemplate {
  id?: number;
  templateId: string;
  name: string;
  titlePrompt: string;
  mainImagePrompt: string;
  createTime?: string;
  updateTime?: string;
}

export interface MaterialTransactionRow {
  color: string;
  specification: string;
  stockingMode: string;
  skc: string;
  sku: string;
  price: number;
  stock: number;
  length: number;
  width: number;
  height: number;
  weightGram: number;
}

export interface ProductMaterialPackage {
  materialPackagePath?: string;
  productName: string;
  sourceUrl: string;
  shopName: string;
  categoryName: string;
  brand: string;
  categoryAttributes: Record<string, string>;
  variantAttributes: Record<string, string>;
  sizeChartImageName?: string;
  sizeChartImagePath?: string;
  transactionRows: MaterialTransactionRow[];
  mainImageSourcePaths: string[];
  detailImagePaths: string[];
}

export interface ListingDraftPreview {
  shopName: string;
  categoryName: string;
  sourceUrl: string;
  chineseTitle: string;
  englishTitle: string;
  brand: string;
  productMainImage?: string;
  productSizeChartImage?: string;
  descriptionImagePaths: string[];
  categoryAttributes: Record<string, string>;
  variantAttributes: Record<string, string[]>;
  transactionInfo: MaterialTransactionRow[];
}
```

- [ ] **Step 2: Create API client**

Create `frontend/src/api/client.ts`:

```ts
import { ProductMaterialPackage, SopTemplate } from './types';

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `请求失败: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export function listTemplates() {
  return request<SopTemplate[]>('/api/sop-templates');
}

export function createTemplate(template: SopTemplate) {
  return request<SopTemplate>('/api/sop-templates', {
    method: 'POST',
    body: JSON.stringify(template),
  });
}

export function updateTemplate(templateId: string, template: Omit<SopTemplate, 'templateId'>) {
  return request<SopTemplate>(`/api/sop-templates/${encodeURIComponent(templateId)}`, {
    method: 'PUT',
    body: JSON.stringify(template),
  });
}

export function parseMaterialPackage(materialPackagePath: string) {
  return request<ProductMaterialPackage>('/api/material-packages/parse', {
    method: 'POST',
    body: JSON.stringify({ materialPackagePath }),
  });
}

export function localImageUrl(path: string) {
  return `/api/local-files/image?path=${encodeURIComponent(path)}`;
}
```

- [ ] **Step 3: Run TypeScript build**

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
error TS2307: Cannot find module './components/AppShell'
```

The expected failure happens because `App.tsx` already references pages/components created in later tasks.

## Task 4: Build App Shell and Template Management

**Files:**
- Create: `frontend/src/components/AppShell.tsx`
- Create: `frontend/src/pages/TemplatePage.tsx`

- [ ] **Step 1: Implement AppShell**

Create `frontend/src/components/AppShell.tsx`:

```tsx
import {
  AppstoreOutlined,
  DatabaseOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { Layout, Menu, Tag, Typography } from 'antd';
import { ReactNode } from 'react';

const { Sider, Header, Content } = Layout;

export type AppPageKey = 'templates' | 'materials' | 'drafts';

interface AppShellProps {
  page: AppPageKey;
  onPageChange: (page: AppPageKey) => void;
  children: ReactNode;
}

const titles: Record<AppPageKey, string> = {
  templates: '模板管理',
  materials: '素材解析',
  drafts: '草稿审核',
};

export default function AppShell({ page, onPageChange, children }: AppShellProps) {
  return (
    <Layout className="app-shell">
      <Sider width={220}>
        <div className="app-logo">SOP 上架工作台</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[page]}
          onClick={(item) => onPageChange(item.key as AppPageKey)}
          items={[
            { key: 'templates', icon: <FileTextOutlined />, label: '模板管理' },
            { key: 'materials', icon: <DatabaseOutlined />, label: '素材解析' },
            { key: 'drafts', icon: <AppstoreOutlined />, label: '草稿审核' },
          ]}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Typography.Title level={4} style={{ margin: 0 }}>
            {titles[page]}
          </Typography.Title>
          <Tag color="blue">本机工作台</Tag>
        </Header>
        <Content className="app-content">{children}</Content>
      </Layout>
    </Layout>
  );
}
```

- [ ] **Step 2: Implement TemplatePage**

Create `frontend/src/pages/TemplatePage.tsx` with list, drawer edit form, create/update calls:

```tsx
import { Button, Drawer, Form, Input, Space, Table, Typography, message } from 'antd';
import { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { createTemplate, listTemplates, updateTemplate } from '../api/client';
import { SopTemplate } from '../api/types';

interface TemplatePageProps {
  selectedTemplate: SopTemplate | null;
  onSelectTemplate: (template: SopTemplate) => void;
}

export default function TemplatePage({ selectedTemplate, onSelectTemplate }: TemplatePageProps) {
  const [templates, setTemplates] = useState<SopTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<SopTemplate | null>(null);
  const [form] = Form.useForm<SopTemplate>();

  async function loadTemplates() {
    setLoading(true);
    try {
      setTemplates(await listTemplates());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '模板加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadTemplates();
  }, []);

  function openCreate() {
    setEditing(null);
    form.resetFields();
    setDrawerOpen(true);
  }

  function openEdit(template: SopTemplate) {
    setEditing(template);
    form.setFieldsValue(template);
    setDrawerOpen(true);
  }

  async function saveTemplate() {
    const values = await form.validateFields();
    try {
      const saved = editing
        ? await updateTemplate(editing.templateId, values)
        : await createTemplate(values);
      message.success('模板已保存');
      onSelectTemplate(saved);
      setDrawerOpen(false);
      await loadTemplates();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '模板保存失败');
    }
  }

  const columns: ColumnsType<SopTemplate> = [
    { title: '模板 ID', dataIndex: 'templateId', width: 230 },
    { title: '名称', dataIndex: 'name' },
    { title: '更新时间', dataIndex: 'updateTime', width: 190 },
    {
      title: '操作',
      width: 190,
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => onSelectTemplate(record)}>
            选择
          </Button>
          <Button type="link" onClick={() => openEdit(record)}>
            编辑
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-grid">
      <Space align="center" style={{ justifyContent: 'space-between' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            SOP 模板
          </Typography.Title>
          <Typography.Text type="secondary">
            当前选择：{selectedTemplate?.name || '未选择'}
          </Typography.Text>
        </div>
        <Button type="primary" onClick={openCreate}>
          新增模板
        </Button>
      </Space>

      <Table
        rowKey="templateId"
        loading={loading}
        columns={columns}
        dataSource={templates}
        pagination={false}
      />

      <Drawer
        width={620}
        title={editing ? '编辑模板' : '新增模板'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Button type="primary" onClick={saveTemplate}>
            保存
          </Button>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="templateId" label="模板 ID" rules={[{ required: true }]}>
            <Input disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item name="name" label="模板名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="titlePrompt" label="标题提示词" rules={[{ required: true }]}>
            <Input.TextArea rows={8} />
          </Form.Item>
          <Form.Item name="mainImagePrompt" label="主图提示词" rules={[{ required: true }]}>
            <Input.TextArea rows={8} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}
```

- [ ] **Step 3: Run build**

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
error TS2307: Cannot find module './pages/MaterialPage'
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/AppShell.tsx frontend/src/pages/TemplatePage.tsx frontend/src/App.tsx
git commit -m "feat: add template workbench page"
```

## Task 5: Build Material Parsing Page and Image Preview

**Files:**
- Create: `frontend/src/components/ImagePathPreview.tsx`
- Create: `frontend/src/pages/MaterialPage.tsx`

- [ ] **Step 1: Implement ImagePathPreview**

Create `frontend/src/components/ImagePathPreview.tsx`:

```tsx
import { FileImageOutlined } from '@ant-design/icons';
import { Empty } from 'antd';
import { localImageUrl } from '../api/client';

interface ImagePathPreviewProps {
  paths: string[];
  emptyText?: string;
}

function fileName(path: string) {
  return path.split('/').pop() || path;
}

export default function ImagePathPreview({ paths, emptyText = '暂无图片' }: ImagePathPreviewProps) {
  if (!paths.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }

  return (
    <div className="image-grid">
      {paths.map((path) => (
        <div className="image-preview" key={path}>
          <img src={localImageUrl(path)} alt={fileName(path)} />
          <div className="image-preview-caption">
            <FileImageOutlined /> {fileName(path)}
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 2: Implement MaterialPage**

Create `frontend/src/pages/MaterialPage.tsx`:

```tsx
import { Button, Card, Descriptions, Form, Input, Space, Table, Tabs, Typography, message } from 'antd';
import { useState } from 'react';
import { parseMaterialPackage } from '../api/client';
import { MaterialTransactionRow, ProductMaterialPackage } from '../api/types';
import ImagePathPreview from '../components/ImagePathPreview';

interface MaterialPageProps {
  material: ProductMaterialPackage | null;
  onMaterialParsed: (material: ProductMaterialPackage) => void;
  onReviewDraft: () => void;
}

export default function MaterialPage({ material, onMaterialParsed, onReviewDraft }: MaterialPageProps) {
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm<{ materialPackagePath: string }>();

  async function parse() {
    const values = await form.validateFields();
    setLoading(true);
    try {
      const parsed = await parseMaterialPackage(values.materialPackagePath);
      onMaterialParsed(parsed);
      message.success('素材包解析完成');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '素材包解析失败');
    } finally {
      setLoading(false);
    }
  }

  const transactionColumns = [
    { title: '颜色', dataIndex: 'color' },
    { title: '规格', dataIndex: 'specification' },
    { title: 'SKU', dataIndex: 'sku' },
    { title: '价格', dataIndex: 'price' },
    { title: '库存', dataIndex: 'stock' },
    { title: '尺寸', render: (_: unknown, row: MaterialTransactionRow) => `${row.length} × ${row.width} × ${row.height}` },
    { title: '重量g', dataIndex: 'weightGram' },
  ];

  return (
    <div className="page-grid">
      <Card>
        <Form form={form} layout="inline">
          <Form.Item
            name="materialPackagePath"
            label="素材包路径"
            rules={[{ required: true, message: '请输入素材包绝对路径' }]}
            style={{ flex: 1 }}
          >
            <Input placeholder="/Users/xiaobo/Downloads/素材/商品名称" />
          </Form.Item>
          <Button type="primary" loading={loading} onClick={parse}>
            解析素材包
          </Button>
          <Button disabled={!material} onClick={onReviewDraft}>
            进入草稿审核
          </Button>
        </Form>
      </Card>

      {material && (
        <>
          <Card title="产品信息">
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="产品名称">{material.productName}</Descriptions.Item>
              <Descriptions.Item label="店铺">{material.shopName}</Descriptions.Item>
              <Descriptions.Item label="类目">{material.categoryName}</Descriptions.Item>
              <Descriptions.Item label="品牌">{material.brand}</Descriptions.Item>
              <Descriptions.Item label="来源 URL" span={2}>{material.sourceUrl || '-'}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Tabs
            items={[
              {
                key: 'images',
                label: '图片',
                children: (
                  <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Card title="主图来源"><ImagePathPreview paths={material.mainImageSourcePaths} /></Card>
                    <Card title="副图 / 描述图"><ImagePathPreview paths={material.detailImagePaths} /></Card>
                    <Card title="尺码表"><ImagePathPreview paths={material.sizeChartImagePath ? [material.sizeChartImagePath] : []} /></Card>
                  </Space>
                ),
              },
              {
                key: 'attributes',
                label: '属性',
                children: (
                  <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Card title="分类属性">
                      <Descriptions column={2} bordered size="small">
                        {Object.entries(material.categoryAttributes).map(([key, value]) => (
                          <Descriptions.Item label={key} key={key}>{value}</Descriptions.Item>
                        ))}
                      </Descriptions>
                    </Card>
                    <Card title="变种属性">
                      <Descriptions column={2} bordered size="small">
                        {Object.entries(material.variantAttributes).map(([key, value]) => (
                          <Descriptions.Item label={key} key={key}>{value}</Descriptions.Item>
                        ))}
                      </Descriptions>
                    </Card>
                  </Space>
                ),
              },
              {
                key: 'transactions',
                label: '交易信息',
                children: (
                  <Card>
                    <Table
                      rowKey="sku"
                      columns={transactionColumns}
                      dataSource={material.transactionRows}
                      pagination={false}
                      size="small"
                    />
                  </Card>
                ),
              },
            ]}
          />
        </>
      )}

      {!material && (
        <Card>
          <Typography.Text type="secondary">输入素材包路径后，解析结果会显示在这里。</Typography.Text>
        </Card>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Run build**

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
error TS2307: Cannot find module './pages/DraftReviewPage'
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/ImagePathPreview.tsx frontend/src/pages/MaterialPage.tsx frontend/src/api
git commit -m "feat: add material parsing page"
```

## Task 6: Build Draft Review Page

**Files:**
- Create: `frontend/src/pages/DraftReviewPage.tsx`

- [ ] **Step 1: Implement DraftReviewPage**

Create `frontend/src/pages/DraftReviewPage.tsx`:

```tsx
import { Alert, Card, Descriptions, Empty, Form, Input, Space, Table, Typography } from 'antd';
import { useMemo } from 'react';
import { ListingDraftPreview, ProductMaterialPackage, SopTemplate } from '../api/types';
import ImagePathPreview from '../components/ImagePathPreview';

interface DraftReviewPageProps {
  template: SopTemplate | null;
  material: ProductMaterialPackage | null;
}

function toArrayVariantAttributes(attributes: Record<string, string>) {
  return Object.fromEntries(
    Object.entries(attributes).map(([key, value]) => [key, value.split(',').map((item) => item.trim()).filter(Boolean)]),
  );
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
    variantAttributes: toArrayVariantAttributes(material.variantAttributes),
    transactionInfo: material.transactionRows,
  };
}

function validateDraft(draft: ListingDraftPreview | null) {
  const errors: string[] = [];
  if (!draft) {
    return ['缺少素材解析结果'];
  }
  if (!draft.chineseTitle) errors.push('缺少中文标题');
  if (!draft.productMainImage) errors.push('缺少产品主图');
  if (!draft.descriptionImagePaths.length) errors.push('缺少描述图');
  if (!draft.transactionInfo.length) errors.push('缺少交易信息');
  return errors;
}

export default function DraftReviewPage({ template, material }: DraftReviewPageProps) {
  const draft = useMemo(() => (material ? createDraft(material) : null), [material]);
  const errors = validateDraft(draft);

  if (!material || !draft) {
    return <Empty description="请先在素材解析页解析一个素材包" />;
  }

  return (
    <div className="page-grid">
      <Alert
        type={errors.length ? 'warning' : 'success'}
        showIcon
        message={errors.length ? '草稿还需要补充信息' : '草稿基础信息完整'}
        description={errors.length ? errors.join('；') : '后续接入 CodexExec 后，这里会展示 AI 生成的标题和主图。'}
      />

      <Card title="模板快照">
        {template ? (
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="模板 ID">{template.templateId}</Descriptions.Item>
            <Descriptions.Item label="模板名称">{template.name}</Descriptions.Item>
            <Descriptions.Item label="标题提示词" span={2}>{template.titlePrompt}</Descriptions.Item>
            <Descriptions.Item label="主图提示词" span={2}>{template.mainImagePrompt}</Descriptions.Item>
          </Descriptions>
        ) : (
          <Typography.Text type="secondary">未选择模板，仍可预览素材草稿。</Typography.Text>
        )}
      </Card>

      <Card title="基础信息">
        <Form layout="vertical" initialValues={draft}>
          <Space style={{ width: '100%' }} size="large" align="start">
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
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div>
            <Typography.Title level={5}>产品主图</Typography.Title>
            <ImagePathPreview paths={draft.productMainImage ? [draft.productMainImage] : []} />
          </div>
          <div>
            <Typography.Title level={5}>尺码表</Typography.Title>
            <ImagePathPreview paths={draft.productSizeChartImage ? [draft.productSizeChartImage] : []} />
          </div>
          <div>
            <Typography.Title level={5}>描述图</Typography.Title>
            <ImagePathPreview paths={draft.descriptionImagePaths} />
          </div>
        </Space>
      </Card>

      <Card title="交易信息">
        <Table
          rowKey="sku"
          dataSource={draft.transactionInfo}
          pagination={false}
          size="small"
          columns={[
            { title: '颜色', dataIndex: 'color' },
            { title: '规格', dataIndex: 'specification' },
            { title: 'SKU', dataIndex: 'sku' },
            { title: '价格', dataIndex: 'price' },
            { title: '库存', dataIndex: 'stock' },
            { title: '重量g', dataIndex: 'weightGram' },
          ]}
        />
      </Card>
    </div>
  );
}
```

- [ ] **Step 2: Run build**

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
✓ built in ...
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/DraftReviewPage.tsx
git commit -m "feat: add draft review page"
```

## Task 7: Final Verification and Dev Server

**Files:**
- Modify only if verification finds issues.

- [ ] **Step 1: Run backend tests**

Run:

```bash
./mvnw test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 2: Run frontend build**

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
✓ built in ...
```

- [ ] **Step 3: Start backend**

Run:

```bash
./mvnw spring-boot:run
```

Expected:

```text
Started ECommerceAutoApplication
```

- [ ] **Step 4: Start frontend**

Run:

```bash
cd frontend
npm run dev
```

Expected:

```text
Local: http://127.0.0.1:5173/
```

- [ ] **Step 5: Browser smoke test**

Open:

```text
http://127.0.0.1:5173/
```

Check:

- 模板管理页显示空表或真实模板列表。
- 素材解析页可以提交路径并展示错误或解析结果。
- 草稿审核页在无素材时显示空状态，在解析素材后显示草稿壳。

- [ ] **Step 6: Commit verification fixes if any**

If verification required fixes:

```bash
git add <changed-files>
git commit -m "fix: polish frontend workbench verification"
```

If no fixes were required, do not create an empty commit.
