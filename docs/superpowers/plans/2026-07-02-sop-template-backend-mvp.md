# SOP 模板后端 MVP 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 SOP 模板、商品素材包解析、上架草稿核心模型、草稿校验与 `TikTokPublishRequest` 映射，为后续 CodexExec 生成和 React 审核台提供稳定后端基础。

**Architecture:** 先完成可单元测试的后端核心链路：MyBatis-Plus/MySQL 依赖与模板实体，纯 Java 的素材解析器，纯 Java 的草稿模型/校验/映射。`MabangPublisher` 保持现状，只在后续发布接口中被调用。本计划避免先碰前端和真实 CodexExec，确保第一批代码可快速测试和回归。

**Tech Stack:** Java 21, Spring Boot 4.1, MyBatis-Plus, MySQL Connector/J, JUnit 5, Lombok, Jackson.

---

## 文件结构

新增或修改以下文件：

```text
pom.xml
src/main/resources/application.properties

src/main/java/com/auto/ecommerce/ecommerceauto/template/entity/SopTemplateEntity.java
src/main/java/com/auto/ecommerce/ecommerceauto/template/mapper/SopTemplateMapper.java
src/main/java/com/auto/ecommerce/ecommerceauto/template/service/SopTemplateService.java
src/main/java/com/auto/ecommerce/ecommerceauto/template/service/impl/SopTemplateServiceImpl.java
src/main/java/com/auto/ecommerce/ecommerceauto/template/controller/SopTemplateController.java
src/main/java/com/auto/ecommerce/ecommerceauto/template/dto/SopTemplateCreateRequest.java
src/main/java/com/auto/ecommerce/ecommerceauto/template/dto/SopTemplateUpdateRequest.java

src/main/java/com/auto/ecommerce/ecommerceauto/material/model/ProductMaterialPackage.java
src/main/java/com/auto/ecommerce/ecommerceauto/material/model/MaterialTransactionRow.java
src/main/java/com/auto/ecommerce/ecommerceauto/material/parser/AttributeInfoTextParser.java
src/main/java/com/auto/ecommerce/ecommerceauto/material/parser/MaterialPackageParser.java
src/main/java/com/auto/ecommerce/ecommerceauto/material/controller/MaterialPackageController.java
src/main/java/com/auto/ecommerce/ecommerceauto/material/dto/ParseMaterialPackageRequest.java

src/main/java/com/auto/ecommerce/ecommerceauto/draft/model/ListingDraft.java
src/main/java/com/auto/ecommerce/ecommerceauto/draft/model/ListingDraftStatus.java
src/main/java/com/auto/ecommerce/ecommerceauto/draft/model/ListingDraftTransactionRow.java
src/main/java/com/auto/ecommerce/ecommerceauto/draft/entity/ListingDraftEntity.java
src/main/java/com/auto/ecommerce/ecommerceauto/draft/mapper/ListingDraftMapper.java
src/main/java/com/auto/ecommerce/ecommerceauto/draft/validation/ListingDraftValidator.java
src/main/java/com/auto/ecommerce/ecommerceauto/draft/publish/ListingDraftToTikTokPublishRequestMapper.java

src/test/java/com/auto/ecommerce/ecommerceauto/material/parser/AttributeInfoTextParserTest.java
src/test/java/com/auto/ecommerce/ecommerceauto/material/parser/MaterialPackageParserTest.java
src/test/java/com/auto/ecommerce/ecommerceauto/draft/validation/ListingDraftValidatorTest.java
src/test/java/com/auto/ecommerce/ecommerceauto/draft/publish/ListingDraftToTikTokPublishRequestMapperTest.java
```

## 注意事项

- 当前工作区已有未提交改动。实施前优先创建隔离 worktree；如果用户不希望创建 worktree，则在当前目录实施时只改本计划列出的文件。
- 严格执行 TDD：新增生产代码前先写失败测试，并运行确认失败。
- 第一批不连接真实 MySQL 运行集成测试，避免依赖本机账号密码。Mapper 和 MySQL 连接配置只落结构；业务行为用单元测试保护。
- 不改动 `MabangPublisher` 的现有自动化流程。

---

### Task 1: 增加 MyBatis-Plus 和 MySQL 依赖配置

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: 修改依赖配置**

在 `pom.xml` 的 `<dependencies>` 中增加：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.9</version>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

在 `src/main/resources/application.properties` 中增加本地 MySQL 配置。密码先留空占位，由本机运行时修改：

```properties
# 本地 MySQL 配置
spring.datasource.url=jdbc:mysql://localhost:3306/e-commerce?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# MyBatis-Plus 配置
mybatis-plus.configuration.map-underscore-to-camel-case=true
```

- [ ] **Step 2: 运行 Maven 测试确认依赖可解析**

Run:

```bash
./mvnw test
```

Expected:

```text
BUILD SUCCESS
```

如果 Maven 因网络无法下载依赖失败，按工具提示申请网络权限后重试。

- [ ] **Step 3: Commit**

```bash
git add pom.xml src/main/resources/application.properties
git commit -m "chore: add mysql mybatis plus configuration"
```

---

### Task 2: 实现 SOP 模板实体、Mapper、Service 和 Controller

**Files:**
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/template/entity/SopTemplateEntity.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/template/mapper/SopTemplateMapper.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/template/service/SopTemplateService.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/template/service/impl/SopTemplateServiceImpl.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/template/controller/SopTemplateController.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/template/dto/SopTemplateCreateRequest.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/template/dto/SopTemplateUpdateRequest.java`

- [ ] **Step 1: 写模板 Service 行为测试**

Create `src/test/java/com/auto/ecommerce/ecommerceauto/template/service/SopTemplateServiceImplTest.java`:

```java
package com.auto.ecommerce.ecommerceauto.template.service;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.auto.ecommerce.ecommerceauto.template.service.impl.SopTemplateServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SopTemplateServiceImplTest {

    @Test
    void createsTemplateWithRequiredPrompts() {
        SopTemplateMapper mapper = mock(SopTemplateMapper.class);
        when(mapper.insert(any(SopTemplateEntity.class))).thenReturn(1);
        SopTemplateServiceImpl service = new SopTemplateServiceImpl(mapper);

        SopTemplateCreateRequest request = new SopTemplateCreateRequest();
        request.setTemplateId("tiktok-fullservice-mabang-v1");
        request.setName("TikTok 全托管马帮模板");
        request.setTitlePrompt("同时生成中文标题和英文标题");
        request.setMainImagePrompt("生成真实清晰主图提示词");

        SopTemplateEntity created = service.createTemplate(request);

        assertThat(created.getTemplateId()).isEqualTo("tiktok-fullservice-mabang-v1");
        assertThat(created.getTitlePrompt()).contains("中文标题");
        assertThat(created.getMainImagePrompt()).contains("主图");
        assertThat(created.getCreateTime()).isNotNull();
        assertThat(created.getUpdateTime()).isNotNull();
        verify(mapper).insert(created);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./mvnw -Dtest=SopTemplateServiceImplTest test
```

Expected:

```text
Compilation failure: package ...template... does not exist
```

- [ ] **Step 3: 实现 DTO**

Create `SopTemplateCreateRequest.java`:

```java
package com.auto.ecommerce.ecommerceauto.template.dto;

import lombok.Data;

@Data
public class SopTemplateCreateRequest {
    private String templateId;
    private String name;
    private String titlePrompt;
    private String mainImagePrompt;
}
```

Create `SopTemplateUpdateRequest.java`:

```java
package com.auto.ecommerce.ecommerceauto.template.dto;

import lombok.Data;

@Data
public class SopTemplateUpdateRequest {
    private String name;
    private String titlePrompt;
    private String mainImagePrompt;
}
```

- [ ] **Step 4: 实现 Entity 和 Mapper**

Create `SopTemplateEntity.java`:

```java
package com.auto.ecommerce.ecommerceauto.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sop_template")
public class SopTemplateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateId;
    private String name;
    private String titlePrompt;
    private String mainImagePrompt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

Create `SopTemplateMapper.java`:

```java
package com.auto.ecommerce.ecommerceauto.template.mapper;

import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SopTemplateMapper extends BaseMapper<SopTemplateEntity> {
}
```

- [ ] **Step 5: 实现 Service**

Create `SopTemplateService.java`:

```java
package com.auto.ecommerce.ecommerceauto.template.service;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateUpdateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;

import java.util.List;

public interface SopTemplateService {
    SopTemplateEntity createTemplate(SopTemplateCreateRequest request);
    List<SopTemplateEntity> listTemplates();
    SopTemplateEntity updateTemplate(String templateId, SopTemplateUpdateRequest request);
}
```

Create `SopTemplateServiceImpl.java`:

```java
package com.auto.ecommerce.ecommerceauto.template.service.impl;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateUpdateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.auto.ecommerce.ecommerceauto.template.service.SopTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SopTemplateServiceImpl implements SopTemplateService {

    private final SopTemplateMapper mapper;

    @Override
    public SopTemplateEntity createTemplate(SopTemplateCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        SopTemplateEntity entity = new SopTemplateEntity();
        entity.setTemplateId(request.getTemplateId());
        entity.setName(request.getName());
        entity.setTitlePrompt(request.getTitlePrompt());
        entity.setMainImagePrompt(request.getMainImagePrompt());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public List<SopTemplateEntity> listTemplates() {
        return mapper.selectList(new LambdaQueryWrapper<SopTemplateEntity>()
                .orderByDesc(SopTemplateEntity::getUpdateTime));
    }

    @Override
    public SopTemplateEntity updateTemplate(String templateId, SopTemplateUpdateRequest request) {
        SopTemplateEntity entity = mapper.selectOne(new LambdaQueryWrapper<SopTemplateEntity>()
                .eq(SopTemplateEntity::getTemplateId, templateId));
        if (entity == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        entity.setName(request.getName());
        entity.setTitlePrompt(request.getTitlePrompt());
        entity.setMainImagePrompt(request.getMainImagePrompt());
        entity.setUpdateTime(LocalDateTime.now());
        mapper.updateById(entity);
        return entity;
    }
}
```

- [ ] **Step 6: 实现 Controller**

Create `SopTemplateController.java`:

```java
package com.auto.ecommerce.ecommerceauto.template.controller;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateUpdateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.service.SopTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sop-templates")
public class SopTemplateController {

    private final SopTemplateService service;

    @GetMapping
    public List<SopTemplateEntity> list() {
        return service.listTemplates();
    }

    @PostMapping
    public SopTemplateEntity create(@RequestBody SopTemplateCreateRequest request) {
        return service.createTemplate(request);
    }

    @PutMapping("/{templateId}")
    public SopTemplateEntity update(@PathVariable String templateId, @RequestBody SopTemplateUpdateRequest request) {
        return service.updateTemplate(templateId, request);
    }
}
```

- [ ] **Step 7: 运行测试确认通过**

Run:

```bash
./mvnw -Dtest=SopTemplateServiceImplTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/template src/test/java/com/auto/ecommerce/ecommerceauto/template
git commit -m "feat: add sop template crud foundation"
```

---

### Task 3: 实现 `属性信息.txt` 解析器

**Files:**
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/model/ProductMaterialPackage.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/model/MaterialTransactionRow.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/parser/AttributeInfoTextParser.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/material/parser/AttributeInfoTextParserTest.java`

- [ ] **Step 1: 写解析成功测试**

Create `AttributeInfoTextParserTest.java`:

```java
package com.auto.ecommerce.ecommerceauto.material.parser;

import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeInfoTextParserTest {

    @Test
    void parsesProductAttributesVariantsAndTransactionRows() {
        String text = """
                [产品信息]
                产品名称=黑白剪刀
                来源URL=https://example.com/item
                店铺=测试店铺
                类目=家用工具/厨房工具
                品牌=无品牌

                [分类属性]
                使用=家用
                材质=不锈钢
                原产地=中国

                [变种属性]
                颜色=套装1
                规格=均码
                尺码表图片=size.jpg

                [交易信息]
                颜色|规格|备货模式|SKC货号|SKU货号|不含税价|库存|长|宽|高|重量g
                套装1|均码|JIT备货|黑白剪刀|黑白剪刀-套装1-均码|5|999|21|9|2|150
                """;

        ProductMaterialPackage result = new AttributeInfoTextParser().parse(text);

        assertThat(result.getProductName()).isEqualTo("黑白剪刀");
        assertThat(result.getShopName()).isEqualTo("测试店铺");
        assertThat(result.getCategoryAttributes()).containsEntry("材质", "不锈钢");
        assertThat(result.getVariantAttributes()).containsEntry("颜色", "套装1");
        assertThat(result.getSizeChartImageName()).isEqualTo("size.jpg");
        assertThat(result.getTransactionRows()).hasSize(1);
        assertThat(result.getTransactionRows().get(0).getSku()).isEqualTo("黑白剪刀-套装1-均码");
        assertThat(result.getTransactionRows().get(0).getWeight()).isEqualTo(150.0);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./mvnw -Dtest=AttributeInfoTextParserTest test
```

Expected:

```text
Compilation failure: package ...material... does not exist
```

- [ ] **Step 3: 实现模型**

Create `MaterialTransactionRow.java`:

```java
package com.auto.ecommerce.ecommerceauto.material.model;

import lombok.Data;

@Data
public class MaterialTransactionRow {
    private String color;
    private String size;
    private String stockingMode;
    private String skc;
    private String sku;
    private Double price;
    private Integer stock;
    private Double length;
    private Double width;
    private Double height;
    private Double weight;
}
```

Create `ProductMaterialPackage.java`:

```java
package com.auto.ecommerce.ecommerceauto.material.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ProductMaterialPackage {
    private String materialPackagePath;
    private String productName;
    private String sourceUrl;
    private String shopName;
    private String categoryName;
    private String brand;
    private String sizeChartImageName;
    private String sizeChartImagePath;
    private Map<String, String> categoryAttributes = new LinkedHashMap<>();
    private Map<String, String> variantAttributes = new LinkedHashMap<>();
    private List<MaterialTransactionRow> transactionRows = new ArrayList<>();
    private List<String> mainImageSourcePaths = new ArrayList<>();
    private List<String> detailImagePaths = new ArrayList<>();
}
```

- [ ] **Step 4: 实现解析器**

Create `AttributeInfoTextParser.java`:

```java
package com.auto.ecommerce.ecommerceauto.material.parser;

import com.auto.ecommerce.ecommerceauto.material.model.MaterialTransactionRow;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AttributeInfoTextParser {

    private static final String TRANSACTION_HEADER = "颜色|规格|备货模式|SKC货号|SKU货号|不含税价|库存|长|宽|高|重量g";

    public ProductMaterialPackage parse(String text) {
        Map<String, List<String>> sections = splitSections(text);
        requireSection(sections, "产品信息");
        requireSection(sections, "分类属性");
        requireSection(sections, "变种属性");
        requireSection(sections, "交易信息");

        ProductMaterialPackage result = new ProductMaterialPackage();
        Map<String, String> product = parseKeyValues(sections.get("产品信息"));
        result.setProductName(product.get("产品名称"));
        result.setSourceUrl(product.get("来源URL"));
        result.setShopName(product.get("店铺"));
        result.setCategoryName(product.get("类目"));
        result.setBrand(product.getOrDefault("品牌", "无品牌"));

        result.setCategoryAttributes(parseKeyValues(sections.get("分类属性")));
        Map<String, String> variants = parseKeyValues(sections.get("变种属性"));
        result.setSizeChartImageName(variants.remove("尺码表图片"));
        result.setVariantAttributes(variants);
        result.setTransactionRows(parseTransactionRows(sections.get("交易信息")));
        return result;
    }

    private Map<String, List<String>> splitSections(String text) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = null;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = line.substring(1, line.length() - 1);
                sections.putIfAbsent(current, new ArrayList<>());
                continue;
            }
            if (current == null) {
                throw new IllegalArgumentException("发现未归属分段的内容: " + line);
            }
            sections.get(current).add(line);
        }
        return sections;
    }

    private void requireSection(Map<String, List<String>> sections, String name) {
        if (!sections.containsKey(name) || sections.get(name).isEmpty()) {
            throw new IllegalArgumentException("缺少必需分段: " + name);
        }
    }

    private Map<String, String> parseKeyValues(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                throw new IllegalArgumentException("字段必须使用 key=value 格式: " + line);
            }
            values.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
        }
        return values;
    }

    private List<MaterialTransactionRow> parseTransactionRows(List<String> lines) {
        if (lines.isEmpty() || !TRANSACTION_HEADER.equals(lines.get(0))) {
            throw new IllegalArgumentException("交易信息表头不符合约定");
        }
        List<MaterialTransactionRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split("\\|", -1);
            if (parts.length != 11) {
                throw new IllegalArgumentException("交易信息列数必须为 11: " + lines.get(i));
            }
            MaterialTransactionRow row = new MaterialTransactionRow();
            row.setColor(parts[0].trim());
            row.setSize(parts[1].trim());
            row.setStockingMode(parts[2].trim());
            row.setSkc(parts[3].trim());
            row.setSku(parts[4].trim());
            row.setPrice(Double.valueOf(parts[5].trim()));
            row.setStock(Integer.valueOf(parts[6].trim()));
            row.setLength(Double.valueOf(parts[7].trim()));
            row.setWidth(Double.valueOf(parts[8].trim()));
            row.setHeight(Double.valueOf(parts[9].trim()));
            row.setWeight(Double.valueOf(parts[10].trim()));
            rows.add(row);
        }
        return rows;
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:

```bash
./mvnw -Dtest=AttributeInfoTextParserTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: 添加错误表头测试并确认仍通过**

Append to `AttributeInfoTextParserTest.java`:

```java
@Test
void rejectsInvalidTransactionHeader() {
    String text = """
            [产品信息]
            产品名称=黑白剪刀

            [分类属性]
            材质=不锈钢

            [变种属性]
            颜色=套装1

            [交易信息]
            颜色|规格|价格
            套装1|均码|5
            """;

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AttributeInfoTextParser().parse(text))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("交易信息表头");
}
```

Run:

```bash
./mvnw -Dtest=AttributeInfoTextParserTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/material src/test/java/com/auto/ecommerce/ecommerceauto/material
git commit -m "feat: parse attribute info text"
```

---

### Task 4: 实现素材包目录扫描器

**Files:**
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/parser/MaterialPackageParser.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/controller/MaterialPackageController.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/material/dto/ParseMaterialPackageRequest.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/material/parser/MaterialPackageParserTest.java`

- [ ] **Step 1: 写目录扫描测试**

Create `MaterialPackageParserTest.java`:

```java
package com.auto.ecommerce.ecommerceauto.material.parser;

import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialPackageParserTest {

    @TempDir
    Path tempDir;

    @Test
    void scansFixedDirectoriesAndSortsImagesByFileName() throws Exception {
        Path main = Files.createDirectories(tempDir.resolve("主图"));
        Path detail = Files.createDirectories(tempDir.resolve("副图"));
        Path size = Files.createDirectories(tempDir.resolve("尺码表"));
        Files.writeString(main.resolve("2.jpg"), "fake");
        Files.writeString(main.resolve("1.jpg"), "fake");
        Files.writeString(detail.resolve("3.jpg"), "fake");
        Files.writeString(detail.resolve("2.jpg"), "fake");
        Files.writeString(size.resolve("size.jpg"), "fake");
        Files.writeString(tempDir.resolve("属性信息.txt"), """
                [产品信息]
                产品名称=黑白剪刀
                店铺=测试店铺
                类目=家用工具
                品牌=无品牌

                [分类属性]
                材质=不锈钢

                [变种属性]
                颜色=套装1
                规格=均码
                尺码表图片=size.jpg

                [交易信息]
                颜色|规格|备货模式|SKC货号|SKU货号|不含税价|库存|长|宽|高|重量g
                套装1|均码|JIT备货|黑白剪刀|黑白剪刀-套装1-均码|5|999|21|9|2|150
                """);

        ProductMaterialPackage result = new MaterialPackageParser(new AttributeInfoTextParser()).parse(tempDir);

        assertThat(result.getMainImageSourcePaths()).extracting(path -> Path.of(path).getFileName().toString())
                .containsExactly("1.jpg", "2.jpg");
        assertThat(result.getDetailImagePaths()).extracting(path -> Path.of(path).getFileName().toString())
                .containsExactly("2.jpg", "3.jpg");
        assertThat(Path.of(result.getSizeChartImagePath()).getFileName().toString()).isEqualTo("size.jpg");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./mvnw -Dtest=MaterialPackageParserTest test
```

Expected:

```text
Compilation failure: cannot find symbol MaterialPackageParser
```

- [ ] **Step 3: 实现扫描器**

Create `MaterialPackageParser.java`:

```java
package com.auto.ecommerce.ecommerceauto.material.parser;

import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class MaterialPackageParser {

    private final AttributeInfoTextParser textParser;

    public MaterialPackageParser(AttributeInfoTextParser textParser) {
        this.textParser = textParser;
    }

    public ProductMaterialPackage parse(Path packagePath) {
        Path mainDir = requireDirectory(packagePath, "主图");
        Path detailDir = requireDirectory(packagePath, "副图");
        Path sizeDir = requireDirectory(packagePath, "尺码表");
        Path infoFile = packagePath.resolve("属性信息.txt");
        if (!Files.isRegularFile(infoFile)) {
            throw new IllegalArgumentException("缺少 属性信息.txt: " + infoFile);
        }
        try {
            ProductMaterialPackage result = textParser.parse(Files.readString(infoFile));
            result.setMaterialPackagePath(packagePath.toString());
            result.setMainImageSourcePaths(listImages(mainDir));
            result.setDetailImagePaths(listImages(detailDir));
            result.setSizeChartImagePath(resolveSizeChart(sizeDir, result.getSizeChartImageName()));
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("读取素材包失败: " + packagePath, e);
        }
    }

    private Path requireDirectory(Path packagePath, String name) {
        Path path = packagePath.resolve(name);
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("缺少目录: " + name);
        }
        return path;
    }

    private List<String> listImages(Path dir) throws IOException {
        List<String> images;
        try (var stream = Files.list(dir)) {
            images = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedImage)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(Path::toString)
                    .toList();
        }
        if (images.isEmpty()) {
            throw new IllegalArgumentException("图片目录为空: " + dir);
        }
        return images;
    }

    private String resolveSizeChart(Path sizeDir, String imageName) throws IOException {
        if (imageName != null && !imageName.isBlank()) {
            Path selected = sizeDir.resolve(imageName);
            if (!Files.isRegularFile(selected)) {
                throw new IllegalArgumentException("尺码表图片不存在: " + selected);
            }
            return selected.toString();
        }
        return listImages(sizeDir).get(0);
    }

    private boolean isSupportedImage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }
}
```

- [ ] **Step 4: 添加 Controller DTO**

Create `ParseMaterialPackageRequest.java`:

```java
package com.auto.ecommerce.ecommerceauto.material.dto;

import lombok.Data;

@Data
public class ParseMaterialPackageRequest {
    private String materialPackagePath;
}
```

Create `MaterialPackageController.java`:

```java
package com.auto.ecommerce.ecommerceauto.material.controller;

import com.auto.ecommerce.ecommerceauto.material.dto.ParseMaterialPackageRequest;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.material.parser.AttributeInfoTextParser;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/material-packages")
public class MaterialPackageController {

    @PostMapping("/parse")
    public ProductMaterialPackage parse(@RequestBody ParseMaterialPackageRequest request) {
        return new MaterialPackageParser(new AttributeInfoTextParser())
                .parse(Path.of(request.getMaterialPackagePath()));
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:

```bash
./mvnw -Dtest=MaterialPackageParserTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/material src/test/java/com/auto/ecommerce/ecommerceauto/material
git commit -m "feat: parse product material packages"
```

---

### Task 5: 实现 ListingDraft 模型、校验器和映射器

**Files:**
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/model/ListingDraft.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/model/ListingDraftStatus.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/model/ListingDraftTransactionRow.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/entity/ListingDraftEntity.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/mapper/ListingDraftMapper.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/validation/ListingDraftValidator.java`
- Create: `src/main/java/com/auto/ecommerce/ecommerceauto/draft/publish/ListingDraftToTikTokPublishRequestMapper.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/draft/validation/ListingDraftValidatorTest.java`
- Test: `src/test/java/com/auto/ecommerce/ecommerceauto/draft/publish/ListingDraftToTikTokPublishRequestMapperTest.java`

- [ ] **Step 1: 写草稿校验失败测试**

Create `ListingDraftValidatorTest.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.validation;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingDraftValidatorTest {

    @Test
    void rejectsApprovalWhenFinalMainImageIsMissing() {
        ListingDraft draft = new ListingDraft();
        draft.setStatus(ListingDraftStatus.REVIEWING);
        draft.getTitleInfo().setChineseTitle("中文标题");
        draft.getTitleInfo().setEnglishTitle("English title");
        draft.getCategoryAttributes().put("材质", "不锈钢");
        draft.getImageInfo().getDescriptionImagePaths().add("/tmp/detail.jpg");
        draft.getImageInfo().setFinalMainImagePath("/tmp/not-exists.jpg");

        assertThatThrownBy(() -> new ListingDraftValidator().validateForApproval(draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最终主图文件不存在");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
./mvnw -Dtest=ListingDraftValidatorTest test
```

Expected:

```text
Compilation failure: package ...draft... does not exist
```

- [ ] **Step 3: 实现草稿模型**

Create `ListingDraftStatus.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.model;

public enum ListingDraftStatus {
    GENERATED,
    REVIEWING,
    APPROVED,
    PUBLISHED,
    FAILED
}
```

Create `ListingDraftTransactionRow.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.model;

import lombok.Data;

@Data
public class ListingDraftTransactionRow {
    private String color;
    private String size;
    private String skc;
    private String stockingMode;
    private String sku;
    private Double price;
    private Integer stock;
    private Double length;
    private Double width;
    private Double height;
    private Double weight;
    private Boolean enabled = true;
}
```

Create `ListingDraft.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ListingDraft {
    private String draftId;
    private String templateId;
    private String templateName;
    private String titlePromptSnapshot;
    private String mainImagePromptSnapshot;
    private String materialPackagePath;
    private ListingDraftStatus status = ListingDraftStatus.GENERATED;
    private BasicInfo basicInfo = new BasicInfo();
    private TitleInfo titleInfo = new TitleInfo();
    private Map<String, String> categoryAttributes = new LinkedHashMap<>();
    private ImageInfo imageInfo = new ImageInfo();
    private VariantInfo variantInfo = new VariantInfo();
    private List<ListingDraftTransactionRow> transactionRows = new ArrayList<>();

    @Data
    public static class BasicInfo {
        private String shopName;
        private String categoryName;
        private String sourceUrl;
        private String brand = "无品牌";
    }

    @Data
    public static class TitleInfo {
        private String productNameCn;
        private String productNameEn;
        private String chineseTitle;
        private String englishTitle;
        private String titleReviewNotes;
    }

    @Data
    public static class ImageInfo {
        private List<String> mainImageSourcePaths = new ArrayList<>();
        private String mainImagePrompt;
        private String finalMainImagePath;
        private String productSizeChartImage;
        private List<String> productDetailImages = new ArrayList<>();
        private List<String> descriptionImagePaths = new ArrayList<>();
        private String imageReviewNotes;
    }

    @Data
    public static class VariantInfo {
        private Map<String, List<String>> variantAttributes = new LinkedHashMap<>();
        private List<String> variantPreviewImages = new ArrayList<>();
    }
}
```

- [ ] **Step 4: 实现 Entity 和 Mapper**

Create `ListingDraftEntity.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("listing_draft")
public class ListingDraftEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String draftId;
    private String templateId;
    private String templateName;
    private String titlePromptSnapshot;
    private String mainImagePromptSnapshot;
    private String materialPackagePath;
    private String status;
    private String draftJson;
    private String publishRequestJson;
    private String lastErrorType;
    private String lastErrorMessage;
    private String publishScreenshotPath;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

Create `ListingDraftMapper.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.mapper;

import com.auto.ecommerce.ecommerceauto.draft.entity.ListingDraftEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ListingDraftMapper extends BaseMapper<ListingDraftEntity> {
}
```

- [ ] **Step 5: 实现校验器**

Create `ListingDraftValidator.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.validation;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;

import java.nio.file.Files;
import java.nio.file.Path;

public class ListingDraftValidator {

    public void validateForApproval(ListingDraft draft) {
        requireText(draft.getTitleInfo().getChineseTitle(), "中文标题不能为空");
        requireText(draft.getTitleInfo().getEnglishTitle(), "英文标题不能为空");
        if (draft.getCategoryAttributes().isEmpty()) {
            throw new IllegalArgumentException("分类属性不能为空");
        }
        String mainImage = draft.getImageInfo().getFinalMainImagePath();
        requireText(mainImage, "最终主图路径不能为空");
        if (!Files.isRegularFile(Path.of(mainImage))) {
            throw new IllegalArgumentException("最终主图文件不存在: " + mainImage);
        }
        if (draft.getImageInfo().getDescriptionImagePaths().isEmpty()) {
            throw new IllegalArgumentException("描述图不能为空");
        }
        if (draft.getTransactionRows().isEmpty()) {
            throw new IllegalArgumentException("交易信息不能为空");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

- [ ] **Step 6: 运行校验测试确认通过**

Run:

```bash
./mvnw -Dtest=ListingDraftValidatorTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 7: 写映射测试**

Create `ListingDraftToTikTokPublishRequestMapperTest.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.publish;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftTransactionRow;
import com.auto.ecommerce.ecommerceauto.playwright.TikTokPublishRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListingDraftToTikTokPublishRequestMapperTest {

    @Test
    void mapsApprovedDraftToTikTokPublishRequest() {
        ListingDraft draft = new ListingDraft();
        draft.getBasicInfo().setShopName("测试店铺");
        draft.getBasicInfo().setCategoryName("家用工具");
        draft.getBasicInfo().setSourceUrl("https://example.com/item");
        draft.getBasicInfo().setBrand("无品牌");
        draft.getTitleInfo().setChineseTitle("中文标题");
        draft.getTitleInfo().setEnglishTitle("English title");
        draft.getCategoryAttributes().put("材质", "不锈钢");
        draft.getImageInfo().setFinalMainImagePath("/tmp/main.jpg");
        draft.getImageInfo().setProductSizeChartImage("/tmp/size.jpg");
        draft.getImageInfo().setDescriptionImagePaths(List.of("/tmp/detail-1.jpg"));
        draft.getVariantInfo().getVariantAttributes().put("颜色", List.of("套装1"));

        ListingDraftTransactionRow row = new ListingDraftTransactionRow();
        row.setColor("套装1");
        row.setSize("均码");
        row.setSku("SKU-1");
        row.setSkc("SKC-1");
        row.setStockingMode("JIT备货");
        row.setPrice(5.0);
        row.setStock(999);
        row.setLength(21.0);
        row.setWidth(9.0);
        row.setHeight(2.0);
        row.setWeight(150.0);
        draft.getTransactionRows().add(row);

        TikTokPublishRequest request = new ListingDraftToTikTokPublishRequestMapper().map(draft);

        assertThat(request.getShopName()).isEqualTo("测试店铺");
        assertThat(request.getChineseTitle()).isEqualTo("中文标题");
        assertThat(request.getProductMainImage()).isEqualTo("/tmp/main.jpg");
        assertThat(request.getDescriptionImagePaths()).containsExactly("/tmp/detail-1.jpg");
        assertThat(request.getCategoryAttributes()).containsEntry("材质", "不锈钢");
        assertThat(request.getTransactionInfo()).hasSize(1);
        assertThat(request.getTransactionInfo().get(0).getSku()).isEqualTo("SKU-1");
        assertThat(request.isPublish()).isFalse();
    }
}
```

- [ ] **Step 8: 运行映射测试确认失败**

Run:

```bash
./mvnw -Dtest=ListingDraftToTikTokPublishRequestMapperTest test
```

Expected:

```text
Compilation failure: cannot find symbol ListingDraftToTikTokPublishRequestMapper
```

- [ ] **Step 9: 实现映射器**

Create `ListingDraftToTikTokPublishRequestMapper.java`:

```java
package com.auto.ecommerce.ecommerceauto.draft.publish;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftTransactionRow;
import com.auto.ecommerce.ecommerceauto.playwright.TikTokPublishRequest;

import java.util.List;

public class ListingDraftToTikTokPublishRequestMapper {

    public TikTokPublishRequest map(ListingDraft draft) {
        TikTokPublishRequest request = new TikTokPublishRequest();
        request.setShopName(draft.getBasicInfo().getShopName());
        request.setCategoryName(draft.getBasicInfo().getCategoryName());
        request.setSourceUrl(draft.getBasicInfo().getSourceUrl());
        request.setBrand(draft.getBasicInfo().getBrand());
        request.setChineseTitle(draft.getTitleInfo().getChineseTitle());
        request.setEnglishTitle(draft.getTitleInfo().getEnglishTitle());
        request.setCategoryAttributes(draft.getCategoryAttributes());
        request.setProductMainImage(draft.getImageInfo().getFinalMainImagePath());
        request.setProductSizeChartImage(draft.getImageInfo().getProductSizeChartImage());
        request.setProductDetailImages(draft.getImageInfo().getProductDetailImages());
        request.setDescriptionImagePaths(draft.getImageInfo().getDescriptionImagePaths());
        request.setVariantAttributes(draft.getVariantInfo().getVariantAttributes());
        request.setVariantPreviewImages(draft.getVariantInfo().getVariantPreviewImages());
        request.setTransactionInfo(mapRows(draft.getTransactionRows()));
        request.setPublish(false);
        return request;
    }

    private List<TikTokPublishRequest.TransactionRow> mapRows(List<ListingDraftTransactionRow> rows) {
        return rows.stream().map(row -> TikTokPublishRequest.TransactionRow.builder()
                .color(row.getColor())
                .size(row.getSize())
                .skc(row.getSkc())
                .stockingMode(row.getStockingMode())
                .sku(row.getSku())
                .price(row.getPrice())
                .stock(row.getStock())
                .length(row.getLength())
                .width(row.getWidth())
                .height(row.getHeight())
                .weight(row.getWeight())
                .enabled(row.getEnabled())
                .build()).toList();
    }
}
```

- [ ] **Step 10: 运行映射测试确认通过**

Run:

```bash
./mvnw -Dtest=ListingDraftToTikTokPublishRequestMapperTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 11: 运行所有新增单元测试**

Run:

```bash
./mvnw -Dtest=SopTemplateServiceImplTest,AttributeInfoTextParserTest,MaterialPackageParserTest,ListingDraftValidatorTest,ListingDraftToTikTokPublishRequestMapperTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/auto/ecommerce/ecommerceauto/draft src/test/java/com/auto/ecommerce/ecommerceauto/draft
git commit -m "feat: add listing draft validation and mapping"
```

---

## 后续计划

后端 MVP 通过后，再创建下一份实施计划：

```text
2026-07-02-codexexec-draft-generation.md
```

覆盖：

- `ListingDraftService` 持久化。
- `CodexPromptBuilder`。
- `CodexExecListingAiGenerator`。
- 草稿生成、审核、发布 REST API。
- `MabangPublisher.publish` 包装与错误持久化。

前端随后创建单独计划：

```text
2026-07-02-react-ant-design-operator-console.md
```

覆盖：

- `frontend/` Vite React 初始化。
- Ant Design 布局。
- 模板管理页。
- 素材解析页。
- 草稿审核页。
- API client 和代理配置。

## 自检

- 本计划覆盖了第一批后端基础：依赖、模板、素材解析、草稿模型、校验、发布请求映射。
- 本计划没有实现真实 CodexExec 和前端，因为它们是独立子系统，拆到后续计划能降低一次变更的风险。
- 所有生产代码任务都有先写测试、确认失败、实现、确认通过的步骤。
- 当前计划没有占位内容或未定义类型；后续类型在各任务中先定义再使用。
