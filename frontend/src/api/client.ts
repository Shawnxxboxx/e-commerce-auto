import type {
  ListingDraftPageResponse,
  ListingDraftResponse,
  MabangPublishResult,
  ProductMaterialPackage,
  SopTemplate,
  SopTemplateCreateRequest,
  SopTemplateUpdateRequest,
} from './types';

function errorMessageFromText(text: string, status: number): string {
  try {
    const payload: unknown = JSON.parse(text);

    if (payload && typeof payload === 'object') {
      const { message, error } = payload as { message?: unknown; error?: unknown };
      if (typeof message === 'string' && message) {
        return message;
      }
      if (typeof error === 'string' && error) {
        return error;
      }
    }
  } catch {
    // Response was not JSON; fall back to the raw text below.
  }

  return text || `请求失败: ${status}`;
}

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(errorMessageFromText(text, response.status));
  }

  return response.json() as Promise<T>;
}

export function listTemplates(): Promise<SopTemplate[]> {
  return request<SopTemplate[]>('/api/sop-templates');
}

export function createTemplate(template: SopTemplateCreateRequest): Promise<SopTemplate> {
  return request<SopTemplate>('/api/sop-templates', {
    method: 'POST',
    body: JSON.stringify(template),
  });
}

export function updateTemplate(
  id: number,
  template: SopTemplateUpdateRequest,
): Promise<SopTemplate> {
  const { name, titlePrompt, mainImagePrompt } = template;

  return request<SopTemplate>(`/api/sop-templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, titlePrompt, mainImagePrompt }),
  });
}

export function parseMaterialPackage(materialPackagePath: string): Promise<ProductMaterialPackage> {
  return request<ProductMaterialPackage>('/api/material-packages/parse', {
    method: 'POST',
    body: JSON.stringify({ materialPackagePath }),
  });
}

export function generateListingDraft(templateId: number, materialPackagePath: string): Promise<ListingDraftResponse> {
  return request<ListingDraftResponse>('/api/listing-drafts/generate', {
    method: 'POST',
    body: JSON.stringify({ templateId, materialPackagePath }),
  });
}

export function getListingDraft(draftId: string): Promise<ListingDraftResponse> {
  return request<ListingDraftResponse>(`/api/listing-drafts/${draftId}`);
}

export function listListingDrafts(
  page: number,
  size: number,
  filters: { keyword?: string; status?: string } = {},
): Promise<ListingDraftPageResponse> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.keyword) params.set('keyword', filters.keyword);
  if (filters.status) params.set('status', filters.status);

  return request<ListingDraftPageResponse>(`/api/listing-drafts?${params}`);
}

export function publishListingDraft(draftId: string): Promise<MabangPublishResult> {
  return request<MabangPublishResult>(`/api/listing-drafts/${draftId}/publish`, {
    method: 'POST',
  });
}

export function chooseLocalDirectory(): Promise<{ path: string }> {
  return request<{ path: string }>('/api/local-files/choose-directory');
}

export function localImageUrl(path: string): string {
  return `/api/local-files/image?path=${encodeURIComponent(path)}`;
}
