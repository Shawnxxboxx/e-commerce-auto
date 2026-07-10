package com.auto.ecommerce.ecommerceauto.material.parser;

import com.auto.ecommerce.ecommerceauto.material.model.MaterialTransactionRow;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeInfoTextParserTest {

    private final AttributeInfoTextParser parser = new AttributeInfoTextParser();

    @Test
    void parsesAttributeInfoText() {
        String text = """
                # 商品属性信息
                [产品信息]
                产品名称=黑白剪刀
                来源URL=https://example.com/item/1
                店铺=xxx店铺
                类目=家用工具/厨房工具
                品牌=无品牌

                [分类属性]
                使用=家用
                材质=不锈钢
                是否含有化学物质=否
                原产地=中国

                [资质合规]
                制造商=测试制造商
                欧盟责任人=测试责任人

                [变种属性]
                颜色=套装1
                规格=均码
                尺码表图片=size.jpg

                [交易信息]
                颜色|商品货号(SKC)|备货模式|尺码|SKU货号|不含税价(CNY)|库存|长|宽|高|重量(g)
                套装1|黑白剪刀|JIT备货|均码|黑白剪刀-套装1-均码|5.5|999|21|9|2|150
                """;

        ProductMaterialPackage material = parser.parse(text);

        assertThat(material.getProductName()).isEqualTo("黑白剪刀");
        assertThat(material.getSourceUrl()).isEqualTo("https://example.com/item/1");
        assertThat(material.getShopName()).isEqualTo("xxx店铺");
        assertThat(material.getCategoryName()).isEqualTo("家用工具/厨房工具");
        assertThat(material.getBrand()).isEqualTo("无品牌");
        assertThat(material.getCategoryAttributes()).containsEntry("材质", "不锈钢");
        assertThat(material.getVariantAttributes()).containsEntry("颜色", "套装1");
        assertThat(material.getSizeChartImageName()).isEqualTo("size.jpg");
        assertThat(material.getManufacturer()).isEqualTo("测试制造商");
        assertThat(material.getEuResponsiblePerson()).isEqualTo("测试责任人");

        MaterialTransactionRow row = material.getTransactionRows().getFirst();
        assertThat(row.getColor()).isEqualTo("套装1");
        assertThat(row.getSpecification()).isEqualTo("均码");
        assertThat(row.getStockingMode()).isEqualTo("JIT备货");
        assertThat(row.getSkc()).isEqualTo("黑白剪刀");
        assertThat(row.getSku()).isEqualTo("黑白剪刀-套装1-均码");
        assertThat(row.getPrice()).isEqualByComparingTo("5.5");
        assertThat(row.getStock()).isEqualTo(999);
        assertThat(row.getLength()).isEqualByComparingTo("21");
        assertThat(row.getWidth()).isEqualByComparingTo("9");
        assertThat(row.getHeight()).isEqualByComparingTo("2");
        assertThat(row.getWeightGram()).isEqualByComparingTo("150");
    }

    @Test
    void rejectsInvalidTransactionHeader() {
        String text = """
                [产品信息]
                产品名称=黑白剪刀
                店铺=xxx店铺
                类目=家用工具/厨房工具
                品牌=无品牌

                [分类属性]
                材质=不锈钢

                [资质合规]
                制造商=测试制造商
                欧盟责任人=测试责任人

                [变种属性]
                颜色=套装1

                [交易信息]
                颜色|规格|SKU货号
                套装1|均码|sku-1
                """;

        assertThatThrownBy(() -> parser.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("交易信息表头不符合约定");
    }
}
