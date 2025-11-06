package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.trace.Tracer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transitional wrapper that delegates to the underlying {@link ChatModel} while routing
 * observability concerns through {@link ObservabilityChatModelListener}.
 */
public final class InstrumentedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ObservabilityChatModelListener listener;
    private final String defaultModelName;

    public InstrumentedChatModel(ChatModel delegate, Tracer tracer, String system, String defaultModelName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.listener = new ObservabilityChatModelListener(
                Objects.requireNonNull(tracer, "tracer"),
                system == null || system.isBlank() ? delegate.getClass().getSimpleName() : system,
                defaultModelName);
        this.defaultModelName = defaultModelName;
    }

    public ChatModel delegate() {
        return delegate;
    }

    @Override
    public String chat(String prompt) {
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(prompt == null ? List.of() : List.of(UserMessage.from(prompt)));
        if (defaultModelName != null && !defaultModelName.isBlank()) {
            builder.parameters(dev.langchain4j.model.openai.OpenAiChatRequestParameters.builder()
                    .modelName(defaultModelName)
                    .build());
        }
        ChatRequest syntheticRequest = builder.build();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        listener.onRequest(new ChatModelRequestContext(syntheticRequest, delegate.provider(), attributes));
        try {
            String reply = delegate.chat(prompt);
            ChatResponse syntheticResponse = ChatResponse.builder()
                    .aiMessage(reply == null ? null : AiMessage.from(reply))
                    .build();
            listener.onResponse(new ChatModelResponseContext(
                    syntheticResponse, syntheticRequest, delegate.provider(), attributes));
            return reply;
        } catch (RuntimeException ex) {
            listener.onError(new ChatModelErrorContext(ex, syntheticRequest, delegate.provider(), attributes));
            throw ex;
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        Objects.requireNonNull(request, "request");
        ChatRequest effectiveRequest = adaptRequest(request);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        listener.onRequest(new ChatModelRequestContext(effectiveRequest, delegate.provider(), attributes));
        try {
            ChatResponse response = delegate.doChat(effectiveRequest);
            listener.onResponse(new ChatModelResponseContext(
                    response, effectiveRequest, delegate.provider(), attributes));
            return response;
        } catch (RuntimeException ex) {
            listener.onError(new ChatModelErrorContext(ex, effectiveRequest, delegate.provider(), attributes));
            throw ex;
        }
    }

    private ChatRequest adaptRequest(ChatRequest request) {
        if (!(delegate instanceof dev.langchain4j.model.openai.OpenAiChatModel) || request == null) {
            return request;
        }
        if (request.parameters() instanceof dev.langchain4j.model.openai.OpenAiChatRequestParameters) {
            return request;
        }
        if (defaultModelName == null || defaultModelName.isBlank()) {
            return request;
        }
        return ChatRequest.builder()
                .messages(request.messages())
                .parameters(dev.langchain4j.model.openai.OpenAiChatRequestParameters.builder()
                        .modelName(defaultModelName)
                        .build())
                .build();
    }
}
