package com.auto.ecommerce.ecommerceauto.material.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "material.storage")
public record MaterialStorageProperties(Path root, long maxPackageSize) {
}
