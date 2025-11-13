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

        CapturingFactory(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatModel create(LangChain4jLlmClient.OpenAiConfig config) {
            this.lastConfig = config;
            return delegate;
        }
    }
}
