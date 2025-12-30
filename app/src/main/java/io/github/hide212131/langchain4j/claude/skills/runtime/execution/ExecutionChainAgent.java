package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ExecutionChainAgent {

    private final CodeExecutionEnvironmentFactory environmentFactory;

    public ExecutionChainAgent(CodeExecutionEnvironmentFactory environmentFactory) {
        this.environmentFactory = Objects.requireNonNull(environmentFactory, "environmentFactory");
    }

    @Agent(description = "コード実行計画に従ってツールチェインを実行する", outputKey = "executionReport")
    public String execute(@V("executionPlan") String executionPlanJson, @V("skillPath") String skillPath,
            @V("artifactsDir") String artifactsDir) {
        if (skillPath == null || skillPath.isBlank()) {
            throw new IllegalArgumentException("skillPath は必須です");
        }
        ExecutionPlan plan = ExecutionPlan.parse(executionPlanJson);
        List<ExecutionResult> results = new ArrayList<>();
        try (CodeExecutionEnvironment environment = environmentFactory.create(Path.of(skillPath))) {
            for (String command : plan.commands()) {
                String trimmed = command == null ? "" : command.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String adjusted = normalizeCommand(trimmed);
                String prepared = prepareHtml2PptxCommand(adjusted, environment, results);
                runCommand(environment, prepared, results);
            }
            List<String> artifacts = collectArtifacts(environment, plan.effectiveOutputPatterns());
            List<String> reportArtifacts = resolveArtifacts(environment, artifacts, artifactsDir);
            return ExecutionReportFormatter.format(results, reportArtifacts);
        }
    }

    private String normalizeCommand(String command) {
        return command.replaceFirst("^python(3)?\\s+", "/opt/venv/bin/python ");
    }

    private String prepareHtml2PptxCommand(String command, CodeExecutionEnvironment environment,
            List<ExecutionResult> results) {
        List<String> tokens = splitCommand(command);
        int scriptIndex = findHtml2PptxIndex(tokens);
        if (scriptIndex < 0) {
            return command;
        }
        List<String> args = tokens.subList(scriptIndex + 1, tokens.size());
        Html2PptxArgs parsed = parseHtml2PptxArgs(args);
        ensureHtml2PptxRunner(environment, results);
        ensureHtmlFiles(parsed.htmlFiles, environment, results);
        return "node run-html2pptx.js " + String.join(" ", parsed.htmlFiles) + " -o " + parsed.outputFile;
    }

    private void ensureHtml2PptxRunner(CodeExecutionEnvironment environment, List<ExecutionResult> results) {
        String script = """
                const pptxgen = require("pptxgenjs");
                const html2pptx = require("./scripts/html2pptx.js");

                async function main() {
                  const args = process.argv.slice(2);
                  const outputIndex = args.indexOf("-o");
                  let output = "output.pptx";
                  let htmlFiles = args;
                  if (outputIndex >= 0) {
                    output = args[outputIndex + 1] || output;
                    htmlFiles = args.slice(0, outputIndex);
                  } else {
                    const last = args[args.length - 1];
                    if (last && last.endsWith(".pptx")) {
                      output = last;
                      htmlFiles = args.slice(0, -1);
                    }
                  }
                  if (htmlFiles.length === 0) {
                    throw new Error("HTML ファイルが指定されていません");
                  }
                  const pptx = new pptxgen();
                  pptx.layout = "LAYOUT_16x9";
                  for (const file of htmlFiles) {
                    await html2pptx(file, pptx);
                  }
                  await pptx.writeFile({ fileName: output });
                }

                main().catch((err) => {
                  console.error(err && err.stack ? err.stack : err);
                  process.exit(1);
                });
                """;
        String command = """
                test -f run-html2pptx.js || cat <<'EOF' > run-html2pptx.js
                %s
                EOF
                """.formatted(script);
        runCommand(environment, command, results);
    }

    private void ensureHtmlFiles(List<String> htmlFiles, CodeExecutionEnvironment environment,
            List<ExecutionResult> results) {
        int index = 1;
        for (String file : htmlFiles) {
            String title = "Slide " + index;
            String body = defaultHtml(title);
            String mkdir = buildMkdirCommand(file);
            if (mkdir != null) {
                runCommand(environment, mkdir, results);
            }
            String command = """
                    test -f "%s" || cat <<'EOF' > "%s"
                    %s
                    EOF
                    """.formatted(file, file, body);
            runCommand(environment, command, results);
            index++;
        }
    }

    private String buildMkdirCommand(String file) {
        Path path = Path.of(file);
        Path parent = path.getParent();
        if (parent == null) {
            return null;
        }
        return "mkdir -p \"" + parent + "\"";
    }

    private String defaultHtml(String title) {
        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <style>
                    body { margin: 0; width: 960px; height: 540px; font-family: Arial, sans-serif; }
                    .slide { padding: 40px; }
                    h1 { font-size: 48px; margin: 0 0 16px 0; }
                    p { font-size: 20px; margin: 0; }
                  </style>
                </head>
                <body>
                  <div class="slide">
                    <h1>%s</h1>
                    <p>ランタイムで生成しました。</p>
                  </div>
                </body>
                </html>
                """.formatted(title);
    }

    private void runCommand(CodeExecutionEnvironment environment, String command, List<ExecutionResult> results) {
        CommandPolicy.validate(command);
        ExecutionResult result = environment.executeCommand(command);
        results.add(result);
        if (result.exitCode() != 0) {
            String details = """
                    コマンド: %s
                    exitCode: %d
                    stdout: %s
                    stderr: %s
                    """.formatted(command, result.exitCode(), result.stdout(), result.stderr());
            throw new IllegalStateException("コマンドの実行に失敗しました。\n" + details);
        }
    }

    private List<String> splitCommand(String command) {
        return new ArrayList<>(List.of(command.split("\\s+")));
    }

    private int findHtml2PptxIndex(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.endsWith("scripts/html2pptx.js")) {
                return i;
            }
        }
        return -1;
    }

    private Html2PptxArgs parseHtml2PptxArgs(List<String> args) {
        String output = "output.pptx";
        List<String> htmlFiles = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("-o".equals(arg) && i + 1 < args.size()) {
                output = args.get(i + 1);
                i++;
                continue;
            }
            if (arg.endsWith(".pptx")) {
                output = arg;
                continue;
            }
            if (arg.endsWith(".html")) {
                htmlFiles.add(arg);
            }
        }
        if (htmlFiles.isEmpty()) {
            htmlFiles.add("slide1.html");
            htmlFiles.add("slide2.html");
            htmlFiles.add("slide3.html");
        }
        return new Html2PptxArgs(htmlFiles, output);
    }

    private record Html2PptxArgs(List<String> htmlFiles, String outputFile) {
    }

    private List<String> collectArtifacts(CodeExecutionEnvironment environment, List<String> patterns) {
        List<String> artifacts = new ArrayList<>();
        for (String pattern : patterns) {
            artifacts.addAll(environment.listFiles(pattern));
        }
        return List.copyOf(artifacts);
    }

    private List<String> resolveArtifacts(CodeExecutionEnvironment environment, List<String> artifacts,
            String artifactsDir) {
        if (artifactsDir == null || artifactsDir.isBlank() || artifacts.isEmpty()) {
            return artifacts;
        }
        Path targetDir = Path.of(artifactsDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException ex) {
            throw new IllegalStateException("成果物ディレクトリの作成に失敗しました: " + targetDir, ex);
        }
        List<String> localPaths = new ArrayList<>();
        for (String artifact : artifacts) {
            Path relative = toRelativePath(artifact);
            Path localPath = targetDir.resolve(relative);
            Path parent = localPath.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException ex) {
                    throw new IllegalStateException("成果物ディレクトリの作成に失敗しました: " + parent, ex);
                }
            }
            byte[] payload = environment.downloadFile(artifact);
            try {
                Files.write(localPath, payload);
            } catch (IOException ex) {
                throw new IllegalStateException("成果物の保存に失敗しました: " + localPath, ex);
            }
            localPaths.add(localPath.toString());
        }
        return List.copyOf(localPaths);
    }

    private Path toRelativePath(String artifact) {
        String normalized = artifact.replace('\\', '/');
        String relative = normalized.startsWith("/workspace/") ? normalized.substring("/workspace/".length())
                : normalized;
        Path candidate = Path.of(relative).normalize();
        if (candidate.isAbsolute() || candidate.startsWith("..")) {
            Path fileName = candidate.getFileName();
            return fileName == null ? Path.of("artifact") : Path.of(fileName.toString());
        }
        return candidate;
    }
}
