package com.auto.ecommerce.ecommerceauto.draft.service;

import com.auto.ecommerce.ecommerceauto.draft.ai.AiDraftGenerationResult;
import com.auto.ecommerce.ecommerceauto.draft.ai.ListingDraftAiGenerator;
import com.auto.ecommerce.ecommerceauto.draft.dto.GenerateListingDraftRequest;
import com.auto.ecommerce.ecommerceauto.draft.dto.ListingDraftResponse;
import com.auto.ecommerce.ecommerceauto.draft.entity.ListingDraftEntity;
import com.auto.ecommerce.ecommerceauto.draft.mapper.ListingDraftMapper;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraftStatus;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Test
    void rejectsDeletingGeneratingDraft() {
        assertDeletingActiveDraftIsRejected(ListingDraftStatus.GENERATING);
    }

    @Test
    void rejectsDeletingPublishingDraft() {
        assertDeletingActiveDraftIsRejected(ListingDraftStatus.PUBLISHING);
    }

    @Test
    void quarantinesFilesAndDeletesDraftAndMaterial() {
        RecordingDraftMapper draftMapper = new RecordingDraftMapper();
        draftMapper.selected = deletableDraft(MATERIAL_ID);
        RecordingMaterialMapper materialMapper = new RecordingMaterialMapper();
        RecordingStorage storage = new RecordingStorage();

        service(draftMapper, materialService(materialMapper, storage)).delete("draft-1");

        assertThat(storage.quarantinedPackageId).isEqualTo(MATERIAL_ID);
        assertThat(draftMapper.deletedId).isEqualTo(1L);
        assertThat(materialMapper.deletedPackageId).isEqualTo(MATERIAL_ID);
        assertThat(storage.purgedPackageId).isEqualTo(MATERIAL_ID);
    }

    @Test
    void restoresFilesWhenDatabaseDeletionFails() {
        RecordingDraftMapper draftMapper = new RecordingDraftMapper();
        draftMapper.selected = deletableDraft(MATERIAL_ID);
        draftMapper.failDelete = true;
        RecordingStorage storage = new RecordingStorage();

        assertThatThrownBy(() -> service(draftMapper, materialService(new RecordingMaterialMapper(), storage))
                .delete("draft-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数据库删除失败");

        assertThat(storage.quarantinedPackageId).isEqualTo(MATERIAL_ID);
        assertThat(storage.restoredPackageId).isEqualTo(MATERIAL_ID);
        assertThat(storage.purgedPackageId).isNull();
    }

    @Test
    void doesNotDeleteDatabaseRecordsWhenQuarantineFails() {
        RecordingDraftMapper draftMapper = new RecordingDraftMapper();
        draftMapper.selected = deletableDraft(MATERIAL_ID);
        RecordingStorage storage = new RecordingStorage();
        storage.failQuarantine = true;
        RecordingMaterialMapper materialMapper = new RecordingMaterialMapper();

        assertThatThrownBy(() -> service(draftMapper, materialService(materialMapper, storage)).delete("draft-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("隔离失败");

        assertThat(draftMapper.deletedId).isNull();
        assertThat(materialMapper.deletedPackageId).isNull();
    }

    @Test
    void deletesOnlyDatabaseDraftForLegacyPath() {
        RecordingDraftMapper draftMapper = new RecordingDraftMapper();
        draftMapper.selected = deletableDraft(null);
        RecordingMaterialMapper materialMapper = new RecordingMaterialMapper();
        RecordingStorage storage = new RecordingStorage();

        service(draftMapper, materialService(materialMapper, storage)).delete("draft-1");

        assertThat(draftMapper.deletedId).isEqualTo(1L);
        assertThat(materialMapper.deletedPackageId).isNull();
        assertThat(storage.quarantinedPackageId).isNull();
    }

    private void assertDeletingActiveDraftIsRejected(ListingDraftStatus status) {
        RecordingDraftMapper draftMapper = new RecordingDraftMapper();
        draftMapper.selected = draftWithStatus(status, MATERIAL_ID);

        assertThatThrownBy(() -> service(draftMapper, materialService(new RecordingMaterialMapper(), new RecordingStorage()))
                .delete("draft-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能删除");

        assertThat(draftMapper.deletedId).isNull();
    }

    private ListingDraftService service(RecordingDraftMapper recorded, SopTemplateEntity template,
                                        StubParser parser, RecordingWorker worker,
                                        MaterialPackageService materialService) {
        return new ListingDraftService(
                recorded.proxy(), templateMapper(template), parser, materialService, new ListingDraftFactory(),
                worker, null, null, null, transactionTemplate());
    }

    private ListingDraftService service(RecordingDraftMapper recorded, MaterialPackageService materialService) {
        return service(recorded, template(), new StubParser(material()), new RecordingWorker(), materialService);
    }

    private static MaterialPackageService materialService(RecordingMaterialMapper mapper, RecordingStorage storage) {
        return new MaterialPackageService(mapper.proxy(), storage,
                new MaterialPackageParser(new AttributeInfoTextParser()), new ObjectMapper());
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

    private static ListingDraftEntity draftWithStatus(ListingDraftStatus status, String materialPackageId) {
        ListingDraftEntity entity = deletableDraft(materialPackageId);
        entity.setStatus(status.name());
        return entity;
    }

    private static ListingDraftEntity deletableDraft(String materialPackageId) {
        ListingDraftEntity entity = draftEntity(materialPackageId, "/legacy/material-1");
        entity.setId(1L);
        entity.setStatus(ListingDraftStatus.FAILED.name());
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
        private Long deletedId;
        private boolean failDelete;

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
                        case "deleteById" -> {
                            if (failDelete) {
                                throw new IllegalStateException("数据库删除失败");
                            }
                            deletedId = (Long) arguments[0];
                            yield 1;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class RecordingMaterialMapper {
        private String deletedPackageId;

        private MaterialPackageMapper proxy() {
            return (MaterialPackageMapper) Proxy.newProxyInstance(
                    ListingDraftServiceTest.class.getClassLoader(),
                    new Class<?>[] {MaterialPackageMapper.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "selectOne" -> materialEntity();
                        case "delete" -> {
                            deletedPackageId = MATERIAL_ID;
                            yield 1;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class RecordingStorage extends MaterialPackageStorageService {
        private String quarantinedPackageId;
        private String restoredPackageId;
        private String purgedPackageId;
        private boolean failQuarantine;

        private RecordingStorage() {
            super(new MaterialStorageProperties(Path.of("/tmp/material-test"), 1));
        }

        @Override
        public Path quarantine(String materialPackageId) {
            if (failQuarantine) {
                throw new IllegalStateException("隔离失败");
            }
            quarantinedPackageId = materialPackageId;
            return MATERIAL_PATH;
        }

        @Override
        public void restore(String materialPackageId) {
            restoredPackageId = materialPackageId;
        }

        @Override
        public void purgeQuarantine(String materialPackageId) {
            purgedPackageId = materialPackageId;
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

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new RecordingTransactionManager());
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

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
