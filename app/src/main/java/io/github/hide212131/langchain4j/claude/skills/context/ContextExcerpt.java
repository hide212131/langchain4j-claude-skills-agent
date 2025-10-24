package io.github.hide212131.langchain4j.claude.skills.context;

import java.util.Objects;

public record ContextExcerpt(String docRef, String content) {

    public ContextExcerpt {
        Objects.requireNonNull(docRef, "docRef");
        Objects.requireNonNull(content, "content");
    }
}
