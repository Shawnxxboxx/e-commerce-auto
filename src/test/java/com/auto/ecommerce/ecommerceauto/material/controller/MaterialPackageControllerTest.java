package com.auto.ecommerce.ecommerceauto.material.controller;

import com.auto.ecommerce.ecommerceauto.material.config.MaterialStorageProperties;
import com.auto.ecommerce.ecommerceauto.material.entity.MaterialPackageEntity;
import com.auto.ecommerce.ecommerceauto.material.mapper.MaterialPackageMapper;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaterialPackageControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        Path packageRoot = tempDir.resolve("material-1");
        Files.createDirectories(packageRoot.resolve("主图"));
        Files.write(packageRoot.resolve("主图/1.jpg"), new byte[] {1, 2, 3});
        Files.writeString(packageRoot.resolve("属性信息.txt"), "metadata");

        MaterialPackageService service = new MaterialPackageService(
                mapper(),
                new MaterialPackageStorageService(new MaterialStorageProperties(tempDir, 50L * 1024 * 1024)),
                null,
                null);
        mockMvc = MockMvcBuilders.standaloneSetup(new MaterialPackageController(service)).build();
    }

    @Test
    void servesStoredImageFromExistingMaterialPackage() throws Exception {
        mockMvc.perform(get("/api/material-packages/material-1/files").param("path", "主图/1.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
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

    private static MaterialPackageMapper mapper() {
        return (MaterialPackageMapper) Proxy.newProxyInstance(
                MaterialPackageControllerTest.class.getClassLoader(),
                new Class<?>[] {MaterialPackageMapper.class},
                (proxy, method, arguments) -> {
                    if ("selectOne".equals(method.getName())) {
                        MaterialPackageEntity entity = new MaterialPackageEntity();
                        entity.setMaterialPackageId("material-1");
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
