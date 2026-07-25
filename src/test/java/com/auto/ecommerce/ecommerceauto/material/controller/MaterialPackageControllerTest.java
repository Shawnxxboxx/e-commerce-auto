package com.auto.ecommerce.ecommerceauto.material.controller;

import com.auto.ecommerce.ecommerceauto.material.config.MaterialStorageProperties;
import com.auto.ecommerce.ecommerceauto.material.dto.MaterialPackageResponse;
import com.auto.ecommerce.ecommerceauto.material.entity.MaterialPackageEntity;
import com.auto.ecommerce.ecommerceauto.material.mapper.MaterialPackageMapper;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaterialPackageControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private Path packageRoot;

    @BeforeEach
    void setUp() throws IOException {
        packageRoot = tempDir.resolve("material-1");
        Files.createDirectories(packageRoot.resolve("主图"));
        Files.write(packageRoot.resolve("主图/1.jpg"), new byte[] {1, 2, 3});
        Files.writeString(packageRoot.resolve("属性信息.txt"), "metadata");

        MaterialPackageService service = service(
                mapper(),
                new MaterialPackageStorageService(new MaterialStorageProperties(tempDir, 50L * 1024 * 1024)));
        mockMvc = MockMvcBuilders.standaloneSetup(new MaterialPackageController(service)).build();
    }

    @Test
    void servesStoredImageFromExistingMaterialPackage() throws Exception {
        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "主图/1.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void bindsRepeatedRelativePathsInFileOrder() throws Exception {
        AtomicReference<String> directoryName = new AtomicReference<>();
        AtomicReference<List<String>> fileNames = new AtomicReference<>();
        AtomicReference<List<String>> paths = new AtomicReference<>();
        MaterialPackageService uploadService = new MaterialPackageService(null, null, null, null) {
            @Override
            public MaterialPackageResponse upload(String originalDirectoryName, List<MultipartFile> files,
                                                  List<String> relativePaths) {
                directoryName.set(originalDirectoryName);
                fileNames.set(files.stream().map(MultipartFile::getOriginalFilename).toList());
                paths.set(List.copyOf(relativePaths));
                return new MaterialPackageResponse();
            }
        };
        MockMvc uploadMockMvc = MockMvcBuilders
                .standaloneSetup(new MaterialPackageController(uploadService))
                .build();

        uploadMockMvc.perform(multipart("/api/material-packages")
                        .file(new MockMultipartFile("files", "main.jpg", "image/jpeg", new byte[] {1}))
                        .file(new MockMultipartFile("files", "detail.png", "image/png", new byte[] {2}))
                        .param("originalDirectoryName", "眼镜素材")
                        .param("relativePaths", "主图/main.jpg", "副图/detail.png"))
                .andExpect(status().isOk());

        assertThat(directoryName.get()).isEqualTo("眼镜素材");
        assertThat(fileNames.get()).containsExactly("main.jpg", "detail.png");
        assertThat(paths.get()).containsExactly("主图/main.jpg", "副图/detail.png");
    }

    @Test
    void servesServerGeneratedAiImage() throws Exception {
        Files.createDirectories(packageRoot.resolve("AI生成"));
        Files.write(packageRoot.resolve("AI生成/generated.png"), new byte[] {4, 5, 6});

        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "AI生成/generated.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[] {4, 5, 6}));
    }

    @Test
    void rejectsUnsafeOrUnsupportedPaths() throws Exception {
        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "../../secret.txt"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", tempDir.resolve("secret.jpg").toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "主图/1.txt"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "属性信息.txt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingMaterialFile() throws Exception {
        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "主图/missing.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsImageWhenParentDirectoryIsSymlink() throws Exception {
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.write(outside.resolve("1.jpg"), new byte[] {4, 5, 6});
        Files.delete(packageRoot.resolve("主图/1.jpg"));
        Files.delete(packageRoot.resolve("主图"));
        Files.createSymbolicLink(packageRoot.resolve("主图"), outside);

        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "主图/1.jpg"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsImageWhenFinalFileIsSymlink() throws Exception {
        Path outside = tempDir.resolve("outside.jpg");
        Files.write(outside, new byte[] {4, 5, 6});
        Files.delete(packageRoot.resolve("主图/1.jpg"));
        Files.createSymbolicLink(packageRoot.resolve("主图/1.jpg"), outside);

        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "主图/1.jpg"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingMaterialPackageWithoutResolvingFile() throws Exception {
        AtomicBoolean resolved = new AtomicBoolean();
        MaterialPackageStorageService storage = new MaterialPackageStorageService(
                new MaterialStorageProperties(tempDir, 50L * 1024 * 1024)) {
            @Override
            public Path resolveFile(String materialPackageId, String relativePath) {
                resolved.set(true);
                return super.resolveFile(materialPackageId, relativePath);
            }
        };
        MaterialPackageService service = service(mapper(false), storage);
        MockMvc missingPackageMockMvc = MockMvcBuilders.standaloneSetup(new MaterialPackageController(service)).build();

        missingPackageMockMvc.perform(get("/api/material-packages/missing/files").param("path", "主图/1.jpg"))
                .andExpect(status().isNotFound());

        assertThat(resolved).isFalse();
    }

    @Test
    void rejectsFileSystemWithoutSecureDirectoryStream() throws Exception {
        URI archiveUri = URI.create("jar:" + tempDir.resolve("materials.zip").toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(archiveUri, Map.of("create", "true"))) {
            Path archiveRoot = fileSystem.getPath("/");
            Files.createDirectories(archiveRoot.resolve("material-1/主图"));
            Files.write(archiveRoot.resolve("material-1/主图/1.jpg"), new byte[] {1, 2, 3});
            MaterialPackageStorageService storage = new MaterialPackageStorageService(
                    new MaterialStorageProperties(archiveRoot, 50L * 1024 * 1024)) {
                @Override
                public Path resolveFile(String materialPackageId, String relativePath) {
                    return resolvePackage(materialPackageId).resolve(relativePath).normalize();
                }
            };
            MaterialPackageService service = new MaterialPackageService(
                    mapper(),
                    storage,
                    null,
                    null);

            assertThatThrownBy(() -> service.file("material-1", "主图/1.jpg"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SecureDirectoryStream");
        }
    }

    private static MaterialPackageMapper mapper() {
        return mapper(true);
    }

    private static MaterialPackageService service(MaterialPackageMapper mapper,
                                                  MaterialPackageStorageService storage) {
        return new MaterialPackageService(mapper, storage, null, null) {
            @Override
            protected DirectoryStream<Path> openDirectoryStream(Path directory) {
                return secureDirectoryStream(directory);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static SecureDirectoryStream<Path> secureDirectoryStream(Path directory) {
        return (SecureDirectoryStream<Path>) Proxy.newProxyInstance(
                MaterialPackageControllerTest.class.getClassLoader(),
                new Class<?>[] {SecureDirectoryStream.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "newDirectoryStream" -> {
                        assertThat((LinkOption[]) arguments[1]).contains(LinkOption.NOFOLLOW_LINKS);
                        Path child = directory.resolve((Path) arguments[0]).normalize();
                        if (Files.notExists(child, LinkOption.NOFOLLOW_LINKS)) {
                            throw new NoSuchFileException(child.toString());
                        }
                        if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException("不是安全目录: " + child);
                        }
                        yield secureDirectoryStream(child);
                    }
                    case "newByteChannel" -> {
                        Set<? extends OpenOption> options = (Set<? extends OpenOption>) arguments[1];
                        assertThat(options.stream().anyMatch(LinkOption.NOFOLLOW_LINKS::equals)).isTrue();
                        yield Files.newByteChannel(directory.resolve((Path) arguments[0]).normalize(), options);
                    }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static MaterialPackageMapper mapper(boolean materialPackageExists) {
        return (MaterialPackageMapper) Proxy.newProxyInstance(
                MaterialPackageControllerTest.class.getClassLoader(),
                new Class<?>[] {MaterialPackageMapper.class},
                (proxy, method, arguments) -> {
                    if ("selectOne".equals(method.getName())) {
                        if (!materialPackageExists) {
                            return null;
                        }
                        MaterialPackageEntity entity = new MaterialPackageEntity();
                        entity.setMaterialPackageId("material-1");
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
