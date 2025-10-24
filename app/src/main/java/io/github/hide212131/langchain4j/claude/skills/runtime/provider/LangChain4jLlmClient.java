package io.github.hide212131.langchain4j.claude.skills.runtime.provider;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LangChain4j wrapper that exposes a small surface tailored for the agent runtime.
 */
public final class LangChain4jLlmClient {

    private final ChatModel chatModel;
    private final Clock clock;
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicLong cumulativeDurationMs = new AtomicLong();
    private final AtomicInteger cumulativeInputTokens = new AtomicInteger();
    private final AtomicInteger cumulativeOutputTokens = new AtomicInteger();

    private LangChain4jLlmClient(ChatModel chatModel, Clock clock) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static LangChain4jLlmClient forOpenAi(EnvironmentVariables environment) {
        return forOpenAi(environment, new OpenAiChatModelFactory(), Clock.systemUTC());
    }

    static LangChain4jLlmClient forOpenAi(
            EnvironmentVariables environment, ChatModelFactory factory, Clock clock) {
        String apiKey = environment.get("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY must be set");
        }
        OpenAiConfig config = new OpenAiConfig(apiKey, "gpt-4o-mini");
        ChatModel chatModel = factory.create(config);
        return new LangChain4jLlmClient(chatModel, clock);
    }

    public static LangChain4jLlmClient usingChatModel(ChatModel chatModel) {
        return new LangChain4jLlmClient(chatModel, Clock.systemUTC());
    }

    public static LangChain4jLlmClient fake() {
        return new LangChain4jLlmClient(new FakeChatModel(), Clock.systemUTC());
    }

    public CompletionResult complete(String prompt) {
        Instant start = clock.instant();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from(prompt)))
                .build();
        ChatResponse response = chatModel.doChat(request);
        long durationMs = Duration.between(start, clock.instant()).toMillis();
        AiMessage aiMessage = response.aiMessage();
        String content = aiMessage != null ? aiMessage.text() : "";
        TokenUsage usage = response.tokenUsage();
        recordMetrics(usage, durationMs);
        return new CompletionResult(content, usage, durationMs);
    }

    public ChatModel chatModel() {
        return chatModel;
    }

    public ProviderMetrics metrics() {
        return new ProviderMetrics(
                callCount.get(),
                cumulativeDurationMs.get(),
                cumulativeInputTokens.get(),
                cumulativeOutputTokens.get());
    }

    public record CompletionResult(String content, TokenUsage tokenUsage, long durationMs) {}

    public record ProviderMetrics(int callCount, long totalDurationMs, int totalInputTokens, int totalOutputTokens) {
        public int totalTokenCount() {
            return totalInputTokens + totalOutputTokens;
        }
    }

    @FunctionalInterface
    public interface EnvironmentVariables {
        String get(String name);
    }

    interface ChatModelFactory {
        ChatModel create(OpenAiConfig config);
    }

    static final class OpenAiConfig {
        final String apiKey;
        final String modelName;

        OpenAiConfig(String apiKey, String modelName) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            this.modelName = Objects.requireNonNull(modelName, "modelName");
        }
    }

    private static final class OpenAiChatModelFactory implements ChatModelFactory {
        @Override
        public ChatModel create(OpenAiConfig config) {
            return OpenAiChatModel.builder()
                    .apiKey(config.apiKey)
                    .modelName(config.modelName)
                    .build();
        }
    }

    private static final class FakeChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("dry-run-plan"))
                    .tokenUsage(new TokenUsage(0, 0, 0))
                    .build();
        }
    }

    private void recordMetrics(TokenUsage usage, long durationMs) {
        callCount.incrementAndGet();
        cumulativeDurationMs.addAndGet(durationMs);
        if (usage != null) {
            if (usage.inputTokenCount() != null) {
                cumulativeInputTokens.addAndGet(usage.inputTokenCount());
            }
            if (usage.outputTokenCount() != null) {
                cumulativeOutputTokens.addAndGet(usage.outputTokenCount());
            }
        }
    }
}
