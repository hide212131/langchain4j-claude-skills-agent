package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class DockerfileGenerator {

    private static final String MARKER = "# @skill-commands";
    private static final String SKILL_DEPS_FILE = "skill-deps.yaml";
    private static final String RUNTIME_DIR = ".skill-runtime";

    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public DockerfileGenerator() {
    }

    public List<DockerfileSpec> generate(Path skillsRoot, Path templatePath) {
        Objects.requireNonNull(skillsRoot, "skillsRoot");
        Objects.requireNonNull(templatePath, "templatePath");
        if (!Files.exists(skillsRoot)) {
            throw new IllegalArgumentException("SKILL ルートが見つかりません: " + skillsRoot);
        }
        if (!Files.isRegularFile(templatePath)) {
            throw new IllegalArgumentException("テンプレート Dockerfile が見つかりません: " + templatePath);
        }
        List<Path> depsFiles = collectSkillDeps(skillsRoot);
        if (depsFiles.isEmpty()) {
            throw new IllegalStateException("skill-deps.yaml が見つかりませんでした: " + skillsRoot);
        }
        List<DockerfileSpec> specs = new ArrayList<>();
        for (Path depsFile : depsFiles) {
            Path runtimeDir = depsFile.getParent();
            Path skillDir = runtimeDir.getParent();
            String skillPath = skillsRoot.relativize(skillDir).toString();
            List<String> commands = loadCommands(depsFile);
            Path dockerfilePath = runtimeDir.resolve("Dockerfile");
            writeDockerfile(templatePath, dockerfilePath, commands);
            specs.add(new DockerfileSpec(skillPath, dockerfilePath));
        }
        return specs;
    }

    private List<Path> collectSkillDeps(Path skillsRoot) {
        try (Stream<Path> stream = Files.walk(skillsRoot)) {
            return stream.filter(path -> SKILL_DEPS_FILE.equals(path.getFileName().toString())).filter(
                    path -> path.getParent() != null && RUNTIME_DIR.equals(path.getParent().getFileName().toString()))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("skill-deps.yaml の探索に失敗しました: " + skillsRoot, ex);
        }
    }

    private List<String> loadCommands(Path depsFile) {
        Object loaded;
        try {
            loaded = yaml.load(Files.readString(depsFile, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("skill-deps.yaml の読み込みに失敗しました: " + depsFile, ex);
        }
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        return normalizeCommands(readStringList(map, "commands"));
    }

    private List<String> readStringList(Map<String, Object> map, String key) {
        Object raw = map.get(key);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String value) {
            return value.isBlank() ? List.of() : List.of(value.trim());
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String value && !value.isBlank()) {
                results.add(value.trim());
            }
        }
        return results;
    }

    private List<String> normalizeCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String command : commands) {
            if (command == null) {
                continue;
            }
            String trimmed = command.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            unique.add(normalizeAptCommand(trimmed));
        }
        return List.copyOf(unique);
    }

    private String normalizeAptCommand(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        boolean hasAptInstall = lower.matches(".*\\bapt(-get)?\\s+install\\b.*");
        if (!hasAptInstall) {
            return command;
        }
        String updated = command;
        if (!lower.contains(" -y") && !lower.contains(" --yes")) {
            updated = command.replaceFirst("(?i)\\b(apt-get|apt)\\s+install\\b", "$1 install -y");
        }
        if (lower.contains("apt-get update") || lower.contains("apt update")) {
            return updated;
        }
        String prefix = lower.contains("sudo ") ? "sudo apt-get update && " : "apt-get update && ";
        return prefix + updated;
    }

    private void writeDockerfile(Path templatePath, Path outputPath, List<String> commands) {
        List<String> templateLines;
        try {
            templateLines = Files.readAllLines(templatePath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("テンプレート Dockerfile の読み込みに失敗しました: " + templatePath, ex);
        }
        List<String> output = applyTemplate(templateLines, buildCommandLines(commands));
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, String.join("\n", output) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Dockerfile の書き込みに失敗しました: " + outputPath, ex);
        }
    }

    private List<String> buildCommandLines(List<String> commands) {
        if (commands.isEmpty()) {
            return List.of("# 依存コマンドなし");
        }
        List<String> lines = new ArrayList<>();
        for (String command : commands) {
            lines.add("RUN " + command);
        }
        return lines;
    }

    private List<String> applyTemplate(List<String> templateLines, List<String> commandLines) {
        List<String> output = new ArrayList<>();
        boolean replaced = false;
        for (String line : templateLines) {
            if (MARKER.equals(line.strip())) {
                replaced = true;
                output.addAll(commandLines);
            } else {
                output.add(line);
            }
        }
        if (!replaced) {
            throw new IllegalStateException("テンプレートにマーカーが見つかりません: " + MARKER);
        }
        return output;
    }

    public record DockerfileSpec(String skillPath, Path path) {
    }
}
