package com.auto.ecommerce.ecommerceauto.draft.ai;

import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CodexDraftAiGenerator implements ListingDraftAiGenerator {

    private final ObjectMapper objectMapper;

    public CodexDraftAiGenerator() {
        this(new ObjectMapper());
    }

    CodexDraftAiGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Setter
    @Value("${ai.codex.command:/Applications/Codex.app/Contents/Resources/codex}")
    private String codexCommand = "/Applications/Codex.app/Contents/Resources/codex";

    @Setter
    @Value("${ai.codex.timeout-seconds:600}")
    private long timeoutSeconds = 600;

    @Override
    public AiDraftGenerationResult generate(SopTemplateEntity template, ProductMaterialPackage material) {
        try {
            Path packagePath = Path.of(material.getMaterialPackagePath());
            Path outputDir = packagePath.resolve("AI生成");
            Files.createDirectories(outputDir);
            Path responseFile = outputDir.resolve("codex-result-" + System.currentTimeMillis() + ".json");
            Path mainImagePath = outputDir.resolve("main-" + System.currentTimeMillis() + ".png");

            runCodex(packagePath, responseFile, buildPrompt(template, material, mainImagePath), material.getMainImageSourcePaths());
            AiDraftGenerationResult result = parseResult(Files.readString(responseFile, StandardCharsets.UTF_8));
            if (result.getMainImagePath() == null || result.getMainImagePath().isBlank()) {
                result.setMainImagePath(mainImagePath.toString());
            }
            if (!Files.exists(Path.of(result.getMainImagePath()))) {
                throw new IllegalStateException("Codex 未生成主图文件: " + result.getMainImagePath());
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Codex 执行失败: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Codex 执行失败: " + e.getMessage(), e);
        }
    }

    AiDraftGenerationResult parseResult(String text) {
        // Codex 最终回复可能带 Markdown 包裹，这里只取第一段 JSON 对象。
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Codex 输出不是 JSON: " + text);
        }
        try {
            return objectMapper.readValue(text.substring(start, end + 1), AiDraftGenerationResult.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Codex JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private void runCodex(Path packagePath, Path responseFile, String prompt, List<String> images)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(codexCommand);
        command.add("exec");
        command.add("--cd");
        command.add(packagePath.toString());
        command.add("--skip-git-repo-check");
        command.add("--sandbox");
        command.add("workspace-write");
        command.add("-o");
        command.add(responseFile.toString());
        for (String image : images == null ? List.<String>of() : images) {
            command.add("--image");
            command.add(image);
        }
        command.add(prompt);

        log.info("开始执行 Codex AI 生成，素材包: {}", packagePath);
        Path logFile = responseFile.resolveSibling(responseFile.getFileName() + ".log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
        boolean finished = process.waitFor(Duration.ofSeconds(timeoutSeconds).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Codex 执行超时");
        }
        if (process.exitValue() != 0) {
            String output = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            throw new IllegalStateException("Codex 退出码 " + process.exitValue() + ": " + output);
        }
    }

    private String buildPrompt(SopTemplateEntity template, ProductMaterialPackage material, Path mainImagePath) {
        return """
                你是电商上架助手。请根据素材包信息和 SOP 提示词生成草稿需要的 AI 字段。

                标题提示词:
                %s

                主图提示词:
                %s

                产品信息:
                产品名称=%s
                店铺=%s
                类目=%s
                品牌=%s
                来源URL=%s

                主图源图片已通过 --image 附加。请结合主图提示词生成最终商品主图，并保存到:
                %s

                最终回复只输出 JSON，不要解释，不要 Markdown:
                {"chineseTitle":"中文标题","englishTitle":"English Title","mainImagePath":"%s"}
                """.formatted(
                template.getTitlePrompt(),
                template.getMainImagePrompt(),
                material.getProductName(),
                material.getShopName(),
                material.getCategoryName(),
                material.getBrand(),
                material.getSourceUrl(),
                mainImagePath,
                mainImagePath);
    }
}
