import { useState } from 'react';
import { Empty, Image, Typography } from 'antd';
import { localImageUrl, materialImageUrl } from '../api/client';

interface ImagePathPreviewProps {
  materialPackageId?: string;
  paths: string[];
  emptyText?: string;
}

function fileName(path: string): string {
  return path.split(/[\\/]/).pop() || path;
}

export function ImagePathPreview({ materialPackageId, paths, emptyText = '暂无图片' }: ImagePathPreviewProps) {
  const [failedPaths, setFailedPaths] = useState<Set<string>>(() => new Set());
  const imagePaths = paths.filter(Boolean);

  if (imagePaths.length === 0) {
    return <Empty description={emptyText} />;
  }

  const markFailed = (path: string) => {
    setFailedPaths((current) => new Set(current).add(path));
  };

  return (
    <Image.PreviewGroup>
      <div className="image-grid">
        {imagePaths.map((path, index) => {
          const failed = failedPaths.has(path);

          return (
            <div key={`${path}-${index}`}>
              <div className="image-preview">
                {failed ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="加载失败" />
                ) : (
                  <Image
                    src={materialPackageId ? materialImageUrl(materialPackageId, path) : localImageUrl(path)}
                    alt={fileName(path)}
                    onError={() => markFailed(path)}
                    preview={{ mask: '点击放大' }}
                  />
                )}
              </div>
              <Typography.Text type="secondary" ellipsis title={fileName(path)}>
                {fileName(path)}
              </Typography.Text>
            </div>
          );
        })}
      </div>
    </Image.PreviewGroup>
  );
}

export default ImagePathPreview;
