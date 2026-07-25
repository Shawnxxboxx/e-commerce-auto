package com.auto.ecommerce.ecommerceauto.draft.service;

import com.auto.ecommerce.ecommerceauto.draft.ai.AiDraftGenerationResult;
import com.auto.ecommerce.ecommerceauto.draft.ai.ListingDraftAiGenerator;
import com.auto.ecommerce.ecommerceauto.draft.dto.GenerateListingDraftRequest;
import com.auto.ecommerce.ecommerceauto.draft.dto.ListingDraftResponse;
import com.auto.ecommerce.ecommerceauto.draft.entity.ListingDraftEntity;
import com.auto.ecommerce.ecommerceauto.draft.mapper.ListingDraftMapper;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.material.config.MaterialStorageProperties;
import com.auto.ecommerce.ecommerceauto.material.entity.MaterialPackageEntity;
import com.auto.ecommerce.ecommerceauto.material.mapper.MaterialPackageMapper;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.material.parser.AttributeInfoTextParser;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageService;
import com.auto.ecommerce.ecommerceauto.material.service.MaterialPackageStorageService;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingDraftServiceTest {

    private static final String MATERIAL_ID = "material-1";
    private static final Path MATERIAL_PATH = Path.of("/server/material-1");

    @Test
    void startsGenerationFromMaterialPackageId() {
        RecordingDraftMapper recorded = new RecordingDraftMapper();
        StubParser parser = new StubParser(material());
        RecordingWorker worker = new RecordingWorker();
        StubMaterialService materialService = new StubMaterialService(materialEntity(), MATERIAL_PATH);
        ListingDraftService service = service(recorded, template(), parser, worker, materialService);

        ListingDraftResponse response = service.startGeneration(request());

        assertThat(response.getDraft().getMaterialPackageId()).isEqualTo(MATERIAL_ID);
        assertThat(response.getDraft().getMaterialPackagePath()).isEqualTo(MATERIAL_PATH.toString());
        assertThat(recorded.inserted.getMaterialPackageId()).isEqualTo(MATERIAL_ID);
        assertThat(recorded.inserted.getMaterialPackagePath()).isEqualTo(MATERIAL_PATH.toString());
        assertThat(worker.generatedDraftId).isEqualTo(response.getDraftId());
    }

    @Test
    void rejectsAlreadyBoundMaterialPackageBeforeGeneration() {
        RecordingDraftMapper recorded = new RecordingDraftMapper();
        recorded.count = 1;
        ListingDraftService service = service(
                recorded, template(), new StubParser(material()), new RecordingWorker(),
                new StubMaterialService(materialEntity(), MATERIAL_PATH));

        assertThatThrownBy(() -> service.startGeneration(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已生成草稿");

        assertThat(recorded.inserted).isNull();
    }

    @Test
    void workerResolvesMaterialPathFromMaterialPackageId() throws Exception {
        RecordingDraftMapper recorded = new RecordingDraftMapper();
        recorded.selected = draftEntity(MATERIAL_ID, "/legacy/path");
        StubParser parser = new StubParser(material());
        StubMaterialService materialService = new StubMaterialService(materialEntity(), MATERIAL_PATH);

        worker(recorded, template(), parser, materialService).generate("draft-1");

        assertThat(parser.lastPath).isEqualTo(MATERIAL_PATH);
        ListingDraft generated = new ObjectMapper().readValue(recorded.updated.getDraftJson(), ListingDraft.class);
        assertThat(generated.getMaterialPackageId()).isEqualTo(MATERIAL_ID);
        assertThat(generated.getMaterialPackagePath()).isEqualTo(MATERIAL_PATH.toString());
    }

    @Test
    void workerFallsBackToLegacyMaterialPackagePathWithoutId() {
        RecordingDraftMapper recorded = new RecordingDraftMapper();
        Path legacyPath = Path.of("/legacy/material-1");
        recorded.selected = draftEntity(null, legacyPath.toString());
        StubParser parser = new StubParser(material());
        StubMaterialService materialService = new StubMaterialService(materialEntity(), MATERIAL_PATH);

        worker(recorded, template(), parser, materialService).generate("draft-1");

        assertThat(parser.lastPath).isEqualTo(legacyPath);
        assertThat(materialService.packagePathRequest).isNull();
    }

    private ListingDraftService service(RecordingDraftMapper recorded, SopTemplateEntity template,
                                        StubParser parser, RecordingWorker worker,
                                        StubMaterialService materialService) {
        return new ListingDraftService(
                recorded.proxy(), templateMapper(template), parser, materialService, new ListingDraftFactory(),
                worker, null, null, null);
    }

    private ListingDraftGenerationWorker worker(RecordingDraftMapper recorded, SopTemplateEntity template,
                                                 StubParser parser, StubMaterialService materialService) {
        return new ListingDraftGenerationWorker(
                recorded.proxy(), templateMapper(template), parser, materialService, new ListingDraftFactory(),
                (ignoredTemplate, ignoredMaterial) -> aiResult());
    }

    private GenerateListingDraftRequest request() {
        GenerateListingDraftRequest request = new GenerateListingDraftRequest();
        request.setTemplateId(1L);
        request.setMaterialPackageId(MATERIAL_ID);
        return request;
    }

    private static SopTemplateEntity template() {
        SopTemplateEntity template = new SopTemplateEntity();
        template.setId(1L);
        template.setName("测试模板");
        template.setTitlePrompt("标题");
        template.setMainImagePrompt("主图");
        return template;
    }

    private static SopTemplateMapper templateMapper(SopTemplateEntity template) {
        return (SopTemplateMapper) Proxy.newProxyInstance(
                ListingDraftServiceTest.class.getClassLoader(),
                new Class<?>[] {SopTemplateMapper.class},
                (proxy, method, arguments) -> {
                    if ("selectById".equals(method.getName())) {
                        return template;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static MaterialPackageEntity materialEntity() {
        MaterialPackageEntity entity = new MaterialPackageEntity();
        entity.setMaterialPackageId(MATERIAL_ID);
        entity.setStoragePath(MATERIAL_PATH.toString());
        return entity;
    }

    private static ProductMaterialPackage material() {
        ProductMaterialPackage material = new ProductMaterialPackage();
        material.setMaterialPackagePath(MATERIAL_PATH.toString());
        material.setProductName("测试商品");
        material.setMainImageSourcePaths(List.of(MATERIAL_PATH.resolve("主图/1.jpg").toString()));
        material.setDetailImagePaths(List.of());
        material.setSizeChartImagePaths(List.of());
        material.setPackageImagePaths(List.of());
        material.setCategoryAttributes(Map.of());
        material.setVariantAttributes(Map.of());
        material.setTransactionRows(List.of());
        return material;
    }

    private static ListingDraftEntity draftEntity(String materialPackageId, String materialPackagePath) {
        ListingDraftEntity entity = new ListingDraftEntity();
        entity.setDraftId("draft-1");
        entity.setTemplateId("1");
        entity.setMaterialPackageId(materialPackageId);
        entity.setMaterialPackagePath(materialPackagePath);
        return entity;
    }

    private static AiDraftGenerationResult aiResult() {
        AiDraftGenerationResult result = new AiDraftGenerationResult();
        result.setChineseTitle("测试商品");
        result.setEnglishTitle("Test product");
        result.setMainImagePath(MATERIAL_PATH.resolve("AI生成/main.png").toString());
        return result;
    }

    private static final class RecordingDraftMapper {
        private long count;
        private ListingDraftEntity inserted;
        private ListingDraftEntity selected;
        private ListingDraftEntity updated;

        private ListingDraftMapper proxy() {
            return (ListingDraftMapper) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {ListingDraftMapper.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "selectCount" -> count;
                        case "insert" -> {
                            inserted = (ListingDraftEntity) arguments[0];
                            yield 1;
                        }
                        case "selectOne" -> selected;
                        case "updateById" -> {
                            updated = (ListingDraftEntity) arguments[0];
                            yield 1;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class StubParser extends MaterialPackageParser {
        private final ProductMaterialPackage material;
        private Path lastPath;

        private StubParser(ProductMaterialPackage material) {
            super(new AttributeInfoTextParser());
            this.material = material;
        }

        @Override
        public ProductMaterialPackage parse(Path materialPackagePath) {
            lastPath = materialPackagePath;
            return material;
        }
    }

    private static final class StubMaterialService extends MaterialPackageService {
        private final MaterialPackageEntity entity;
        private final Path path;
        private String packagePathRequest;

        private StubMaterialService(MaterialPackageEntity entity, Path path) {
            super(materialMapper(),
                    new MaterialPackageStorageService(new MaterialStorageProperties(Path.of("/tmp/material-test"), 1)),
                    new MaterialPackageParser(new AttributeInfoTextParser()), new ObjectMapper());
            this.entity = entity;
            this.path = path;
        }

        @Override
        public MaterialPackageEntity require(String materialPackageId) {
            return entity;
        }

        @Override
        public Path packagePath(String materialPackageId) {
            packagePathRequest = materialPackageId;
            return path;
        }
    }

    private static MaterialPackageMapper materialMapper() {
        return (MaterialPackageMapper) Proxy.newProxyInstance(
                ListingDraftServiceTest.class.getClassLoader(),
                new Class<?>[] {MaterialPackageMapper.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class RecordingWorker extends ListingDraftGenerationWorker {
        private String generatedDraftId;

        private RecordingWorker() {
            super(new RecordingDraftMapper().proxy(), templateMapper(template()), new StubParser(material()),
                    new StubMaterialService(materialEntity(), MATERIAL_PATH), new ListingDraftFactory(),
                    (ignoredTemplate, ignoredMaterial) -> aiResult());
        }

        @Override
        public void generate(String draftId) {
            generatedDraftId = draftId;
        }
    }
}
