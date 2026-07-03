package com.auto.ecommerce.ecommerceauto.localfile.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class LocalDirectoryPickerService {

    public String chooseDirectory() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            throw new UnsupportedOperationException("当前仅支持 macOS 目录选择器");
        }

        try {
            Process process = new ProcessBuilder(List.of(
                    "osascript",
                    "-e",
                    "POSIX path of (choose folder with prompt \"请选择素材包目录\")"
            )).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(error.isBlank() ? "已取消选择目录" : error);
            }
            return requireDirectory(output);
        } catch (IOException e) {
            throw new IllegalStateException("打开目录选择器失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("目录选择被中断", e);
        }
    }

    String requireDirectory(String rawPath) {
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("选择的路径不是目录: " + rawPath);
        }
        return path.toString();
    }
}
