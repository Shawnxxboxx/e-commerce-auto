package com.auto.ecommerce.ecommerceauto.material.controller;

import com.auto.ecommerce.ecommerceauto.material.dto.ParseMaterialPackageRequest;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/api/material-packages")
@RequiredArgsConstructor
public class MaterialPackageController {

    private final MaterialPackageParser parser;

    @PostMapping("/parse")
    public ProductMaterialPackage parse(@RequestBody ParseMaterialPackageRequest request) {
        return parser.parse(Path.of(request.getMaterialPackagePath()));
    }

    @PostMapping(value = "/parse-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductMaterialPackage parseUpload(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择素材包文件");
        }

        try {
            // 保留临时目录，解析结果里的图片路径还要给前端预览使用。
            Path uploadDir = Files.createTempDirectory("material-package-");
            for (MultipartFile file : files) {
                Path target = uploadDir.resolve(safeRelativePath(file.getOriginalFilename())).normalize();
                if (!target.startsWith(uploadDir)) {
                    throw new IllegalArgumentException("文件路径不合法: " + file.getOriginalFilename());
                }
                Files.createDirectories(target.getParent());
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            }
            return parser.parse(findPackageRoot(uploadDir));
        } catch (IOException e) {
            throw new UncheckedIOException("保存素材包失败", e);
        }
    }

    private Path safeRelativePath(String originalFilename) {
        String fileName = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        Path relative = Path.of(fileName).normalize();
        if (fileName.isBlank() || relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("文件路径不合法: " + originalFilename);
        }
        return relative;
    }

    private Path findPackageRoot(Path uploadDir) throws IOException {
        if (Files.isRegularFile(uploadDir.resolve("属性信息.txt"))) {
            return uploadDir;
        }
        try (var stream = Files.list(uploadDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("属性信息.txt")))
                    .findFirst()
                    .orElse(uploadDir);
        }
    }
}
