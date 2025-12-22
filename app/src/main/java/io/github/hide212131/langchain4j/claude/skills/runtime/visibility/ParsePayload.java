package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import java.util.Collections;
import java.util.Map;

/** SKILL.md パース結果の可視化ペイロード。 */
public record ParsePayload(String path, Map<String, Object> frontMatter, String bodyPreview, boolean validated)
        implements VisibilityPayload {

    public ParsePayload(String path, Map<String, Object> frontMatter, String bodyPreview, boolean validated) {
        this.path = requirePath(path);
        this.frontMatter = normalizeFrontMatter(frontMatter);
        this.bodyPreview = bodyPreview;
        this.validated = validated;
    }

    private String requirePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path");
        }
        return path;
    }

    private Map<String, Object> normalizeFrontMatter(Map<String, Object> frontMatter) {
        if (frontMatter == null || frontMatter.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(frontMatter);
    }
}
