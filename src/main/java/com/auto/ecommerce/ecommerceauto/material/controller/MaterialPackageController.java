package com.auto.ecommerce.ecommerceauto.material.controller;

import com.auto.ecommerce.ecommerceauto.material.dto.MaterialPackageResponse;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService.InvalidMaterialFileException;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService.MaterialFile;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService.MaterialFileNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/material-packages")
@RequiredArgsConstructor
public class MaterialPackageController {

    private final MaterialPackageService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialPackageResponse upload(
            @RequestParam(name = "originalDirectoryName") String originalDirectoryName,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(name = "relativePaths") List<String> relativePaths) {
        return service.upload(originalDirectoryName, files, relativePaths);
    }

    @GetMapping("/{materialPackageId}/files")
    public ResponseEntity<byte[]> file(@PathVariable String materialPackageId, @RequestParam String path) {
        MaterialFile materialFile = service.file(materialPackageId, path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(materialFile.contentType()))
                .body(materialFile.content());
    }

    @ExceptionHandler(InvalidMaterialFileException.class)
    public ResponseEntity<String> badRequest(InvalidMaterialFileException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(MaterialFileNotFoundException.class)
    public ResponseEntity<String> notFound(MaterialFileNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }
}
