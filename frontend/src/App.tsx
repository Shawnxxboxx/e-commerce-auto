import { useState } from 'react';
import { AppShell } from './components/AppShell';
import { TemplatePage } from './pages/TemplatePage';
import { MaterialPage } from './pages/MaterialPage';
import { DraftReviewPage } from './pages/DraftReviewPage';
import type { ProductMaterialPackage, SopTemplate } from './api/types';

type PageKey = 'templates' | 'materials' | 'drafts';

export default function App() {
  const [page, setPage] = useState<PageKey>('templates');
  const [selectedTemplate, setSelectedTemplate] = useState<SopTemplate | null>(null);
  const [material, setMaterial] = useState<ProductMaterialPackage | null>(null);

  return (
    <AppShell page={page} onPageChange={setPage}>
      {page === 'templates' && (
        <TemplatePage selectedTemplate={selectedTemplate} onTemplateSelect={setSelectedTemplate} />
      )}
      {page === 'materials' && <MaterialPage material={material} onMaterialChange={setMaterial} />}
      {page === 'drafts' && (
        <DraftReviewPage selectedTemplate={selectedTemplate} material={material} />
      )}
    </AppShell>
  );
}
