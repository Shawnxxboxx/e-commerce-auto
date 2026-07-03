package com.auto.ecommerce.ecommerceauto.localfile.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDirectoryPickerServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void requiresDirectoryPath() {
        LocalDirectoryPickerService service = new LocalDirectoryPickerService();

        assertThat(service.requireDirectory(tempDir.toString())).isEqualTo(tempDir.toAbsolutePath().toString());
        assertThatThrownBy(() -> service.requireDirectory(tempDir.resolve("missing").toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
