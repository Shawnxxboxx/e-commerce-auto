package com.auto.ecommerce.ecommerceauto.material.parser;

import com.auto.ecommerce.ecommerceauto.material.model.MaterialTransactionRow;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AttributeInfoTextParser {

    private static final String PRODUCT_INFO = "产品信息";
    private static final String CATEGORY_ATTRIBUTES = "分类属性";
    private static final String VARIANT_ATTRIBUTES = "变种属性";
    private static final String TRANSACTION_INFO = "交易信息";
    private static final List<String> TRANSACTION_HEADER = List.of(
            "颜色", "规格", "备货模式", "SKC货号", "SKU货号", "不含税价", "库存", "长", "宽", "高", "重量g"
    );

    public ProductMaterialPackage parse(String text) {
        Map<String, List<String>> sections = readSections(text);
        requireSection(sections, PRODUCT_INFO);
        requireSection(sections, CATEGORY_ATTRIBUTES);
        requireSection(sections, VARIANT_ATTRIBUTES);
        requireSection(sections, TRANSACTION_INFO);

        ProductMaterialPackage material = new ProductMaterialPackage();
        Map<String, String> productInfo = parseKeyValues(sections.get(PRODUCT_INFO), PRODUCT_INFO);
        material.setProductName(requiredValue(productInfo, "产品名称", PRODUCT_INFO));
        material.setSourceUrl(productInfo.getOrDefault("来源URL", ""));
        material.setShopName(requiredValue(productInfo, "店铺", PRODUCT_INFO));
        material.setCategoryName(requiredValue(productInfo, "类目", PRODUCT_INFO));
        material.setBrand(requiredValue(productInfo, "品牌", PRODUCT_INFO));
        material.setCategoryAttributes(parseKeyValues(sections.get(CATEGORY_ATTRIBUTES), CATEGORY_ATTRIBUTES));

        Map<String, String> variantAttributes = parseKeyValues(sections.get(VARIANT_ATTRIBUTES), VARIANT_ATTRIBUTES);
        material.setSizeChartImageName(variantAttributes.remove("尺码表图片"));
        material.setVariantAttributes(variantAttributes);
        material.setTransactionRows(parseTransactionRows(sections.get(TRANSACTION_INFO)));
        return material;
    }

    private Map<String, List<String>> readSections(String text) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String currentSection = null;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1).trim();
                sections.putIfAbsent(currentSection, new ArrayList<>());
                continue;
            }
            if (currentSection == null) {
                throw new IllegalArgumentException("属性信息存在未归属分段的内容: " + line);
            }
            sections.get(currentSection).add(line);
        }
        return sections;
    }

    private void requireSection(Map<String, List<String>> sections, String sectionName) {
        if (!sections.containsKey(sectionName)) {
            throw new IllegalArgumentException("缺少必需分段: [" + sectionName + "]");
        }
    }

    private Map<String, String> parseKeyValues(List<String> lines, String sectionName) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.indexOf('=');
            if (separator < 0) {
                throw new IllegalArgumentException("[" + sectionName + "] 字段必须使用 key=value 格式: " + line);
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("[" + sectionName + "] 存在空字段名");
            }
            values.put(key, value);
        }
        return values;
    }

    private String requiredValue(Map<String, String> values, String key, String sectionName) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("[" + sectionName + "] 缺少必填字段: " + key);
        }
        return value;
    }

    private List<MaterialTransactionRow> parseTransactionRows(List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("[交易信息] 缺少表头");
        }
        List<String> header = splitTableLine(lines.getFirst());
        if (!TRANSACTION_HEADER.equals(header)) {
            throw new IllegalArgumentException("交易信息表头不符合约定，应为: " + String.join("|", TRANSACTION_HEADER));
        }

        List<MaterialTransactionRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> columns = splitTableLine(lines.get(i));
            if (columns.size() != TRANSACTION_HEADER.size()) {
                throw new IllegalArgumentException("交易信息第 " + (i + 1) + " 行列数不匹配");
            }
            rows.add(toTransactionRow(columns, i + 1));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("[交易信息] 至少需要一行交易数据");
        }
        return rows;
    }

    private List<String> splitTableLine(String line) {
        return Arrays.stream(line.split("\\|", -1))
                .map(String::trim)
                .toList();
    }

    private MaterialTransactionRow toTransactionRow(List<String> columns, int rowNumber) {
        MaterialTransactionRow row = new MaterialTransactionRow();
        row.setColor(requiredColumn(columns, 0, rowNumber));
        row.setSpecification(requiredColumn(columns, 1, rowNumber));
        row.setStockingMode(requiredColumn(columns, 2, rowNumber));
        row.setSkc(requiredColumn(columns, 3, rowNumber));
        row.setSku(requiredColumn(columns, 4, rowNumber));
        row.setPrice(parseDecimal(requiredColumn(columns, 5, rowNumber), "不含税价", rowNumber));
        row.setStock(parseInteger(requiredColumn(columns, 6, rowNumber), "库存", rowNumber));
        row.setLength(parseDecimal(requiredColumn(columns, 7, rowNumber), "长", rowNumber));
        row.setWidth(parseDecimal(requiredColumn(columns, 8, rowNumber), "宽", rowNumber));
        row.setHeight(parseDecimal(requiredColumn(columns, 9, rowNumber), "高", rowNumber));
        row.setWeightGram(parseDecimal(requiredColumn(columns, 10, rowNumber), "重量g", rowNumber));
        return row;
    }

    private String requiredColumn(List<String> columns, int index, int rowNumber) {
        String value = columns.get(index);
        if (value.isBlank()) {
            throw new IllegalArgumentException("交易信息第 " + rowNumber + " 行字段不能为空: " + TRANSACTION_HEADER.get(index));
        }
        return value;
    }

    private BigDecimal parseDecimal(String value, String fieldName, int rowNumber) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("交易信息第 " + rowNumber + " 行字段不是数字: " + fieldName, e);
        }
    }

    private Integer parseInteger(String value, String fieldName, int rowNumber) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("交易信息第 " + rowNumber + " 行字段不是整数: " + fieldName, e);
        }
    }
}
