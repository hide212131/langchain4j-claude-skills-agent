package io.github.hide212131.langchain4j.claude.skills.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/** SKILL.md の frontmatter（YAML）と本文を読み取り、必須項目のみを POJO 化するパーサ。 */
public final class SkillDocumentParser {

  private static final Set<String> REQUIRED_KEYS = Set.of("name", "description");
  private static final Set<String> ALLOWED_KEYS =
      Set.of(
          "id",
          "name",
          "description",
          "version",
          "inputs",
          "outputs",
          "keywords",
          "stages",
          "license");
  private static final Pattern LEADING_NEWLINES = Pattern.compile("^(\\r?\\n)+");

  private final Yaml yaml = new Yaml();

  public SkillDocument parse(Path skillMdPath) {
    return parse(skillMdPath, null, null);
  }

  public SkillDocument parse(Path skillMdPath, String fallbackId) {
    return parse(skillMdPath, null, fallbackId);
  }

  SkillDocument parse(Path skillMdPath, String rawContent, String fallbackId) {
    Objects.requireNonNull(skillMdPath, "skillMdPath");
    String content = rawContent;
    if (content == null) {
      content = readContent(skillMdPath);
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("SKILL.md の内容が空です: " + skillMdPath);
    }

    FrontMatter frontMatter = extractFrontMatter(skillMdPath, content);
    Map<String, Object> yamlMap = loadYaml(skillMdPath, frontMatter.yamlSection());

    validateKeys(skillMdPath, yamlMap);

    String name = requireString(skillMdPath, yamlMap, "name");
    String description = requireString(skillMdPath, yamlMap, "description");
    String id = resolveId(skillMdPath, yamlMap, fallbackId);

    String body = extractBody(frontMatter);
    return new SkillDocument(id, name, description, body);
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
      throw new IllegalArgumentException(
          "SKILL.md の先頭に '---' で始まる frontmatter が必要です: " + skillMdPath);
    }
    int end = content.indexOf("---", start + 3);
    if (end <= start) {
      throw new IllegalArgumentException("SKILL.md の frontmatter 終端（---）が見つかりません: " + skillMdPath);
    }
    String yamlSection = content.substring(start + 3, end);
    String bodySection = content.substring(end + 3);
    return new FrontMatter(yamlSection, bodySection);
  }

  private Map<String, Object> loadYaml(Path skillMdPath, String yamlSection) {
    try {
      Map<String, Object> map = yaml.load(yamlSection);
      return map == null ? Map.of() : map;
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(
          "SKILL.md の frontmatter を YAML として解釈できません: " + skillMdPath, ex);
    }
  }

  private void validateKeys(Path skillMdPath, Map<String, Object> yamlMap) {
    for (String key : yamlMap.keySet()) {
      if (!ALLOWED_KEYS.contains(key)) {
        throw new IllegalArgumentException(
            "SKILL.md に未対応のキーがあります: " + key + " (" + skillMdPath + ")");
      }
    }
    for (String required : REQUIRED_KEYS) {
      if (!yamlMap.containsKey(required)) {
        throw new IllegalArgumentException(
            "SKILL.md の必須項目 " + required + " が見つかりません: " + skillMdPath);
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
    return skillMdPath.getFileName().toString();
  }

  private String extractBody(FrontMatter frontMatter) {
    String body = frontMatter.bodySection();
    body = LEADING_NEWLINES.matcher(body).replaceFirst("");
    return body;
  }

  private record FrontMatter(String yamlSection, String bodySection) {}
}
