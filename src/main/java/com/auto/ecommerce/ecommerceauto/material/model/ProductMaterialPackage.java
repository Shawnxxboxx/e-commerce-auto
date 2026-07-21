package com.auto.ecommerce.ecommerceauto.material.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ProductMaterialPackage {
    private String materialPackageId;
    private String originalDirectoryName;
    private String materialPackagePath;
    private Integer fileCount;
    private Long totalSize;
    private String productName;
    private String sourceUrl;
    private String shopName;
    private String categoryName;
    private String brand;
    private String manufacturer;
    private String euResponsiblePerson;
    private Map<String, String> categoryAttributes = new LinkedHashMap<>();
    private Map<String, String> variantAttributes = new LinkedHashMap<>();
    private String sizeChartImageName;
    private String sizeChartImagePath;
    private List<String> sizeChartImagePaths = new ArrayList<>();
    private List<MaterialTransactionRow> transactionRows = new ArrayList<>();
    private List<String> mainImageSourcePaths = new ArrayList<>();
    private List<String> detailImagePaths = new ArrayList<>();
    private List<String> packageImagePaths = new ArrayList<>();
}
