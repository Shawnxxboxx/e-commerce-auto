package com.auto.ecommerce.ecommerceauto.localfile.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageFileServiceTest {

    @TempDir
    Path tempDir;

    private final LocalImageFileService service = new LocalImageFileService();

    @Test
    void readsSupportedImageFile() throws Exception {
        Path image = tempDir.resolve("main.jpg");
        Files.writeString(image, "image");

        LocalImageFileService.LocalImageFile result = service.readImage(image.toString());

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.bytes()).isEqualTo("image".getBytes());
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        Path text = tempDir.resolve("note.txt");
        Files.writeString(text, "text");

        assertThatThrownBy(() -> service.readImage(text.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 jpg、jpeg、png 图片");
    }
}
