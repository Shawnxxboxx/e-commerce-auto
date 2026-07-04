package com.auto.ecommerce.ecommerceauto.draft.ai;

import com.auto.ecommerce.ecommerceauto.material.model.ProductMaterialPackage;
import com.auto.ecommerce.ecommerceauto.template.entity.SopTemplateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
            long timestamp = System.currentTimeMillis();
            Path titleResponseFile = outputDir.resolve("codex-title-" + timestamp + ".json");
            Path mainImageResponseFile = outputDir.resolve("codex-main-image-" + timestamp + ".json");
            Path mainImagePath = outputDir.resolve("main-" + System.currentTimeMillis() + ".png");

            runCodex("title", packagePath, titleResponseFile, buildTitlePrompt(template, material), List.of());
            AiDraftGenerationResult titleResult = parseResult(Files.readString(titleResponseFile, StandardCharsets.UTF_8));

            runCodex("main-image", packagePath, mainImageResponseFile, buildMainImagePrompt(template, mainImagePath), material.getMainImageSourcePaths());
            AiDraftGenerationResult imageResult = parseResult(Files.readString(mainImageResponseFile, StandardCharsets.UTF_8));

            AiDraftGenerationResult result = new AiDraftGenerationResult();
            result.setChineseTitle(titleResult.getChineseTitle());
            result.setEnglishTitle(titleResult.getEnglishTitle());
            result.setMainImagePath(imageResult.getMainImagePath());
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

    private void runCodex(String stage, Path packagePath, Path responseFile, String prompt, List<String> images)
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

        Path logFile = responseFile.resolveSibling(responseFile.getFileName() + ".log");
        long startTime = System.currentTimeMillis();
        log.info("开始执行 Codex AI 生成 [{}]，素材包: {}", stage, packagePath);
        log.info("Codex 命令: {}", printableCommand(command));
        log.info("Codex 响应文件: {}", responseFile);
        log.info("Codex 日志文件: {}", logFile);
        Process process = new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.DISCARD)
                .redirectErrorStream(true)
                .start();
        Thread logThread = streamProcessLog(process, logFile);
        boolean finished = process.waitFor(Duration.ofSeconds(timeoutSeconds).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            logThread.join(1000);
            throw new IllegalStateException("Codex 执行超时，日志文件: " + logFile);
        }
        logThread.join(1000);
        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("Codex 执行结束 [{}]，退出码: {}, 耗时: {}ms", stage, process.exitValue(), elapsedMs);
        if (process.exitValue() != 0) {
            String output = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            throw new IllegalStateException("Codex 退出码 " + process.exitValue() + ": " + output);
        }
    }

    private Thread streamProcessLog(Process process, Path logFile) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(Files.newBufferedWriter(logFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(line);
                    writer.flush();
                    log.info("[codex] {}", line);
                }
            } catch (IOException e) {
                log.warn("读取 Codex 日志失败: {}", e.getMessage());
            }
        }, "codex-exec-log-reader");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private String printableCommand(List<String> command) {
        return String.join(" ", command);
    }

    String buildTitlePrompt(SopTemplateEntity template, ProductMaterialPackage material) {
        return template.getTitlePrompt().contains("%s")
                ? template.getTitlePrompt().replace("%s", material.getProductName())
                : template.getTitlePrompt();
    }

    String buildMainImagePrompt(SopTemplateEntity template, Path mainImagePath) {
        return template.getMainImagePrompt().contains("%s")
                ? template.getMainImagePrompt().replace("%s", mainImagePath.toString())
                : template.getMainImagePrompt();
    }
}
