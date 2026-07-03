import { useState } from 'react';
import AppShell, { type AppPageKey } from './components/AppShell';
import TemplatePage from './pages/TemplatePage';
import { MaterialPage } from './pages/MaterialPage';
import { DraftReviewPage } from './pages/DraftReviewPage';
import type { ProductMaterialPackage, SopTemplate } from './api/types';

export default function App() {
  const [page, setPage] = useState<AppPageKey>('templates');
  const [selectedTemplate, setSelectedTemplate] = useState<SopTemplate | null>(null);
  const [material, setMaterial] = useState<ProductMaterialPackage | null>(null);
  const [draftId, setDraftId] = useState<string | null>(null);

  return (
    <AppShell page={page} onPageChange={setPage}>
      {page === 'templates' && (
        <TemplatePage />
      )}
      {page === 'materials' && (
        <MaterialPage
          material={material}
          selectedTemplate={selectedTemplate}
          onTemplateSelect={setSelectedTemplate}
          onMaterialParsed={setMaterial}
          onDraftStarted={(nextDraftId) => {
            setDraftId(nextDraftId);
            setPage('drafts');
          }}
        />
      )}
      {page === 'drafts' && (
        <DraftReviewPage draftId={draftId} />
      )}
    </AppShell>
  );
}
