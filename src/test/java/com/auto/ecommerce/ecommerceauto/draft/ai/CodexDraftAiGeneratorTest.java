package com.auto.ecommerce.ecommerceauto.draft.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}
