package com.auto.ecommerce.ecommerceauto.localfile.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class LocalImageFileService {

    public LocalImageFile readImage(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new InvalidImagePathException("图片路径不能为空");
        }

        Path path;
        try {
            path = Path.of(rawPath);
        } catch (InvalidPathException e) {
            throw new InvalidImagePathException("图片路径不合法: " + rawPath, e);
        }
        if (!Files.isRegularFile(path)) {
            throw new ImageFileNotFoundException("图片文件不存在: " + path);
        }

        String contentType = contentType(path);
        try {
            return new LocalImageFile(contentType, Files.readAllBytes(path));
        } catch (IOException e) {
            throw new ImageReadException("读取图片失败: " + path, e);
        }
    }

    private String contentType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        throw new InvalidImagePathException("仅支持 jpg、jpeg、png 图片");
    }

    public record LocalImageFile(String contentType, byte[] bytes) {
    }

    public static class InvalidImagePathException extends IllegalArgumentException {

        public InvalidImagePathException(String message) {
            super(message);
        }

        public InvalidImagePathException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ImageFileNotFoundException extends IllegalArgumentException {

        public ImageFileNotFoundException(String message) {
            super(message);
        }
    }

    public static class ImageReadException extends UncheckedIOException {

        public ImageReadException(String message, IOException cause) {
            super(message, cause);
        }
    }
}
