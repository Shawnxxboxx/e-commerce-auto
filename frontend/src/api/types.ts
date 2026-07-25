export interface SopTemplate {
  id: number;
  name: string;
  titlePrompt: string;
  mainImagePrompt: string;
  gmtCreateTime?: string;
  gmtModifiedTime?: string;
}

export type SopTemplateCreateRequest = Pick<SopTemplate, 'name' | 'titlePrompt' | 'mainImagePrompt'>;
export type SopTemplateUpdateRequest = Pick<SopTemplate, 'name' | 'titlePrompt' | 'mainImagePrompt'>;

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
  materialPackageId: string;
  originalDirectoryName: string;
  fileCount: number;
  totalSize: number;
  productName: string;
  sourceUrl: string;
  shopName: string;
  categoryName: string;
  brand: string;
  manufacturer?: string;
  euResponsiblePerson?: string;
  categoryAttributes: Record<string, string>;
  variantAttributes: Record<string, string>;
  sizeChartImageName?: string;
  sizeChartImagePath?: string;
  sizeChartImagePaths?: string[];
  packageImagePaths?: string[];
  transactionRows: MaterialTransactionRow[];
  mainImageSourcePaths: string[];
  detailImagePaths: string[];
}

export interface ListingDraftTransactionRow {
  color: string;
  size: string;
  stockingMode: string;
  skc: string;
  sku: string;
  price: number;
  stock: number;
  length: number;
  width: number;
  height: number;
  weightGram: number;
  enabled?: boolean;
}

export interface ListingDraftPreview {
  draftId?: string;
  templateId?: string;
  templateName?: string;
  titlePromptSnapshot?: string;
  mainImagePromptSnapshot?: string;
  materialPackageId?: string;
  status?: ListingDraftStatus;
  shopName: string;
  categoryName: string;
  sourceUrl: string;
  chineseTitle: string;
  englishTitle: string;
  brand: string;
  manufacturer?: string;
  euResponsiblePerson?: string;
  productMainImage?: string;
  productSizeChartImage?: string;
  variantPreviewImages?: string[];
  descriptionImagePaths: string[];
  packageImagePaths?: string[];
  categoryAttributes: Record<string, string>;
  variantAttributes: Record<string, string[]>;
  transactionInfo: ListingDraftTransactionRow[];
}

export type ListingDraftStatus = 'GENERATING' | 'GENERATED' | 'REVIEWING' | 'APPROVED' | 'PUBLISHING' | 'PUBLISHED' | 'FAILED';

export interface ListingDraftResponse {
  draftId: string;
  status: ListingDraftStatus;
  draft: ListingDraftPreview;
  lastErrorType?: string;
  lastErrorMessage?: string;
  publishScreenshotPath?: string;
  createTime?: string;
  updateTime?: string;
}

export interface ListingDraftPageResponse {
  records: ListingDraftResponse[];
  total: number;
  page: number;
  size: number;
}

export interface MabangPublishResult {
  success: boolean;
  message: string;
  finalUrl?: string;
  elapsedMs: number;
  screenshotPath?: string;
}
