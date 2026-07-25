package com.auto.ecommerce.ecommerceauto.material.service;

import com.auto.ecommerce.ecommerceauto.material.dto.MaterialPackageResponse;
import com.auto.ecommerce.ecommerceauto.material.entity.MaterialPackageEntity;
import com.auto.ecommerce.ecommerceauto.material.mapper.MaterialPackageMapper;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialPackageService {

    private static final Set<String> ALLOWED_IMAGE_DIRECTORIES =
            Set.of("主图", "副图", "尺码表", "包装图", "AI生成");

    private final MaterialPackageMapper mapper;
    private final MaterialPackageStorageService storage;
    private final MaterialPackageParser parser;
    private final ObjectMapper objectMapper;

    public MaterialPackageResponse upload(String originalDirectoryName, List<MultipartFile> files,
                                          List<String> relativePaths) {
        String materialPackageId = "material-" + UUID.randomUUID();
        Path storedPath = storage.store(materialPackageId, files, relativePaths);
        try {
            ProductMaterialPackage parsedMaterial = parser.parse(storedPath);
            parsedMaterial.setMaterialPackageId(materialPackageId);
            parsedMaterial.setOriginalDirectoryName(originalDirectoryName);
            parsedMaterial.setFileCount(files.size());
            parsedMaterial.setTotalSize(totalSize(files));

            MaterialPackageResponse response = toResponse(parsedMaterial, storedPath);
            MaterialPackageEntity entity = toEntity(parsedMaterial, storedPath);
            if (mapper.insert(entity) != 1) {
                throw new IllegalStateException("素材包数据库写入失败");
            }
            return response;
        } catch (RuntimeException exception) {
            deleteStoredPackage(materialPackageId, exception);
            throw exception;
        }
    }

    public MaterialPackageEntity require(String materialPackageId) {
        MaterialPackageEntity entity = mapper.selectOne(new LambdaQueryWrapper<MaterialPackageEntity>()
                .eq(MaterialPackageEntity::getMaterialPackageId, materialPackageId)
                .last("limit 1"));
        if (entity == null) {
            throw new IllegalArgumentException("素材包不存在: " + materialPackageId);
        }
        return entity;
    }

    public Path packagePath(String materialPackageId) {
        return storage.resolvePackage(materialPackageId);
    }

    public void quarantine(String materialPackageId) {
        storage.quarantine(materialPackageId);
    }

    public void restore(String materialPackageId) {
        storage.restore(materialPackageId);
    }

    public void purgeQuarantine(String materialPackageId) {
        storage.purgeQuarantine(materialPackageId);
    }

    public void delete(String materialPackageId) {
        if (mapper.delete(new LambdaQueryWrapper<MaterialPackageEntity>()
                .eq(MaterialPackageEntity::getMaterialPackageId, materialPackageId)) != 1) {
            throw new IllegalStateException("素材包数据库删除失败: " + materialPackageId);
        }
    }

    public MaterialFile file(String materialPackageId, String relativePath) {
        try {
            require(materialPackageId);
        } catch (IllegalArgumentException exception) {
            throw new MaterialFileNotFoundException("素材包不存在: " + materialPackageId);
        }

        Path packageRoot;
        Path path;
        try {
            packageRoot = storage.resolvePackage(materialPackageId);
            path = storage.resolveFile(materialPackageId, relativePath);
        } catch (IllegalArgumentException exception) {
            throw new InvalidMaterialFileException(exception.getMessage());
        }
        Path relative = packageRoot.relativize(path);
        if (!path.startsWith(packageRoot)
                || relative.getNameCount() != 2
                || !ALLOWED_IMAGE_DIRECTORIES.contains(relative.getName(0).toString())
                || !isImage(relative)) {
            throw new InvalidMaterialFileException("非法素材图片路径: " + relativePath);
        }
        return new MaterialFile(readSecurely(packageRoot, relative, relativePath), contentType(path));
    }

    private MaterialPackageEntity toEntity(ProductMaterialPackage material, Path storedPath) {
        LocalDateTime now = LocalDateTime.now();
        MaterialPackageEntity entity = new MaterialPackageEntity();
        entity.setMaterialPackageId(material.getMaterialPackageId());
        entity.setOriginalDirectoryName(material.getOriginalDirectoryName());
        entity.setStoragePath(storedPath.toAbsolutePath().normalize().toString());
        entity.setParsedJson(toJson(material));
        entity.setFileCount(material.getFileCount());
        entity.setTotalSize(material.getTotalSize());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    private MaterialPackageResponse toResponse(ProductMaterialPackage material, Path storedPath) {
        Path packageRoot = storedPath.toAbsolutePath().normalize();
        MaterialPackageResponse response = new MaterialPackageResponse();
        BeanUtils.copyProperties(material, response);
        response.setMaterialPackagePath(".");
        response.setMainImageSourcePaths(relativePaths(packageRoot, material.getMainImageSourcePaths()));
        response.setDetailImagePaths(relativePaths(packageRoot, material.getDetailImagePaths()));
        response.setSizeChartImagePath(relativePath(packageRoot, material.getSizeChartImagePath()));
        response.setSizeChartImagePaths(relativePaths(packageRoot, material.getSizeChartImagePaths()));
        response.setPackageImagePaths(relativePaths(packageRoot, material.getPackageImagePaths()));
        return response;
    }

    private long totalSize(List<MultipartFile> files) {
        long totalSize = 0;
        for (MultipartFile file : files) {
            totalSize = Math.addExact(totalSize, file.getSize());
        }
        return totalSize;
    }

    private String contentType(Path path) {
        try {
            String detected = Files.probeContentType(path);
            if ("image/jpeg".equals(detected) || "image/png".equals(detected)) {
                return detected;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取素材图片类型失败: " + path, exception);
        }

        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")
                ? "image/png"
                : "image/jpeg";
    }

    private boolean isImage(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
    }

    private byte[] readSecurely(Path packageRoot, Path relative, String requestedPath) {
        Path absolutePath = packageRoot.resolve(relative).toAbsolutePath().normalize();
        try {
            Path realPath = absolutePath.toRealPath();
            if (!realPath.startsWith(packageRoot.toRealPath())) {
                throw new InvalidMaterialFileException("非法素材图片路径（路径穿越）: " + requestedPath);
            }
            try (InputStream input = Files.newInputStream(realPath)) {
                return input.readAllBytes();
            }
        } catch (NoSuchFileException exception) {
            throw new MaterialFileNotFoundException("素材图片不存在: " + requestedPath);
        } catch (IOException exception) {
            throw new InvalidMaterialFileException("非法素材图片路径: " + requestedPath, exception);
        }
    }


    private List<String> relativePaths(Path packageRoot, List<String> imagePaths) {
        if (imagePaths == null) {
            return null;
        }
        return imagePaths.stream().map(imagePath -> relativePath(packageRoot, imagePath)).toList();
    }

    private String relativePath(Path packageRoot, String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return imagePath;
        }
        Path image = Path.of(imagePath).toAbsolutePath().normalize();
        if (!image.startsWith(packageRoot)) {
            throw new IllegalArgumentException("素材图片路径不在素材包目录内: " + imagePath);
        }
        return packageRoot.relativize(image).toString();
    }

    private void deleteStoredPackage(String materialPackageId, RuntimeException original) {
        try {
            storage.deletePackage(materialPackageId);
        } catch (RuntimeException cleanupException) {
            original.addSuppressed(cleanupException);
        }
    }

    private String toJson(ProductMaterialPackage material) {
        try {
            return objectMapper.writeValueAsString(material);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("素材包 JSON 序列化失败", exception);
        }
    }

    public record MaterialFile(byte[] content, String contentType) {
    }

    public static class InvalidMaterialFileException extends IllegalArgumentException {
        public InvalidMaterialFileException(String message) {
            super(message);
        }

        public InvalidMaterialFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class MaterialFileNotFoundException extends IllegalArgumentException {
        public MaterialFileNotFoundException(String message) {
            super(message);
        }
    }
}
