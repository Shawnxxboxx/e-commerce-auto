package com.auto.ecommerce.ecommerceauto.material.service;

import com.auto.ecommerce.ecommerceauto.material.config.MaterialStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialPackageStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAllowedFilesAndPreservesRelativePaths() {
        MaterialPackageStorageService service = storageService();

        Path stored = service.store("material-1",
                List.of(file("属性信息.txt", "[产品信息]"), file("1.jpg", "image")),
                List.of("属性信息.txt", "主图/1.jpg"));

        assertThat(stored.resolve("属性信息.txt")).exists();
        assertThat(stored.resolve("主图/1.jpg")).exists();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret.txt", "/tmp/secret.txt", "主图/../../secret.jpg", "其他/1.jpg"})
    void rejectsUnsafeOrUnsupportedPaths(String relativePath) {
        MaterialPackageStorageService service = storageService();

        assertThatThrownBy(() -> service.store("material-1", List.of(file("x", "x")), List.of(relativePath)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"主图/a.txt", "副图/a.gif", "尺码表/nested/a.jpg"})
    void rejectsNonImageFilesAndNestedDirectories(String relativePath) {
        MaterialPackageStorageService service = storageService();

        assertThatThrownBy(() -> service.store("material-1", List.of(file("x", "x")), List.of(relativePath)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPackageLargerThanFiftyMegabytes() {
        MaterialPackageStorageService service = storageService();
        MultipartFile oversized = new MockMultipartFile(
                "files", "1.jpg", "image/jpeg", new byte[50 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.store("material-1", List.of(oversized), List.of("主图/1.jpg")))
                .hasMessageContaining("50MB");
    }

    @Test
    void removesTemporaryPackageWhenWritingFails() {
        MaterialPackageStorageService service = storageService();

        assertThatThrownBy(() -> service.store("material-1",
                List.of(file("属性信息.txt", "[产品信息]"), failingFile()),
                List.of("属性信息.txt", "主图/1.jpg")))
                .isInstanceOf(RuntimeException.class);

        assertThat(tempDir.resolve(".tmp/material-1")).doesNotExist();
        assertThat(tempDir.resolve("material-1")).doesNotExist();
    }

    private static MaterialStorageProperties properties(Path root) {
        return new MaterialStorageProperties(root, 50L * 1024 * 1024);
    }

    private MaterialPackageStorageService storageService() {
        return new MaterialPackageStorageService(properties(tempDir));
    }

    private static MultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content.getBytes());
    }

    private static MultipartFile failingFile() {
        return new MockMultipartFile("files", "1.jpg", "image/jpeg", new byte[] {1}) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("simulated write failure");
            }
        };
    }
}
