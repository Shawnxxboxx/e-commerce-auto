package com.auto.ecommerce.ecommerceauto.material.controller;

import com.auto.ecommerce.ecommerceauto.material.dto.ParseMaterialPackageRequest;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/material-packages")
@RequiredArgsConstructor
public class MaterialPackageController {

    private final MaterialPackageParser parser;

    @PostMapping("/parse")
    public ProductMaterialPackage parse(@RequestBody ParseMaterialPackageRequest request) {
        return parser.parse(Path.of(request.getMaterialPackagePath()));
    }
}
