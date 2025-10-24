package io.github.hide212131.langchain4j.claude.skills.provider;

public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
