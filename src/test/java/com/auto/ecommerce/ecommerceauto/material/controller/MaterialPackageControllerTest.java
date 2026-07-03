package com.auto.ecommerce.ecommerceauto.material.controller;

import com.auto.ecommerce.ecommerceauto.material.parser.AttributeInfoTextParser;
import com.auto.ecommerce.ecommerceauto.material.parser.MaterialPackageParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaterialPackageControllerTest {

    @Test
    void parsesUploadedMaterialDirectory() throws Exception {
        MaterialPackageParser parser = new MaterialPackageParser(new AttributeInfoTextParser());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MaterialPackageController(parser)).build();

        mockMvc.perform(multipart("/api/material-packages/parse-upload")
                        .file(file("黑白剪刀/属性信息.txt", """
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
                                """))
                        .file(file("黑白剪刀/主图/1.jpg", "image"))
                        .file(file("黑白剪刀/副图/a.jpg", "image"))
                        .file(file("黑白剪刀/尺码表/size.jpg", "image")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("黑白剪刀"))
                .andExpect(jsonPath("$.mainImageSourcePaths[0]").exists())
                .andExpect(jsonPath("$.detailImagePaths[0]").exists())
                .andExpect(jsonPath("$.sizeChartImagePath").exists());
    }

    private MockMultipartFile file(String path, String content) {
        return new MockMultipartFile("files", path, "text/plain", content.getBytes());
    }
}
