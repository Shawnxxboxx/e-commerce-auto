package com.auto.ecommerce.ecommerceauto.template.service;

import com.auto.ecommerce.ecommerceauto.template.dto.SopTemplateCreateRequest;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.auto.ecommerce.ecommerceauto.template.mapper.SopTemplateMapper;
import com.auto.ecommerce.ecommerceauto.template.service.impl.SopTemplateServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SopTemplateServiceImplTest {

    @Test
    void createsTemplateWithRequiredPrompts() {
        AtomicReference<SopTemplateEntity> inserted = new AtomicReference<>();
        SopTemplateMapper mapper = (SopTemplateMapper) Proxy.newProxyInstance(
                SopTemplateMapper.class.getClassLoader(),
                new Class<?>[]{SopTemplateMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        inserted.set((SopTemplateEntity) args[0]);
                        return 1;
                    }
                    return null;
                }
        );
        SopTemplateServiceImpl service = new SopTemplateServiceImpl(mapper);

        SopTemplateCreateRequest request = new SopTemplateCreateRequest();
        request.setName("TikTok 全托管马帮模板");
        request.setTitlePrompt("同时生成中文标题和英文标题");
        request.setMainImagePrompt("生成真实清晰主图提示词");

        SopTemplateEntity created = service.createTemplate(request);

        assertThat(created.getName()).isEqualTo("TikTok 全托管马帮模板");
        assertThat(created.getTitlePrompt()).contains("中文标题");
        assertThat(created.getMainImagePrompt()).contains("主图");
        assertThat(created.getGmtCreateTime()).isNotNull();
        assertThat(created.getGmtModifiedTime()).isNotNull();
        assertThat(inserted.get()).isSameAs(created);
    }
}
