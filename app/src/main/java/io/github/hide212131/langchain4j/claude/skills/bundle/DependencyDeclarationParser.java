package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@SuppressWarnings({ "PMD.GodClass", "PMD.TooManyMethods", "PMD.AvoidCatchingGenericException" })
public final class DependencyDeclarationParser {

    private static final Pattern FRONT_MATTER_END = Pattern.compile("^---[\\t ]*$", Pattern.MULTILINE);
    private static final Pattern APT_UPDATE_INSTALL = Pattern
            .compile("(?i)(apt-get\\s+update\\s*&&\\s*apt(?:-get)?\\s+install\\s+[^#;]+)");
    private static final Pattern APT_COMMAND = Pattern.compile("(?i)(?:^|\\s)(apt(?:-get)?\\s+install\\s+[^#;]+)");
    private static final Pattern PIP_COMMAND = Pattern
            .compile("(?i)(?:^|\\s)((?:pip3?|python\\s+-m\\s+pip)\\s+install\\s+[^#;]+)");
    private static final Pattern NPM_COMMAND = Pattern.compile("(?i)(?:^|\\s)(npm\\s+install\\s+[^#;]+)");
    private static final Pattern COMMAND_PREFIX = Pattern.compile(
            "(?i)^\\s*(?:sudo\\s+)?(apt-get|apt|pip3?|python\\s+-m\\s+pip|npm|yarn|pnpm|brew|conda|poetry)\\b");

    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    private final LlmDependencyExtractor llmExtractor;

    public DependencyDeclarationParser() {
        this(new LlmDependencyExtractor());
    }

    DependencyDeclarationParser(LlmDependencyExtractor llmExtractor) {
        this.llmExtractor = Objects.requireNonNull(llmExtractor, "llmExtractor");
    }

    /**
     * SKILL.md の依存宣言は frontmatter の dependencies を正とし、本文のインストールコマンドは補助的に抽出する。 LLM
     * による抽出に失敗した場合はルールベースにフォールバックする。
     */
    public SkillDependency parse(Path skillMdPath) {
        Objects.requireNonNull(skillMdPath, "skillMdPath");
        String content = readContent(skillMdPath);
        FrontMatter frontMatter = extractFrontMatter(skillMdPath, content);
        Map<String, Object> frontMatterMap = loadYaml(skillMdPath, frontMatter.yamlSection());
        String skillId = resolveId(skillMdPath, frontMatterMap);
        DependencySection dependencies = extractDependencies(frontMatterMap);
        Set<String> warnings = new LinkedHashSet<>(dependencies.warnings());
        if (dependencies.commands().isEmpty()) {
            Optional<DependencySection> llmSection = extractWithLlm(skillId, frontMatter.bodySection(),
                    skillMdPath.toString());
            if (llmSection.isPresent()) {
                dependencies = llmSection.get();
                warnings.addAll(dependencies.warnings());
            } else {
                DependencySection fallback = extractFromBody(frontMatter.bodySection());
                dependencies = fallback;
                warnings.add("LLM 抽出に失敗したためルールベースにフォールバックしました。");
                if (dependencies.commands().isEmpty()) {
                    warnings.add("本文から依存コマンドを抽出できませんでした。");
                }
            }
        }
        List<String> resolvedCommands = resolveCommands(skillId, skillMdPath.toString(), dependencies.commands(),
                warnings);
        return new SkillDependency(skillId, resolvedCommands, List.copyOf(warnings));
    }

    private String readContent(Path skillMdPath) {
        try {
            return Files.readString(skillMdPath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("SKILL.md を読み取れませんでした: " + skillMdPath, ex);
        }
    }

    private FrontMatter extractFrontMatter(Path skillMdPath, String content) {
        if (!content.startsWith("---")) {
            throw new IllegalArgumentException("SKILL.md の先頭に frontmatter がありません: " + skillMdPath);
        }
        Matcher matcher = FRONT_MATTER_END.matcher(content);
        if (!matcher.find() || !matcher.find()) {
            throw new IllegalArgumentException("SKILL.md の frontmatter 終端が見つかりません: " + skillMdPath);
        }
        int end = matcher.end();
        String yamlSection = content.substring(3, end - 3);
        String bodySection = content.substring(end);
        return new FrontMatter(yamlSection, bodySection);
    }

    private Map<String, Object> loadYaml(Path skillMdPath, String yamlSection) {
        try {
            Map<String, Object> map = yaml.load(yamlSection);
            return map == null ? Map.of() : map;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("SKILL.md の frontmatter を YAML として解釈できません: " + skillMdPath, ex);
        }
    }

    private String resolveId(Path skillMdPath, Map<String, Object> frontMatter) {
        Object raw = frontMatter.get("id");
        if (raw instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        Path parent = skillMdPath.getParent();
        if (parent != null && parent.getFileName() != null) {
            return parent.getFileName().toString();
        }
        return skillMdPath.getFileName().toString();
    }

    @SuppressWarnings("unchecked")
    private DependencySection extractDependencies(Map<String, Object> frontMatter) {
        Object raw = frontMatter.get("dependencies");
        if (!(raw instanceof Map<?, ?>)) {
            return DependencySection.empty();
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        List<String> commands = readStringList(map, "commands");
        if (commands.isEmpty()) {
            commands = readStringList(map, "install_commands");
        }
        List<String> warnings = readStringList(map, "warnings");
        return new DependencySection(normalizeCommands(commands), warnings);
    }

    private DependencySection extractFromBody(String bodySection) {
        Set<String> commands = new LinkedHashSet<>();
        for (String line : bodySection.split("\\R")) {
            String cleaned = cleanupLine(line);
            if (cleaned.isBlank()) {
                continue;
            }
            collectCommand(commands, cleaned, APT_UPDATE_INSTALL);
            collectCommand(commands, cleaned, APT_COMMAND);
            collectCommand(commands, cleaned, PIP_COMMAND);
            collectCommand(commands, cleaned, NPM_COMMAND);
        }
        return new DependencySection(List.copyOf(commands), List.of());
    }

    private String cleanupLine(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("```")) {
            return "";
        }
        trimmed = trimmed.replace("`", " ");
        trimmed = trimmed.replaceFirst("^\\s*[-*]\\s+", "");
        trimmed = trimmed.replaceFirst("^\\s*\\d+\\.\\s+", "");
        return trimmed.strip();
    }

    private Optional<DependencySection> extractWithLlm(String skillId, String bodySection, String sourcePath) {
        return llmExtractor.extract(skillId, bodySection, sourcePath).map(this::toDependencySection);
    }

    private DependencySection toDependencySection(LlmDependencyExtractor.DependencyExtractionResult result) {
        return new DependencySection(normalizeCommands(result.commands()), normalizeWarnings(result.warnings()));
    }

    private void collectCommand(Set<String> target, String line, Pattern pattern) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String command = matcher.group(1).trim();
            if (!command.isBlank()) {
                target.add(command);
            }
        }
    }

    private List<String> resolveCommands(String skillId, String sourcePath, List<String> commands,
            Set<String> warnings) {
        List<String> normalized = normalizeCommands(commands);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>();
        for (String command : normalized) {
            String cleaned = normalizeCommand(command);
            if (cleaned.isBlank()) {
                continue;
            }
            if (isLikelyCommand(cleaned)) {
                resolved.add(cleaned);
                continue;
            }
            Optional<LlmDependencyExtractor.ConvertedCommand> converted = llmExtractor.convertInstruction(skillId,
                    cleaned, sourcePath);
            if (converted.isPresent() && converted.get().command() != null && !converted.get().command().isBlank()) {
                String convertedCommand = converted.get().command().trim();
                resolved.add(convertedCommand);
                warnings.add("自然文の指示を LLM でコマンド化しました: \"" + cleaned + "\" -> \"" + convertedCommand + "\"");
                if (converted.get().warning() != null && !converted.get().warning().isBlank()) {
                    warnings.add(converted.get().warning().trim());
                }
            } else {
                warnings.add("自然文の指示をコマンド化できませんでした: \"" + cleaned + "\"");
            }
        }
        return resolved;
    }

    private boolean isLikelyCommand(String command) {
        return COMMAND_PREFIX.matcher(command).find();
    }

    private String normalizeCommand(String command) {
        String trimmed = command.trim();
        if (trimmed.startsWith("sudo ")) {
            trimmed = trimmed.substring(5).trim();
        }
        return trimmed;
    }

    private List<String> normalizeCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String command : commands) {
            if (command == null) {
                continue;
            }
            String trimmed = command.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private List<String> normalizeWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String warning : warnings) {
            if (warning == null) {
                continue;
            }
            String trimmed = warning.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
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
            throw new IllegalArgumentException("dependencies." + key + " は配列である必要があります。");
        }
        List<String> results = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String value && !value.isBlank()) {
                results.add(value.trim());
            }
        }
        return results;
    }

    private record FrontMatter(String yamlSection, String bodySection) {
    }

    private record DependencySection(List<String> commands, List<String> warnings) {
        static DependencySection empty() {
            return new DependencySection(List.of(), List.of());
        }

        boolean isEmpty() {
            return commands.isEmpty();
        }
    }
}
