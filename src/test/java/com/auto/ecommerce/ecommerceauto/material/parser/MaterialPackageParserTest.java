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
    void parsesMaterialPackageDirectory() throws Exception {
        Path packageDir = tempDir.resolve("黑白剪刀");
        Files.createDirectories(packageDir.resolve("主图"));
        Files.createDirectories(packageDir.resolve("副图"));
        Files.createDirectories(packageDir.resolve("尺码表"));
        Files.writeString(packageDir.resolve("主图").resolve("2.png"), "image");
        Files.writeString(packageDir.resolve("主图").resolve("1.jpg"), "image");
        Files.writeString(packageDir.resolve("副图").resolve("b.jpeg"), "image");
        Files.writeString(packageDir.resolve("副图").resolve("a.jpg"), "image");
        Files.writeString(packageDir.resolve("尺码表").resolve("size.jpg"), "image");
        Files.writeString(packageDir.resolve("属性信息.txt"), """
                [产品信息]
                产品名称=黑白剪刀
                来源URL=
                店铺=xxx店铺
                类目=家用工具/厨房工具
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

        MaterialPackageParser parser = new MaterialPackageParser(new AttributeInfoTextParser());

        ProductMaterialPackage material = parser.parse(packageDir);

        assertThat(material.getMaterialPackagePath()).isEqualTo(packageDir.toAbsolutePath().toString());
        assertThat(material.getMainImageSourcePaths())
                .containsExactly(
                        packageDir.resolve("主图").resolve("1.jpg").toAbsolutePath().toString(),
                        packageDir.resolve("主图").resolve("2.png").toAbsolutePath().toString()
                );
        assertThat(material.getDetailImagePaths())
                .containsExactly(
                        packageDir.resolve("副图").resolve("a.jpg").toAbsolutePath().toString(),
                        packageDir.resolve("副图").resolve("b.jpeg").toAbsolutePath().toString()
                );
        assertThat(material.getSizeChartImagePath())
                .isEqualTo(packageDir.resolve("尺码表").resolve("size.jpg").toAbsolutePath().toString());
    }
}
