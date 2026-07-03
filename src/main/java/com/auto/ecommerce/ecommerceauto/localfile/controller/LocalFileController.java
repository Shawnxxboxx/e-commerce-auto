package com.auto.ecommerce.ecommerceauto.localfile.controller;

import com.auto.ecommerce.ecommerceauto.localfile.service.LocalImageFileService;
import com.auto.ecommerce.ecommerceauto.localfile.service.LocalDirectoryPickerService;
import com.auto.ecommerce.ecommerceauto.localfile.service.LocalImageFileService.ImageFileNotFoundException;
import com.auto.ecommerce.ecommerceauto.localfile.service.LocalImageFileService.ImageReadException;
import com.auto.ecommerce.ecommerceauto.localfile.service.LocalImageFileService.InvalidImagePathException;
import com.auto.ecommerce.ecommerceauto.localfile.service.LocalImageFileService.LocalImageFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/local-files")
@RequiredArgsConstructor
public class LocalFileController {

    private final LocalImageFileService localImageFileService;
    private final LocalDirectoryPickerService localDirectoryPickerService;

    @GetMapping("/image")
    public ResponseEntity<byte[]> image(@RequestParam String path) {
        LocalImageFile image = localImageFileService.readImage(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                .body(image.bytes());
    }

    @GetMapping("/choose-directory")
    public DirectorySelection chooseDirectory() {
        return new DirectorySelection(localDirectoryPickerService.chooseDirectory());
    }

    public record DirectorySelection(String path) {
    }

    @ExceptionHandler(InvalidImagePathException.class)
    public ResponseEntity<String> badRequest(InvalidImagePathException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(ImageFileNotFoundException.class)
    public ResponseEntity<String> notFound(ImageFileNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(ImageReadException.class)
    public ResponseEntity<String> readFailed(ImageReadException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, UnsupportedOperationException.class})
    public ResponseEntity<String> directoryPickerFailed(RuntimeException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
