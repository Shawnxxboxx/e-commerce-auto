package com.auto.ecommerce.ecommerceauto.material.controller;

import com.auto.ecommerce.ecommerceauto.material.dto.MaterialPackageResponse;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/material-packages")
@RequiredArgsConstructor
public class MaterialPackageController {

    private final MaterialPackageService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialPackageResponse upload(@RequestPart String originalDirectoryName,
                                          @RequestPart List<MultipartFile> files,
                                          @RequestPart List<String> relativePaths) {
        return service.upload(originalDirectoryName, files, relativePaths);
    }
}
