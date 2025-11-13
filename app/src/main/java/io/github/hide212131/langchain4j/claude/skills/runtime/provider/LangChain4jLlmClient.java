package io.github.hide212131.langchain4j.claude.skills.runtime.provider;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.github.hide212131.langchain4j.claude.skills.runtime.observability.InstrumentedChatModel;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

/**
 * LangChain4j wrapper that exposes a small surface tailored for the agent runtime.
 */
public final class LangChain4jLlmClient {

    private final ChatModel chatModel;
    private final Clock clock;
    private final String defaultModelName;
    private final ChatModel highPerformanceChatModel;
    private final String highPerformanceModelName;
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicLong cumulativeDurationMs = new AtomicLong();
    private final AtomicInteger cumulativeInputTokens = new AtomicInteger();
    private final AtomicInteger cumulativeOutputTokens = new AtomicInteger();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private LangChain4jLlmClient(
            ChatModel chatModel,
            ChatModel highPerformanceChatModel,
            Clock clock,
            String defaultModelName,
            String highPerformanceModelName) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.highPerformanceChatModel =
                highPerformanceChatModel != null ? highPerformanceChatModel : chatModel;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.defaultModelName = defaultModelName;
        this.highPerformanceModelName =
                highPerformanceModelName != null ? highPerformanceModelName : defaultModelName;
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
        String modelName = trimToNull(environment.get("OPENAI_MODEL_NAME"));
        if (modelName == null) {
            modelName = "gpt-5-mini";
        }
        String highPerformanceModelName =
                trimToNull(environment.get("OPENAI_HIGH_PERFORMANCE_MODEL_NAME"));
        Duration timeout = resolveTimeout(environment);
        OpenAiConfig defaultConfig = new OpenAiConfig(apiKey, modelName, timeout);
        ChatModel chatModel = factory.create(defaultConfig);
        ChatModel highPerformanceChatModel = null;
        if (highPerformanceModelName != null && !highPerformanceModelName.equals(modelName)) {
            highPerformanceChatModel =
                    factory.create(new OpenAiConfig(apiKey, highPerformanceModelName, timeout));
        }
        return new LangChain4jLlmClient(
                chatModel,
                highPerformanceChatModel,
                clock,
                defaultConfig.modelName,
                highPerformanceModelName);
    }

    public static LangChain4jLlmClient usingChatModel(ChatModel chatModel) {
        return new LangChain4jLlmClient(chatModel, null, Clock.systemUTC(), null, null);
    }

    public static LangChain4jLlmClient fake() {
        return new LangChain4jLlmClient(new FakeChatModel(), null, Clock.systemUTC(), null, null);
    }

    public CompletionResult complete(String prompt) {
        Instant start = clock.instant();
        ChatModel providerModel = unwrap(chatModel);
        ChatRequestParameters parameters;
        if (providerModel instanceof OpenAiChatModel) {
            var builder = OpenAiChatRequestParameters.builder();
            if (defaultModelName != null && !defaultModelName.isBlank()) {
                builder.modelName(defaultModelName);
            }
            parameters = builder.build();
        } else {
            parameters = ChatRequestParameters.builder().build();
        }
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from(prompt)))
                .parameters(parameters)
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

    public String defaultModelName() {
        return defaultModelName;
    }

    public ChatModel highPerformanceChatModel() {
        return highPerformanceChatModel;
    }

    public String highPerformanceModelName() {
        return highPerformanceModelName;
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
        final Duration timeout;

        OpenAiConfig(String apiKey, String modelName, Duration timeout) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            this.modelName = Objects.requireNonNull(modelName, "modelName");
            this.timeout = Objects.requireNonNull(timeout, "timeout");
        }
    }

    private static final class OpenAiChatModelFactory implements ChatModelFactory {

        OpenAiChatModelFactory() {
        }

        @Override
        public ChatModel create(OpenAiConfig config) {
            return OpenAiChatModel.builder()
                    .apiKey(config.apiKey)
                    .modelName(config.modelName)
                    .timeout(config.timeout)
                    .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                    .strictJsonSchema(true)
                    .build();
        }
    }

    private static final class FakeChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            String combined = request.messages().toString();
            String reply = combined.contains("JSON schema")
                    ? "{\"skill_ids\":[\"brand-guidelines\",\"document-skills/pptx\",\"with-reference\"]}"
                    : "dry-run-plan";
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(reply))
                    .tokenUsage(new TokenUsage(0, 0, 0))
                    .build();
        }
    }

    private static Duration resolveTimeout(EnvironmentVariables environment) {
        String raw = environment.get("OPENAI_TIMEOUT_SECONDS");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TIMEOUT;
        }
        long seconds;
        try {
            seconds = Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "OPENAI_TIMEOUT_SECONDS must be a positive integer (seconds)", ex);
        }
        if (seconds <= 0) {
            throw new IllegalStateException("OPENAI_TIMEOUT_SECONDS must be greater than zero");
        }
        return Duration.ofSeconds(seconds);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private ChatModel unwrap(ChatModel model) {
        if (model instanceof InstrumentedChatModel instrumented) {
            return instrumented.delegate();
        }
        return model;
    }
}
