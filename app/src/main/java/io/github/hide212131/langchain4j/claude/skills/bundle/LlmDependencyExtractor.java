package io.github.hide212131.langchain4j.claude.skills.bundle;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import io.github.hide212131.langchain4j.claude.skills.runtime.LlmConfiguration;
import io.github.hide212131.langchain4j.claude.skills.runtime.LlmConfigurationLoader;
import io.github.hide212131.langchain4j.claude.skills.runtime.LlmProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@SuppressWarnings({ "PMD.AvoidCatchingGenericException", "PMD.GodClass", "PMD.TooManyMethods" })
public final class LlmDependencyExtractor {

    private static final Logger LOGGER = Logger.getLogger(LlmDependencyExtractor.class.getName());
    private static final int MAX_SNIPPET_CHARS = 4000;
    private static final double DEFAULT_TEMPERATURE = 0.0;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final LlmConfigurationLoader configurationLoader;
    private final Yaml yaml;

    public LlmDependencyExtractor() {
        this(new LlmConfigurationLoader(), new Yaml(new SafeConstructor(new LoaderOptions())));
    }

    LlmDependencyExtractor(LlmConfigurationLoader configurationLoader, Yaml yaml) {
        this.configurationLoader = Objects.requireNonNull(configurationLoader, "configurationLoader");
        this.yaml = Objects.requireNonNull(yaml, "yaml");
    }

    public Optional<DependencyExtractionResult> extract(String skillId, String body, String sourcePath) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        Optional<LlmConfiguration> configuration = loadConfiguration();
        if (configuration.isEmpty()) {
            return Optional.empty();
        }
        String prompt = buildPrompt(skillId, sourcePath, body);
        ChatModel model = buildChatModel(configuration.get());
        String response;
        try {
            response = model.chat(prompt);
        } catch (RuntimeException ex) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "LLM 依存抽出が失敗しました: " + ex.getMessage());
            }
            return Optional.empty();
        }
        return parseResponse(response);
    }

    public Optional<ConvertedCommand> convertInstruction(String skillId, String instruction, String sourcePath) {
        if (instruction == null || instruction.isBlank()) {
            return Optional.empty();
        }
        Optional<LlmConfiguration> configuration = loadConfiguration();
        if (configuration.isEmpty()) {
            return Optional.empty();
        }
        String prompt = buildInstructionPrompt(skillId, sourcePath, instruction);
        ChatModel model = buildChatModel(configuration.get());
        String response;
        try {
            response = model.chat(prompt);
        } catch (RuntimeException ex) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "LLM 指示変換が失敗しました: " + ex.getMessage());
            }
            return Optional.empty();
        }
        return parseCommandResponse(response);
    }

    private ChatModel buildChatModel(LlmConfiguration configuration) {
        OpenAiOfficialChatModel.Builder builder = OpenAiOfficialChatModel.builder().apiKey(configuration.openAiApiKey())
                .temperature(DEFAULT_TEMPERATURE).timeout(DEFAULT_TIMEOUT);
        if (configuration.openAiBaseUrl() != null) {
            builder.baseUrl(configuration.openAiBaseUrl());
        }
        if (configuration.openAiModel() != null) {
            builder.modelName(configuration.openAiModel());
        }
        return builder.build();
    }

    private Optional<DependencyExtractionResult> parseResponse(String response) {
        Optional<Map<String, Object>> map = parseYamlMap(response);
        if (map.isEmpty()) {
            return Optional.empty();
        }
        List<String> warnings = readStringList(map.get(), "warnings");
        List<String> commands = normalizeCommands(readStringList(map.get(), "commands"), warnings);
        DependencyExtractionResult result = new DependencyExtractionResult(commands, warnings);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    private List<String> normalizeCommands(List<String> commands, List<String> warnings) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String command : commands) {
            if (command == null) {
                continue;
            }
            String trimmed = command.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (isRuntimeCommand(trimmed)) {
                warnings.add("実行コマンドは環境構築でやることはここまでなので除外しました: \"" + trimmed + "\"");
                continue;
            }
            if (isNpmInstallWithoutPackages(trimmed)) {
                warnings.add("npm install にパッケージ指定がないため除外しました: \"" + trimmed + "\"");
                continue;
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private boolean isNpmInstallWithoutPackages(String command) {
        String trimmed = command.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("sudo ")) {
            trimmed = trimmed.substring(5).trim();
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("npm install")) {
            return false;
        }
        String[] tokens = lower.split("\\s+");
        if (tokens.length <= 2) {
            return true;
        }
        for (int i = 2; i < tokens.length; i++) {
            if (!tokens[i].startsWith("-")) {
                return false;
            }
        }
        return true;
    }

    private boolean isRuntimeCommand(String command) {
        String trimmed = command.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("sudo ")) {
            trimmed = trimmed.substring(5).trim();
        }
        for (String segment : trimmed.split("\\s*(?:&&|\\|\\|);?\\s*")) {
            String lower = segment.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("npm run ") || lower.startsWith("node ") || lower.startsWith("python ")
                    || lower.startsWith("python3 ")) {
                return true;
            }
        }
        return false;
    }

    private Optional<ConvertedCommand> parseCommandResponse(String response) {
        Optional<Map<String, Object>> map = parseYamlMap(response);
        if (map.isEmpty()) {
            return Optional.empty();
        }
        String command = resolveCommand(map.get());
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        String warning = resolveWarning(map.get());
        return Optional.of(new ConvertedCommand(command.trim(), warning == null ? null : warning.trim()));
    }

    private String extractYaml(String response) {
        String trimmed = response.trim();
        int fenceStart = trimmed.indexOf("```");
        if (fenceStart >= 0) {
            int fenceEnd = trimmed.indexOf("```", fenceStart + 3);
            if (fenceEnd > fenceStart) {
                return trimmed.substring(fenceStart + 3, fenceEnd).trim();
            }
        }
        return trimmed;
    }

    private Optional<Map<String, Object>> parseYamlMap(String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        String yamlText = extractYaml(response);
        if (yamlText == null || yamlText.isBlank()) {
            return Optional.empty();
        }
        Object loaded;
        try {
            loaded = yaml.load(yamlText);
        } catch (RuntimeException ex) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "LLM 応答の YAML 解析に失敗しました: " + ex.getMessage());
            }
            return Optional.empty();
        }
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        return Optional.of(map);
    }

    private String resolveCommand(Map<String, Object> map) {
        String command = readString(map, "command");
        if (command != null && !command.isBlank()) {
            return command;
        }
        List<String> commands = readStringList(map, "commands");
        return commands.isEmpty() ? null : commands.get(0);
    }

    private String resolveWarning(Map<String, Object> map) {
        String warning = readString(map, "warning");
        if (warning != null && !warning.isBlank()) {
            return warning;
        }
        List<String> warnings = readStringList(map, "warnings");
        return warnings.isEmpty() ? null : warnings.get(0);
    }

    private String readString(Map<String, Object> map, String key) {
        Object raw = map.get(key);
        if (!(raw instanceof String value)) {
            return null;
        }
        String text = value.trim();
        return text.isBlank() ? null : text;
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

    private String buildPrompt(String skillId, String sourcePath, String body) {
        String snippet = selectRelevantText(body);
        if (snippet.length() > MAX_SNIPPET_CHARS) {
            snippet = snippet.substring(0, MAX_SNIPPET_CHARS);
        }
        return """
                次の SKILL.md 本文から依存インストールコマンドを抽出してください。必ず YAML だけを返してください。
                余計な説明やマークダウン、コードフェンスは禁止です。
                出力キー: commands, warnings
                実行コマンド（npm run / python / node など）は環境構築でやることはここまでなので commands に含めないでください。
                例示・ダミーのスクリプト名（例: your_automation.py など）から依存を推測しないでください。
                package.json や requirements.txt は本文で明記されている場合のみ commands に含めてください。
                上記ファイルの存在を前提にする場合は warnings に理由を記載してください。
                warnings は日本語で書いてください。

                skill_id: %s
                source_path: %s

                --- skill body (excerpt) ---
                %s
                """.formatted(safeValue(skillId), safeValue(sourcePath), snippet);
    }

    private String buildInstructionPrompt(String skillId, String sourcePath, String instruction) {
        return """
                次の依存指示を実行可能なインストールコマンドに変換してください。必ず YAML だけを返してください。
                余計な説明やマークダウン、コードフェンスは禁止です。
                出力キー: command, warning
                warning は日本語で書いてください。
                command は 1 行のシェルコマンドにし、可能なら apt-get install / npm install -g / pip install を優先してください。
                不確実な場合は warning に理由を残し、command も提案してください。

                skill_id: %s
                source_path: %s
                instruction: %s
                """.formatted(safeValue(skillId), safeValue(sourcePath), instruction.trim());
    }

    private String selectRelevantText(String body) {
        String[] lines = body.split("\\R");
        List<String> selected = new ArrayList<>();
        for (String line : lines) {
            if (looksRelevant(line)) {
                selected.add(line);
            }
        }
        if (selected.isEmpty()) {
            return body;
        }
        return String.join(System.lineSeparator(), selected);
    }

    private boolean looksRelevant(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.contains("install") || normalized.contains("setup") || normalized.contains("requirement")
                || normalized.contains("dependency") || normalized.contains("dependencies")
                || normalized.contains("pip") || normalized.contains("npm") || normalized.contains("apt")
                || normalized.contains("brew") || normalized.contains("conda") || normalized.contains("poetry")
                || normalized.contains("pipenv") || normalized.contains("yarn") || normalized.contains("pnpm")
                || normalized.contains("tool");
    }

    private String safeValue(String value) {
        return value == null ? "-" : value;
    }

    private Optional<LlmConfiguration> loadConfiguration() {
        LlmConfiguration configuration;
        try {
            configuration = configurationLoader.load();
        } catch (RuntimeException ex) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "LLM 設定の読み込みに失敗したためスキップします: " + ex.getMessage());
            }
            return Optional.empty();
        }
        if (configuration.provider() != LlmProvider.OPENAI) {
            return Optional.empty();
        }
        return Optional.of(configuration);
    }

    public record DependencyExtractionResult(List<String> commands, List<String> warnings) {

        public DependencyExtractionResult(List<String> commands, List<String> warnings) {
            this.commands = List.copyOf(commands == null ? List.of() : commands);
            this.warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        boolean isEmpty() {
            return commands.isEmpty() && warnings.isEmpty();
        }
    }

    public record ConvertedCommand(String command, String warning) {
    }
}
