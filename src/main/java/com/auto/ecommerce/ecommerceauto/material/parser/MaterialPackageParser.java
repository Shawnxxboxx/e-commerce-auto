package com.auto.ecommerce.ecommerceauto.material.parser;

import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MaterialPackageParser {

    private static final String MAIN_IMAGE_DIR = "主图";
    private static final String DETAIL_IMAGE_DIR = "副图";
    private static final String SIZE_CHART_DIR = "尺码表";
    private static final String PACKAGE_IMAGE_DIR = "包装图";
    private static final String ATTRIBUTE_INFO_FILE = "属性信息.txt";

    private final AttributeInfoTextParser attributeInfoTextParser;

    public ProductMaterialPackage parse(Path materialPackagePath) {
        Path packagePath = materialPackagePath.toAbsolutePath().normalize();
        requireDirectory(packagePath, "素材包目录");

        Path attributeInfoPath = packagePath.resolve(ATTRIBUTE_INFO_FILE);
        if (!Files.isRegularFile(attributeInfoPath)) {
            throw new IllegalArgumentException("缺少 " + ATTRIBUTE_INFO_FILE + ": " + attributeInfoPath);
        }

        try {
            ProductMaterialPackage material = attributeInfoTextParser.parse(Files.readString(attributeInfoPath, StandardCharsets.UTF_8));
            material.setMaterialPackagePath(packagePath.toString());
            material.setMainImageSourcePaths(listImages(packagePath.resolve(MAIN_IMAGE_DIR)));
            material.setDetailImagePaths(listImages(packagePath.resolve(DETAIL_IMAGE_DIR)));
            material.setPackageImagePaths(listImages(packagePath.resolve(PACKAGE_IMAGE_DIR)));
            fillSizeChartPath(packagePath, material);
            return material;
        } catch (IOException e) {
            throw new UncheckedIOException("读取属性信息失败: " + attributeInfoPath, e);
        }
    }

    private void fillSizeChartPath(Path packagePath, ProductMaterialPackage material) {
        Path sizeChartDir = packagePath.resolve(SIZE_CHART_DIR);
        if (Files.isDirectory(sizeChartDir)) {
            List<String> sizeChartImages = listImages(sizeChartDir);
            material.setSizeChartImagePaths(sizeChartImages);
            if (material.getSizeChartImagePath() == null && !sizeChartImages.isEmpty()) {
                material.setSizeChartImagePath(sizeChartImages.getFirst());
            }
        }

        String sizeChartImageName = material.getSizeChartImageName();
        if (sizeChartImageName == null || sizeChartImageName.isBlank()) {
            return;
        }
        Path sizeChartImagePath = checkedSizeChartImagePath(sizeChartDir, sizeChartImageName);
        if (!Files.isRegularFile(sizeChartImagePath)) {
            throw new IllegalArgumentException("尺码表图片不存在: " + sizeChartImagePath);
        }
        material.setSizeChartImagePath(sizeChartImagePath.toString());
    }

    private Path checkedSizeChartImagePath(Path sizeChartDir, String imageName) {
        Path relative = Path.of(imageName).normalize();
        if (relative.isAbsolute() || relative.getNameCount() != 1
                || imageName.contains("/") || imageName.contains("\\")
                || ".".equals(relative.toString()) || "..".equals(relative.toString())
                || !isSupportedImage(relative)) {
            throw new IllegalArgumentException("非法尺码表图片名称: " + imageName);
        }
        Path resolved = sizeChartDir.resolve(relative).normalize();
        if (!resolved.startsWith(sizeChartDir)) {
            throw new IllegalArgumentException("非法尺码表图片名称: " + imageName);
        }
        return resolved;
    }

    private List<String> listImages(Path directory) {
        requireDirectory(directory, directory.getFileName().toString());
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedImage)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("读取图片目录失败: " + directory, e);
        }
    }

    private boolean isSupportedImage(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
    }

    private void requireDirectory(Path directory, String name) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException(name + "不存在或不是目录: " + directory);
        }
    }
}
