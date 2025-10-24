package io.github.hide212131.langchain4j.claude.skills.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderAdapterTest {

    private final FakeLlmClient fakeClient = new FakeLlmClient();
    private ProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProviderAdapter(fakeClient);
    }

    @Test
    void roundTripMessagesThroughClient() {
        ChatInteraction interaction = new ChatInteraction(
                List.of(ChatMessage.system("role"), ChatMessage.user("hello")), List.of());

        fakeClient.enqueueResponse(
                new LlmResponse(List.of(ChatMessage.assistant("hi there")), List.of(), 10, 15));

        LlmResult result = adapter.complete(interaction);

        assertThat(fakeClient.lastRequest().messages()).containsExactlyElementsOf(interaction.messages());
        assertThat(result.messages()).containsExactly(ChatMessage.assistant("hi there"));
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.tokensIn()).isEqualTo(10);
        assertThat(result.tokensOut()).isEqualTo(15);
    }

    @Test
    void bridgesToolDefinitionsIntoToolSpecifications() {
        ToolDefinition invokeSkill =
                new ToolDefinition("invokeSkill", "Call skill runtime", Map.of("type", "object"));
        ChatInteraction interaction = new ChatInteraction(
                List.of(ChatMessage.system("role"), ChatMessage.user("execute skill")), List.of(invokeSkill));

        fakeClient.enqueueResponse(
                new LlmResponse(
                        List.of(ChatMessage.assistant("Calling tool")),
                        List.of(new ToolCall("call-1", "invokeSkill", "{\"skillId\":\"brand\"}")),
                        20,
                        12));

        LlmResult result = adapter.complete(interaction);

        assertThat(fakeClient.lastRequest().tools())
                .containsExactly(new ToolSpecification("invokeSkill", "Call skill runtime", Map.of("type", "object")));
        assertThat(result.toolCalls())
                .containsExactly(new ToolCall("call-1", "invokeSkill", "{\"skillId\":\"brand\"}"));
        assertThat(result.tokensIn()).isEqualTo(20);
        assertThat(result.tokensOut()).isEqualTo(12);
    }

    private static final class FakeLlmClient implements LlmClient {

        private final Deque<LlmResponse> responses = new ArrayDeque<>();
        private LlmRequest lastRequest;

        void enqueueResponse(LlmResponse response) {
            responses.add(response);
        }

        LlmRequest lastRequest() {
            return lastRequest;
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            lastRequest = request;
            if (responses.isEmpty()) {
                throw new IllegalStateException("No fake response configured");
            }
            return responses.removeFirst();
        }
    }
}
