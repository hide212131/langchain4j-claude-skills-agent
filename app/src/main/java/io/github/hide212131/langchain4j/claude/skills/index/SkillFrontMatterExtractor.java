package io.github.hide212131.langchain4j.claude.skills.index;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

final class SkillFrontMatterExtractor {

    private static final Set<String> SUPPORTED_FRONT_MATTER_KEYS =
            Set.of("name", "description", "version", "inputs", "outputs", "keywords", "stages");

    SkillFrontMatterExtraction extract(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        String frontMatterBlock = frontMatter(markdown);
        Map<String, Object> data = parseYaml(frontMatterBlock);

        List<String> unsupported = findUnsupportedKeys(data);

        String name = requireString(data.get("name"), "name");
        String description = requireString(data.get("description"), "description");
        Optional<String> version = optionalString(data.get("version"));

        List<SkillIO> inputs = mapSkillIO(listValue(data.get("inputs"), "inputs"));
        List<SkillIO> outputs = mapSkillIO(listValue(data.get("outputs"), "outputs"));
        List<String> keywords = mapKeywords(listValue(data.get("keywords"), "keywords"));
        List<SkillStage> stages = mapStages(listValue(data.get("stages"), "stages"));

        SkillFrontMatter frontMatter =
                new SkillFrontMatter(name, description, version, inputs, outputs, keywords, stages);
        return new SkillFrontMatterExtraction(frontMatter, unsupported);
    }

    private static String frontMatter(String markdown) {
        try (BufferedReader reader = new BufferedReader(new StringReader(markdown))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.strip().equals("---")) {
                throw new IllegalArgumentException("SKILL.md is missing YAML front matter (expected leading ---)");
            }
            StringBuilder block = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.strip().equals("---")) {
                    return block.toString();
                }
                block.append(line).append('\n');
            }
            throw new IllegalArgumentException("SKILL.md front matter is not terminated with ---");
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected I/O while reading SKILL.md content", e);
        }
    }

    private static Map<String, Object> parseYaml(String yamlText) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object loaded = yaml.load(yamlText);
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("SKILL.md front matter must be a mapping");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("SKILL.md front matter keys must be strings");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<String> findUnsupportedKeys(Map<String, Object> data) {
        Set<String> unsupported = new LinkedHashSet<>();
        for (String key : data.keySet()) {
            if (!SUPPORTED_FRONT_MATTER_KEYS.contains(key)) {
                unsupported.add(key);
            }
        }
        return List.copyOf(unsupported);
    }

    private static String requireString(Object value, String field) {
        return optionalString(value)
                .orElseThrow(() -> new IllegalArgumentException("Missing or non-string field: " + field));
    }

    private static Optional<String> optionalString(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String str) {
            return Optional.of(str);
        }
        throw new IllegalArgumentException("Expected a string value but got: " + value.getClass().getSimpleName());
    }

    private static List<?> listValue(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException("Expected a list for field '" + field + "'");
    }

    private static List<SkillIO> mapSkillIO(List<?> rawList) {
        List<SkillIO> result = new ArrayList<>();
        for (Object element : rawList) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Entries in inputs/outputs must be mappings");
            }
            String id = requireString(map.get("id"), "IO.id");
            String type = requireString(map.get("type"), "IO.type");
            String description = optionalString(map.get("description")).orElse("");
            boolean required = booleanValue(map.get("required"), false);
            result.add(new SkillIO(id, type, required, description));
        }
        return List.copyOf(result);
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        throw new IllegalArgumentException("Expected a boolean value but got: " + value.getClass().getSimpleName());
    }

    private static List<String> mapKeywords(List<?> rawList) {
        List<String> result = new ArrayList<>();
        for (Object element : rawList) {
            if (element instanceof String str) {
                result.add(str);
            } else {
                throw new IllegalArgumentException("Keywords must be strings");
            }
        }
        return List.copyOf(result);
    }

    private static List<SkillStage> mapStages(List<?> rawList) {
        List<SkillStage> result = new ArrayList<>();
        for (Object element : rawList) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Stages entries must be mappings");
            }
            String id = requireString(map.get("id"), "stages.id");
            String purpose = requireString(map.get("purpose"), "stages.purpose");
            List<String> resources = mapStringList(listValue(map.get("resources"), "stages.resources"));
            List<String> scripts = mapStringList(listValue(map.get("scripts"), "stages.scripts"));
            result.add(new SkillStage(id, purpose, resources, scripts));
        }
        return List.copyOf(result);
    }

    private static List<String> mapStringList(List<?> rawList) {
        List<String> result = new ArrayList<>();
        for (Object element : rawList) {
            if (element instanceof String str) {
                result.add(str);
            } else {
                throw new IllegalArgumentException("Expected only string values");
            }
        }
        return List.copyOf(result);
    }

    record SkillFrontMatter(
            String name,
            String description,
            Optional<String> version,
            List<SkillIO> inputs,
            List<SkillIO> outputs,
            List<String> keywords,
            List<SkillStage> stages) {}

    record SkillFrontMatterExtraction(SkillFrontMatter frontMatter, List<String> unsupportedKeys) {}
}
