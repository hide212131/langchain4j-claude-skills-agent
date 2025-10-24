package io.github.hide212131.langchain4j.claude.skills.runtime.provider;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Abstraction over underlying LLM providers to keep runtime components decoupled from the
 * implementation specifics (OpenAI, Claude, etc).
 */
public interface ProviderAdapter {

    ChatModel chatModel();

    default String generate(String prompt) {
        return chatModel().chat(prompt);
    }
}
