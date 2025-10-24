package io.github.hide212131.langchain4j.claude.skills.context;

@FunctionalInterface
public interface ExcerptSummarizer {
    String summarize(String docRef, String content);
}
