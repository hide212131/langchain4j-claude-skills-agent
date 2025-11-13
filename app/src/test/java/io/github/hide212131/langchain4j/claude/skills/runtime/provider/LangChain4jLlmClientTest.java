package io.github.hide212131.langchain4j.claude.skills.runtime.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LangChain4jLlmClientTest {

    @Test
    void forOpenAiShouldFailWhenApiKeyMissing() {
        LangChain4jLlmClient.EnvironmentVariables emptyEnv = key -> null;

        assertThatThrownBy(() -> LangChain4jLlmClient.forOpenAi(emptyEnv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void completeShouldDelegateToChatModelAndReturnTokenUsage() {
        RecordingChatModel chatModel = new RecordingChatModel();
        CapturingFactory factory = new CapturingFactory(chatModel);
        Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

        LangChain4jLlmClient client =
                LangChain4jLlmClient.forOpenAi(key -> key.equals("OPENAI_API_KEY") ? "test-key" : null, factory, fixedClock);
        LangChain4jLlmClient.CompletionResult result = client.complete("demo prompt");

        assertThat(chatModel.lastPrompt).isEqualTo("demo prompt");
        assertThat(result.content()).isEqualTo("assistant response");
        assertThat(result.tokenUsage().totalTokenCount()).isEqualTo(3);
        assertThat(result.durationMs()).isZero();

        LangChain4jLlmClient.ProviderMetrics metrics = client.metrics();
        assertThat(metrics.callCount()).isEqualTo(1);
        assertThat(metrics.totalInputTokens()).isEqualTo(1);
        assertThat(metrics.totalOutputTokens()).isEqualTo(2);

        assertThat(factory.lastConfig.timeout).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void forOpenAiShouldApplyCustomTimeout() {
        RecordingChatModel chatModel = new RecordingChatModel();
        CapturingFactory factory = new CapturingFactory(chatModel);
        Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

        LangChain4jLlmClient.forOpenAi(key -> {
            if (key.equals("OPENAI_API_KEY")) {
                return "test-key";
            }
            if (key.equals("OPENAI_TIMEOUT_SECONDS")) {
                return "240";
            }
            return null;
        }, factory, fixedClock);

        assertThat(factory.lastConfig.timeout).isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void forOpenAiShouldFailWhenTimeoutInvalid() {
        LangChain4jLlmClient.EnvironmentVariables env = key -> {
            if (key.equals("OPENAI_API_KEY")) {
                return "test-key";
            }
            if (key.equals("OPENAI_TIMEOUT_SECONDS")) {
                return "-10";
            }
            return null;
        };

        assertThatThrownBy(() -> LangChain4jLlmClient.forOpenAi(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_TIMEOUT_SECONDS");
    }

    @Test
    void forOpenAiShouldCreateHighPerformanceChatModelWhenSpecified() {
        RecordingChatModel defaultModel = new RecordingChatModel();
        RecordingChatModel highPerformanceModel = new RecordingChatModel();
        SequencedFactory factory = new SequencedFactory(List.of(defaultModel, highPerformanceModel));

        LangChain4jLlmClient client = LangChain4jLlmClient.forOpenAi(key -> switch (key) {
            case "OPENAI_API_KEY" -> "test-key";
            case "OPENAI_HIGH_PERFORMANCE_MODEL_NAME" -> "gpt-5.1";
            default -> null;
        }, factory, Clock.systemUTC());

        assertThat(factory.configs)
                .extracting(config -> config.modelName)
                .containsExactly("gpt-5-mini", "gpt-5.1");
        assertThat(client.chatModel()).isSameAs(defaultModel);
        assertThat(client.highPerformanceChatModel()).isSameAs(highPerformanceModel);
        assertThat(client.highPerformanceModelName()).isEqualTo("gpt-5.1");
    }

    private static final class RecordingChatModel implements ChatModel {
        String lastPrompt;

        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            if (!messages.isEmpty() && messages.get(0) instanceof UserMessage userMessage) {
                lastPrompt = userMessage.singleText();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("assistant response"))
                    .tokenUsage(new TokenUsage(1, 2, 3))
                    .build();
        }
    }

    private static final class CapturingFactory implements LangChain4jLlmClient.ChatModelFactory {
        final ChatModel delegate;
        LangChain4jLlmClient.OpenAiConfig lastConfig;
        final List<LangChain4jLlmClient.OpenAiConfig> configs = new ArrayList<>();

        CapturingFactory(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatModel create(LangChain4jLlmClient.OpenAiConfig config) {
            this.lastConfig = config;
            this.configs.add(config);
            return delegate;
        }
    }

    private static final class SequencedFactory implements LangChain4jLlmClient.ChatModelFactory {
        private final List<ChatModel> delegates;
        private int index;
        final List<LangChain4jLlmClient.OpenAiConfig> configs = new ArrayList<>();

        SequencedFactory(List<ChatModel> delegates) {
            this.delegates = delegates;
        }

        @Override
        public ChatModel create(LangChain4jLlmClient.OpenAiConfig config) {
            configs.add(config);
            if (index >= delegates.size()) {
                throw new IllegalStateException("No delegate available for config " + config.modelName);
            }
            return delegates.get(index++);
        }
    }
}
