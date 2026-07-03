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
  sizeChartImagePaths?: string[];
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
  transactionInfo: ListingDraftTransactionRow[];
}
