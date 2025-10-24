package io.github.hide212131.langchain4j.claude.skills.context;

import java.util.Objects;

public record CachedExcerpt(String docRef, String fullContent, String summary) {

    public CachedExcerpt {
        Objects.requireNonNull(docRef, "docRef");
        Objects.requireNonNull(fullContent, "fullContent");
        Objects.requireNonNull(summary, "summary");
    }
}
