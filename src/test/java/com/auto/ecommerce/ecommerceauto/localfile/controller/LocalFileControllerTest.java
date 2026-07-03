package com.auto.ecommerce.ecommerceauto.localfile.controller;

import com.auto.ecommerce.ecommerceauto.localfile.service.LocalImageFileService;
import com.auto.ecommerce.ecommerceauto.localfile.service.LocalDirectoryPickerService;
import com.auto.ecommerce.ecommerceauto.localfile.service.LocalImageFileService.ImageReadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalFileControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalImageFileService service = new LocalImageFileService();
        mockMvc = MockMvcBuilders.standaloneSetup(new LocalFileController(service, new LocalDirectoryPickerService())).build();
    }

    @Test
    void returnsBadRequestForUnsupportedExtension() throws Exception {
        Path text = tempDir.resolve("note.txt");
        Files.writeString(text, "text");

        mockMvc.perform(get("/api/local-files/image").queryParam("path", text.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestForMalformedPath() throws Exception {
        mockMvc.perform(get("/api/local-files/image").queryParam("path", "bad\0path.jpg"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingFile() throws Exception {
        Path missing = tempDir.resolve("missing.jpg");

        mockMvc.perform(get("/api/local-files/image").queryParam("path", missing.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsInternalServerErrorForReadFailure() throws Exception {
        LocalImageFileService service = new LocalImageFileService() {
            @Override
            public LocalImageFile readImage(String rawPath) {
                throw new ImageReadException("读取图片失败: " + rawPath, new IOException("boom"));
            }
        };
        MockMvc failingMockMvc = MockMvcBuilders.standaloneSetup(new LocalFileController(service, new LocalDirectoryPickerService())).build();

        failingMockMvc.perform(get("/api/local-files/image").queryParam("path", "/tmp/main.jpg"))
                .andExpect(status().isInternalServerError());
    }
}
