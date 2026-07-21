package com.auto.ecommerce.ecommerceauto.material.service;

import com.auto.ecommerce.ecommerceauto.material.config.MaterialStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@EnableConfigurationProperties(MaterialStorageProperties.class)
public class MaterialPackageStorageService {

    private static final Set<String> ALLOWED_DIRECTORIES = Set.of("主图", "副图", "尺码表", "包装图");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> RESERVED_PACKAGE_IDS = Set.of(".tmp", ".trash");

    private final Path root;
    private final long maxPackageSize;

    public MaterialPackageStorageService(MaterialStorageProperties properties) {
        this.root = Objects.requireNonNull(properties.root(), "material.storage.root 不能为空")
                .toAbsolutePath()
                .normalize();
        this.maxPackageSize = properties.maxPackageSize();
    }

    public Path store(String materialPackageId, List<MultipartFile> files, List<String> relativePaths) {
        Objects.requireNonNull(files, "files 不能为空");
        Objects.requireNonNull(relativePaths, "relativePaths 不能为空");
        if (files.size() != relativePaths.size()) {
            throw new IllegalArgumentException("文件数量与相对路径数量不一致");
        }

        Path packageRoot = resolvePackage(materialPackageId);
        Set<Path> targets = new HashSet<>();
        long totalSize = 0;
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = Objects.requireNonNull(files.get(index), "文件不能为空");
            Path target = checkedTarget(packageRoot, relativePaths.get(index));
            if (!targets.add(target)) {
                throw new IllegalArgumentException("素材路径重复: " + relativePaths.get(index));
            }
            totalSize = Math.addExact(totalSize, file.getSize());
            if (totalSize > maxPackageSize) {
                throw new IllegalArgumentException("素材包大小不能超过50MB");
            }
        }

        Path temporaryRoot = root.resolve(".tmp").resolve(checkedPackageId(materialPackageId));
        try {
            Files.createDirectories(root);
            deleteRecursively(temporaryRoot);
            Files.createDirectories(temporaryRoot);

            for (int index = 0; index < files.size(); index++) {
                Path target = checkedTarget(temporaryRoot, relativePaths.get(index));
                Files.createDirectories(target.getParent());
                try (InputStream input = files.get(index).getInputStream()) {
                    Files.copy(input, target);
                }
            }
            return Files.move(temporaryRoot, packageRoot);
        } catch (IOException exception) {
            cleanUpTemporaryRoot(temporaryRoot, exception);
            throw new UncheckedIOException("保存素材包失败: " + materialPackageId, exception);
        } catch (RuntimeException exception) {
            cleanUpTemporaryRoot(temporaryRoot, exception);
            throw exception;
        }
    }

    public Path resolvePackage(String materialPackageId) {
        return root.resolve(checkedPackageId(materialPackageId)).normalize();
    }

    public Path resolveFile(String materialPackageId, String relativePath) {
        return checkedTarget(resolvePackage(materialPackageId), relativePath);
    }

    public void deletePackage(String materialPackageId) {
        delete(resolvePackage(materialPackageId), "删除素材包失败: " + materialPackageId);
    }

    public Path quarantine(String materialPackageId) {
        Path source = resolvePackage(materialPackageId);
        Path target = root.resolve(".trash").resolve(checkedPackageId(materialPackageId));
        return move(source, target, "隔离素材包失败: " + materialPackageId);
    }

    public void restore(String materialPackageId) {
        Path source = root.resolve(".trash").resolve(checkedPackageId(materialPackageId));
        move(source, resolvePackage(materialPackageId), "恢复素材包失败: " + materialPackageId);
    }

    public void purgeQuarantine(String materialPackageId) {
        delete(root.resolve(".trash").resolve(checkedPackageId(materialPackageId)),
                "清理素材包隔离区失败: " + materialPackageId);
    }

    private Path checkedTarget(Path packageRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("非法素材路径: " + relativePath);
        }
        Path relative = Path.of(relativePath).normalize();
        if (relative.isAbsolute() || relative.startsWith("..") || !isAllowed(relative)) {
            throw new IllegalArgumentException("非法素材路径: " + relativePath);
        }
        Path target = packageRoot.resolve(relative).normalize();
        if (!target.startsWith(packageRoot)) {
            throw new IllegalArgumentException("素材路径越界: " + relativePath);
        }
        return target;
    }

    private boolean isAllowed(Path relative) {
        if (relative.getNameCount() == 1) {
            return "属性信息.txt".equals(relative.getFileName().toString());
        }
        if (relative.getNameCount() != 2 || !ALLOWED_DIRECTORIES.contains(relative.getName(0).toString())) {
            return false;
        }
        String fileName = relative.getFileName().toString();
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart > 0
                && ALLOWED_IMAGE_EXTENSIONS.contains(fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT));
    }

    private String checkedPackageId(String materialPackageId) {
        if (materialPackageId == null || materialPackageId.isBlank()) {
            throw new IllegalArgumentException("非法素材包 ID: " + materialPackageId);
        }
        Path packageId = Path.of(materialPackageId).normalize();
        if (packageId.isAbsolute() || packageId.getNameCount() != 1
                || ".".equals(packageId.toString()) || "..".equals(packageId.toString())
                || RESERVED_PACKAGE_IDS.contains(packageId.toString())) {
            throw new IllegalArgumentException("非法素材包 ID: " + materialPackageId);
        }
        return packageId.toString();
    }

    private Path move(Path source, Path target, String message) {
        try {
            Files.createDirectories(target.getParent());
            return Files.move(source, target);
        } catch (IOException exception) {
            throw new UncheckedIOException(message, exception);
        }
    }

    private void delete(Path path, String message) {
        try {
            deleteRecursively(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(message, exception);
        }
    }

    private void cleanUpTemporaryRoot(Path temporaryRoot, Exception original) {
        try {
            deleteRecursively(temporaryRoot);
        } catch (IOException cleanupException) {
            original.addSuppressed(cleanupException);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
