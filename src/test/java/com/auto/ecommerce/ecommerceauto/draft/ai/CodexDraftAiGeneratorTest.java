package com.auto.ecommerce.ecommerceauto.draft.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodexDraftAiGeneratorTest {

    @Test
    void parsesJsonFromMarkdownResponse() {
        CodexDraftAiGenerator generator = new CodexDraftAiGenerator(new ObjectMapper());

        AiDraftGenerationResult result = generator.parseResult("""
                已完成。
                ```json
                {"chineseTitle":"黑色连衣裙","englishTitle":"Black Dress","mainImagePath":"/tmp/main.png"}
                ```
                """);

        assertThat(result.getChineseTitle()).isEqualTo("黑色连衣裙");
        assertThat(result.getEnglishTitle()).isEqualTo("Black Dress");
        assertThat(result.getMainImagePath()).isEqualTo("/tmp/main.png");
    }

    @Test
    void buildsSeparateTitleAndMainImagePrompts() {
        CodexDraftAiGenerator generator = new CodexDraftAiGenerator(new ObjectMapper());
        SopTemplateEntity template = new SopTemplateEntity();
        template.setTitlePrompt("生成适合 TikTok 的中英文标题，产品【%s】");
        template.setMainImagePrompt("生成白底商品主图，保存到 %s，返回 %s");
        ProductMaterialPackage material = new ProductMaterialPackage();
        material.setProductName("黑色连衣裙");

        String titlePrompt = generator.buildTitlePrompt(template, material);
        String imagePrompt = generator.buildMainImagePrompt(template, Path.of("/tmp/main.png"));

        assertThat(titlePrompt)
                .contains("生成适合 TikTok 的中英文标题", "黑色连衣裙")
                .doesNotContain("生成白底商品主图", "/tmp/main.png");
        assertThat(imagePrompt)
                .contains("生成白底商品主图", "/tmp/main.png")
                .doesNotContain("生成适合 TikTok 的中英文标题", "黑色连衣裙");
    }

    @Test
    void keepsMainImagePromptAsTemplateWhenNoPlaceholder() {
        CodexDraftAiGenerator generator = new CodexDraftAiGenerator(new ObjectMapper());
        SopTemplateEntity template = new SopTemplateEntity();
        template.setMainImagePrompt("完全由主图模板决定输出。");

        assertThat(generator.buildMainImagePrompt(template, Path.of("/tmp/main.png"))).isEqualTo("完全由主图模板决定输出。");
    }

    @Test
    void keepsTitlePromptAsTemplateWhenNoPlaceholder() {
        CodexDraftAiGenerator generator = new CodexDraftAiGenerator(new ObjectMapper());
        SopTemplateEntity template = new SopTemplateEntity();
        template.setTitlePrompt("完全由模板决定输出。");
        ProductMaterialPackage material = new ProductMaterialPackage();
        material.setProductName("黑色连衣裙");

        assertThat(generator.buildTitlePrompt(template, material)).isEqualTo("完全由模板决定输出。");
    }

    @Test
    void replacesProductNamePlaceholderInTitlePrompt() {
        CodexDraftAiGenerator generator = new CodexDraftAiGenerator(new ObjectMapper());
        SopTemplateEntity template = new SopTemplateEntity();
        template.setTitlePrompt("请根据产品名称【%s】生成中英文标题，不要使用 100%有效。");
        ProductMaterialPackage material = new ProductMaterialPackage();
        material.setProductName("黑色连衣裙");

        String prompt = generator.buildTitlePrompt(template, material);

        assertThat(prompt)
                .contains("产品名称【黑色连衣裙】")
                .contains("100%有效")
                .doesNotContain("%s", "产品名称=黑色连衣裙");
    }

    @Test
    void replacesImagePathPlaceholderWithoutFormattingPercentText() {
        CodexDraftAiGenerator generator = new CodexDraftAiGenerator(new ObjectMapper());
        SopTemplateEntity template = new SopTemplateEntity();
        template.setMainImagePrompt("商品占画面约 70%–85%，保存到 %s，返回 %s。");

        String prompt = generator.buildMainImagePrompt(template, Path.of("/tmp/main.png"));

        assertThat(prompt)
                .contains("70%–85%", "/tmp/main.png")
                .doesNotContain("%s");
    }

    @Test
    void separatesPromptFromImageArguments() {
        CodexDraftAiGenerator generator = new CodexDraftAiGenerator(new ObjectMapper());

        List<String> command = generator.buildCodexCommand(
                Path.of("/tmp/pkg"),
                Path.of("/tmp/pkg/AI生成/result.json"),
                "生成主图",
                List.of("/tmp/pkg/主图/a.jpg"));

        assertThat(command).containsSubsequence("--image", "/tmp/pkg/主图/a.jpg", "--", "生成主图");
    }

    @Test
    void fallsBackToInstalledChatGptCodexWhenLegacyPathIsMissing() {
        CodexDraftAiGenerator generator = new CodexDraftAiGenerator(new ObjectMapper());
        generator.setCodexCommand("/Applications/Codex.app/Contents/Resources/codex");

        assertThat(generator.resolveCodexCommand())
                .isEqualTo("/Applications/ChatGPT.app/Contents/Resources/codex");
    }
}
