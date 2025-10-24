package io.github.hide212131.langchain4j.claude.skills.context;

@FunctionalInterface
public interface TokenCounter {
    int countTokens(String text);
}
