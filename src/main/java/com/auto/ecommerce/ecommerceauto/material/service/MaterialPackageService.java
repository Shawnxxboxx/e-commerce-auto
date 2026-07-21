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

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialPackageService {

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
}
