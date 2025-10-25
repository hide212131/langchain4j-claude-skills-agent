package io.github.hide212131.langchain4j.claude.skills.runtime.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads SKILL.md metadata from the skills repository.
 * <p>
 * According to the official Claude Skills specification
 * (https://support.claude.com/en/articles/12512198-how-to-create-custom-skills),
 * only 'name' and 'description' are required fields in SKILL.md frontmatter.
 * All other fields (version, inputs, outputs, keywords, stages) are optional.
 */
public final class SkillIndexLoader {

    // Keys recognized by this loader (does not imply they are required)
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "id",
            "name",
            "description",
            "version",
            "inputs",
            "outputs",
            "keywords",
            "stages");

    private final Yaml yaml = new Yaml();

    public LoadResult load(Path skillsDirectory) {
        Objects.requireNonNull(skillsDirectory, "skillsDirectory");
        if (!Files.exists(skillsDirectory)) {
            return new LoadResult(new SkillIndex(), List.of());
        }
        Map<String, SkillIndex.SkillMetadata> metadataMap = new HashMap<>();
        List<String> warnings = new ArrayList<>();

        try {
            Files.walk(skillsDirectory)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .forEach(path -> parseSkillFile(skillsDirectory, metadataMap, path, warnings));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan skills directory: " + skillsDirectory, e);
        }

        return new LoadResult(new SkillIndex(metadataMap), List.copyOf(warnings));
    }

    private void parseSkillFile(
            Path skillsDirectory,
            Map<String, SkillIndex.SkillMetadata> metadataMap,
            Path skillFile,
            List<String> warnings) {
        try {
            String content = Files.readString(skillFile);
            SkillFrontMatter frontMatter = SkillFrontMatter.parse(content, yaml);
            String id = frontMatter.id().orElseGet(() -> deriveSkillId(skillsDirectory, skillFile));
            List<String> unknownKeys = frontMatter.unknownKeys();
            if (!unknownKeys.isEmpty()) {
                warnings.add("Unknown keys in " + skillFile + ": " + String.join(", ", unknownKeys));
            }
            metadataMap.put(
                    id,
                    new SkillIndex.SkillMetadata(
                            id,
                            frontMatter.name().orElse(id),
                            frontMatter.description().orElse(""),
                            List.copyOf(frontMatter.keywords()),
                            List.copyOf(unknownKeys)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skill file: " + skillFile, e);
        }
    }

    private String deriveSkillId(Path root, Path skillFile) {
        return root.relativize(skillFile.getParent()).toString().replace('\\', '/');
    }

    private record SkillFrontMatter(
            Map<String, Object> raw, List<String> keywords, List<String> unknownKeys, String rawContent) {

        static SkillFrontMatter parse(String content, Yaml yaml) {
            int start = content.indexOf("---");
            int end = content.indexOf("---", start + 3);
            if (start != 0 || end <= start) {
                return new SkillFrontMatter(Map.of(), List.of(), List.of(), content);
            }
            String yamlSection = content.substring(start + 3, end);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yaml.load(yamlSection);
            if (map == null) {
                map = Map.of();
            }
            List<String> keywords = extractKeywords(map);
            List<String> unknownKeys = map.keySet().stream()
                    .filter(key -> !ALLOWED_KEYS.contains(key))
                    .map(Object::toString)
                    .collect(Collectors.toCollection(ArrayList::new));
            return new SkillFrontMatter(map, keywords, unknownKeys, content.substring(end + 3));
        }

        private static List<String> extractKeywords(Map<String, Object> map) {
            Object value = map.getOrDefault("keywords", List.of());
            if (value instanceof List<?> list) {
                return list.stream().map(Object::toString).collect(Collectors.toCollection(ArrayList::new));
            }
            return List.of();
        }

        java.util.Optional<String> id() {
            return optionalString("id");
        }

        java.util.Optional<String> name() {
            return optionalString("name");
        }

        java.util.Optional<String> description() {
            return optionalString("description");
        }

        public List<String> unknownKeys() {
            return List.copyOf(unknownKeys);
        }

        private java.util.Optional<String> optionalString(String key) {
            Object value = raw.get(key);
            if (value == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(value.toString());
        }
    }

    public record LoadResult(SkillIndex index, List<String> warnings) {}
}
