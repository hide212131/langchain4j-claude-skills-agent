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
        LangChain4jLlmClient.ChatModelFactory factory = config -> chatModel;
        Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

        LangChain4jLlmClient client =
                LangChain4jLlmClient.forOpenAi(key -> "test-key", factory, fixedClock);
        LangChain4jLlmClient.CompletionResult result = client.complete("demo prompt");

        assertThat(chatModel.lastPrompt).isEqualTo("demo prompt");
        assertThat(result.content()).isEqualTo("assistant response");
        assertThat(result.tokenUsage().totalTokenCount()).isEqualTo(3);
        assertThat(result.durationMs()).isZero();

        LangChain4jLlmClient.ProviderMetrics metrics = client.metrics();
        assertThat(metrics.callCount()).isEqualTo(1);
        assertThat(metrics.totalInputTokens()).isEqualTo(1);
        assertThat(metrics.totalOutputTokens()).isEqualTo(2);
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
}
