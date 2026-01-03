package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ErrorPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.ParsePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillMasking;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/** SKILL.md の frontmatter（YAML）と本文を読み取り、必須項目のみを POJO 化するパーサ。 */
@SuppressWarnings({ "PMD.GodClass", "PMD.TooManyMethods" })
public final class SkillDocumentParser {

    private static final Set<String> REQUIRED_KEYS = Set.of("name", "description");
    private static final Set<String> ALLOWED_KEYS = Set.of("id", "name", "description", "version", "inputs", "outputs",
            "keywords", "stages", "license", "dependencies");
    private static final Pattern LEADING_NEWLINES = Pattern.compile("^(\\r?\\n)+");

    private final Yaml yaml = new Yaml();
    private final SkillEventPublisher events;
    private final SkillMasking masking;

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SkillDocumentParser() {
        this(SkillEventPublisher.noop(), SkillMasking.defaultRules());
    }

    public SkillDocumentParser(SkillEventPublisher events, SkillMasking masking) {
        this.events = Objects.requireNonNull(events, "events");
        this.masking = Objects.requireNonNull(masking, "masking");
    }

    public SkillDocument parse(Path skillMdPath) {
        return parse(skillMdPath, null, null, "-");
    }

    public SkillDocument parse(Path skillMdPath, String fallbackId) {
        return parse(skillMdPath, null, fallbackId, "-");
    }

    public SkillDocument parse(Path skillMdPath, String fallbackId, String runId) {
        return parse(skillMdPath, null, fallbackId, runId);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    SkillDocument parse(Path skillMdPath, String rawContent, String fallbackId, String runId) {
        Objects.requireNonNull(skillMdPath, "skillMdPath");
        String normalizedRunId = normalizeRunId(runId);
        String content = rawContent;
        if (content == null) {
            content = readContent(skillMdPath);
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("SKILL.md の内容が空です: " + skillMdPath);
        }

        publishParseEvent("parse.start", normalizedRunId, fallbackId, skillMdPath.toString(), Map.of(), "");

        String skillIdForEvent = fallbackId;
        try {
            FrontMatter frontMatter = extractFrontMatter(skillMdPath, content);
            Map<String, Object> yamlMap = loadYaml(skillMdPath, frontMatter.yamlSection());

            validateKeys(skillMdPath, yamlMap);

            String name = requireString(skillMdPath, yamlMap, "name");
            String description = requireString(skillMdPath, yamlMap, "description");
            String id = resolveId(skillMdPath, yamlMap, fallbackId);
            skillIdForEvent = id;

            String body = extractBody(frontMatter);
            publishParseEvent("parse.frontmatter", normalizedRunId, id, skillMdPath.toString(),
                    masking.maskFrontMatter(yamlMap), "");
            publishParseEvent("parse.body", normalizedRunId, id, skillMdPath.toString(), Map.of(),
                    masking.maskPreview(body));
            return new SkillDocument(id, name, description, body);
        } catch (RuntimeException ex) {
            publishErrorEvent(normalizedRunId, skillIdForEvent, skillMdPath.toString(), ex);
            throw ex;
        }
    }

    private String normalizeRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return "-";
        }
        return runId.trim();
    }

    private void publishParseEvent(String step, String runId, String skillId, String path,
            Map<String, Object> frontMatter, String preview) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "parse", step, null);
        ParsePayload payload = new ParsePayload(path, frontMatter, preview, true);
        events.publish(new SkillEvent(SkillEventType.PARSE, metadata, payload));
    }

    private void publishErrorEvent(String runId, String skillId, String path, RuntimeException ex) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, "parse", "parse.error", null);
        ErrorPayload payload = new ErrorPayload(path + " のパースに失敗しました: " + ex.getMessage(),
                ex.getClass().getSimpleName());
        events.publish(new SkillEvent(SkillEventType.ERROR, metadata, payload));
    }

    private String readContent(Path skillMdPath) {
        try {
            return Files.readString(skillMdPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("SKILL.md を読み取れませんでした: " + skillMdPath, e);
        }
    }

    private FrontMatter extractFrontMatter(Path skillMdPath, String content) {
        int start = content.indexOf("---");
        if (start != 0) {
            throw new IllegalArgumentException("SKILL.md の先頭に '---' で始まる frontmatter が必要です: " + skillMdPath);
        }
        int end = content.indexOf("---", start + 3);
        if (end <= start) {
            throw new IllegalArgumentException("SKILL.md の frontmatter 終端（---）が見つかりません: " + skillMdPath);
        }
        String yamlSection = content.substring(start + 3, end);
        String bodySection = content.substring(end + 3);
        return new FrontMatter(yamlSection, bodySection);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Map<String, Object> loadYaml(Path skillMdPath, String yamlSection) {
        try {
            Map<String, Object> map = yaml.load(yamlSection);
            return map == null ? Map.of() : map;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("SKILL.md の frontmatter を YAML として解釈できません: " + skillMdPath, ex);
        }
    }

    private void validateKeys(Path skillMdPath, Map<String, Object> yamlMap) {
        for (String key : yamlMap.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("SKILL.md に未対応のキーがあります: " + key + " (" + skillMdPath + ")");
            }
        }
        for (String required : REQUIRED_KEYS) {
            if (!yamlMap.containsKey(required)) {
                throw new IllegalArgumentException("SKILL.md の必須項目 " + required + " が見つかりません: " + skillMdPath);
            }
        }
    }

    private String requireString(Path skillMdPath, Map<String, Object> yamlMap, String key) {
        Object value = yamlMap.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("SKILL.md の必須項目 " + key + " が空です: " + skillMdPath);
        }
        return value.toString().trim();
    }

    private String resolveId(Path skillMdPath, Map<String, Object> yamlMap, String fallbackId) {
        Object raw = yamlMap.get("id");
        if (raw != null && !raw.toString().isBlank()) {
            return raw.toString().trim();
        }
        if (fallbackId != null && !fallbackId.isBlank()) {
            return fallbackId.trim();
        }
        Path parent = skillMdPath.getParent();
        if (parent != null) {
            Path relative = parent.getFileName();
            if (relative != null) {
                return relative.toString();
            }
        }
        Path fileName = skillMdPath.getFileName();
        if (fileName != null) {
            return fileName.toString();
        }
        throw new IllegalArgumentException("SKILL.md のパスからファイル名を解決できません: " + skillMdPath);
    }

    private String extractBody(FrontMatter frontMatter) {
        String body = frontMatter.bodySection();
        body = LEADING_NEWLINES.matcher(body).replaceFirst("");
        return body;
    }

    private record FrontMatter(String yamlSection, String bodySection) {
    }
}
