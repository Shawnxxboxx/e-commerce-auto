package com.auto.ecommerce.ecommerceauto.material.service;

import com.auto.ecommerce.ecommerceauto.material.dto.MaterialPackageResponse;
import com.auto.ecommerce.ecommerceauto.material.entity.MaterialPackageEntity;
import com.auto.ecommerce.ecommerceauto.material.mapper.MaterialPackageMapper;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.material.parser.AttributeInfoTextParser;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import com.auto.ecommerce.ecommerceauto.material.config.MaterialStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialPackageServiceTest {

    @Test
    void uploadsParsesPersistsAndReturnsRelativeImagePaths() {
        RecordingMapper mapper = new RecordingMapper();
        Path packagePath = Path.of("/tmp/materials/material-1");
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "属性信息.txt", "text/plain", "[x]".getBytes()),
                new MockMultipartFile("files", "1.jpg", "image/jpeg", new byte[] {1, 2, 3})
        );
        List<String> paths = List.of("属性信息.txt", "主图/1.jpg");
        ProductMaterialPackage parsedMaterial = parsedMaterial(packagePath);
        StubStorage storage = new StubStorage(packagePath);
        StubParser parser = new StubParser(parsedMaterial);
        MaterialPackageService service = new MaterialPackageService(mapper.proxy(), storage, parser, new ObjectMapper());

        MaterialPackageResponse result = service.upload("眼镜素材", files, paths);

        assertThat(result.getMaterialPackageId()).startsWith("material-");
        assertThat(result.getOriginalDirectoryName()).isEqualTo("眼镜素材");
        assertThat(result.getMainImageSourcePaths()).containsExactly("主图/1.jpg");
        assertThat(result.getDetailImagePaths()).containsExactly("副图/1.jpg");
        assertThat(result.getSizeChartImagePath()).isEqualTo("尺码表/size.jpg");
        assertThat(result.getSizeChartImagePaths()).containsExactly("尺码表/size.jpg");
        assertThat(result.getPackageImagePaths()).containsExactly("包装图/1.jpg");

        MaterialPackageEntity entity = mapper.insertedEntity;
        assertThat(entity).isNotNull();
        assertThat(entity.getMaterialPackageId()).isEqualTo(result.getMaterialPackageId());
        assertThat(entity.getOriginalDirectoryName()).isEqualTo("眼镜素材");
        assertThat(entity.getStoragePath()).isEqualTo(packagePath.toString());
        assertThat(entity.getFileCount()).isEqualTo(2);
        assertThat(entity.getTotalSize()).isEqualTo(6);
        assertThat(entity.getParsedJson()).contains(packagePath.resolve("主图/1.jpg").toString());
    }

    @Test
    void removesStoredDirectoryWhenParsingFails() {
        RecordingMapper mapper = new RecordingMapper();
        Path packagePath = Path.of("/tmp/materials/material-1");
        List<MultipartFile> files = List.of(new MockMultipartFile("files", "属性信息.txt", "text/plain", new byte[0]));
        List<String> paths = List.of("属性信息.txt");
        StubStorage storage = new StubStorage(packagePath);
        StubParser parser = new StubParser(new IllegalArgumentException("缺少 属性信息.txt"));
        MaterialPackageService service = new MaterialPackageService(mapper.proxy(), storage, parser, new ObjectMapper());

        assertThatThrownBy(() -> service.upload("错误素材", files, paths))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("缺少 属性信息.txt");

        assertThat(storage.deletedPackageId).startsWith("material-");
        assertThat(mapper.insertedEntity).isNull();
    }

    private static ProductMaterialPackage parsedMaterial(Path packagePath) {
        ProductMaterialPackage material = new ProductMaterialPackage();
        material.setProductName("眼镜");
        material.setMaterialPackagePath(packagePath.toString());
        material.setMainImageSourcePaths(List.of(packagePath.resolve("主图/1.jpg").toString()));
        material.setDetailImagePaths(List.of(packagePath.resolve("副图/1.jpg").toString()));
        material.setSizeChartImagePath(packagePath.resolve("尺码表/size.jpg").toString());
        material.setSizeChartImagePaths(List.of(packagePath.resolve("尺码表/size.jpg").toString()));
        material.setPackageImagePaths(List.of(packagePath.resolve("包装图/1.jpg").toString()));
        return material;
    }

    private static final class RecordingMapper {
        private MaterialPackageEntity insertedEntity;

        private MaterialPackageMapper proxy() {
            return (MaterialPackageMapper) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {MaterialPackageMapper.class},
                    (proxy, method, arguments) -> {
                        if ("insert".equals(method.getName())) {
                            insertedEntity = (MaterialPackageEntity) arguments[0];
                            return 1;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class StubStorage extends MaterialPackageStorageService {
        private final Path storedPath;
        private String deletedPackageId;

        private StubStorage(Path storedPath) {
            super(new MaterialStorageProperties(storedPath.getParent(), 50L * 1024 * 1024));
            this.storedPath = storedPath;
        }

        @Override
        public Path store(String materialPackageId, List<MultipartFile> files, List<String> relativePaths) {
            return storedPath;
        }

        @Override
        public void deletePackage(String materialPackageId) {
            deletedPackageId = materialPackageId;
        }
    }

    private static final class StubParser extends MaterialPackageParser {
        private final ProductMaterialPackage material;
        private final RuntimeException exception;

        private StubParser(ProductMaterialPackage material) {
            super(new AttributeInfoTextParser());
            this.material = material;
            this.exception = null;
        }

        private StubParser(RuntimeException exception) {
            super(new AttributeInfoTextParser());
            this.material = null;
            this.exception = exception;
        }

        @Override
        public ProductMaterialPackage parse(Path materialPackagePath) {
            if (exception != null) {
                throw exception;
            }
            return material;
        }
    }
}
