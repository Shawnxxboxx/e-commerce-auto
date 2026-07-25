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

  return response.status === 204 ? (undefined as T) : (response.json() as Promise<T>);
}

export interface SelectedMaterialFile {
  file: File;
  relativePath: string;
}

function multipartRequest<T>(url: string, form: FormData, onProgress: (percent: number) => void): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100));
    };
    xhr.onerror = () => reject(new Error('上传失败，请检查网络后重试'));
    xhr.onload = () => {
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(new Error(errorMessageFromText(xhr.responseText, xhr.status)));
        return;
      }
      try {
        resolve(JSON.parse(xhr.responseText) as T);
      } catch {
        reject(new Error('上传响应格式错误'));
      }
    };
    xhr.send(form);
  });
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

export function uploadMaterialPackage(
  directoryName: string,
  files: SelectedMaterialFile[],
  onProgress: (percent: number) => void,
): Promise<ProductMaterialPackage> {
  const form = new FormData();
  form.append('originalDirectoryName', directoryName);
  files.forEach(({ file, relativePath }) => {
    form.append('files', file);
    form.append('relativePaths', relativePath);
  });
  return multipartRequest<ProductMaterialPackage>('/api/material-packages', form, onProgress);
}

export function generateListingDraft(templateId: number, materialPackageId: string): Promise<ListingDraftResponse> {
  return request<ListingDraftResponse>('/api/listing-drafts/generate', {
    method: 'POST',
    body: JSON.stringify({ templateId, materialPackageId }),
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

export function deleteListingDraft(draftId: string): Promise<void> {
  return request<void>(`/api/listing-drafts/${encodeURIComponent(draftId)}`, {
    method: 'DELETE',
  });
}

export function materialImageUrl(materialPackageId: string, relativePath: string): string {
  return `/api/material-packages/${encodeURIComponent(materialPackageId)}/files?path=${encodeURIComponent(relativePath)}`;
}

export function localImageUrl(path: string): string {
  return `/api/local-files/image?path=${encodeURIComponent(path)}`;
}
