package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * ChatModel wrapper that emits a GenAI CLIENT span for every LLM interaction.
 */
public final class InstrumentedChatModel implements ChatModel {

    private static final int MAX_TEXT_PREVIEW = 4096;

    private final ChatModel delegate;
    private final Tracer tracer;
    private final String system;
    private final String defaultModelName;

    public InstrumentedChatModel(ChatModel delegate, Tracer tracer, String system, String defaultModelName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.system = system == null || system.isBlank() ? delegate.getClass().getSimpleName() : system;
        this.defaultModelName = defaultModelName;
    }

    public ChatModel delegate() {
        return delegate;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        Objects.requireNonNull(request, "request");
        ChatRequest effectiveRequest = adaptRequest(request);
        Span span = tracer.spanBuilder("llm.chat")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        span.setAttribute("gen_ai.system", system);
        String modelName = resolveModelName();
        if (modelName != null && !modelName.isBlank()) {
            span.setAttribute("gen_ai.request.model", modelName);
        }
        span.setAttribute("gen_ai.request.type", "chat");
        String promptPreview = summariseMessages(effectiveRequest.messages());
        if (!promptPreview.isBlank()) {
            span.setAttribute("gen_ai.request.prompt", promptPreview);
            span.addEvent("llm.prompt", Attributes.of(AttributeKey.stringKey("preview"), promptPreview));
        }

        Instant start = Instant.now();
        try (Scope scope = span.makeCurrent()) {
            ChatResponse response = delegate.doChat(effectiveRequest);
            handleResponse(span, response, Duration.between(start, Instant.now()));
            span.setStatus(StatusCode.OK);
            return response;
        } catch (Exception ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, safeMessage(ex));
            throw ex;
        } finally {
            span.end();
        }
    }

    private void handleResponse(Span span, ChatResponse response, Duration latency) {
        if (latency != null) {
            span.setAttribute("gen_ai.response.latency_ms", latency.toMillis());
        }
        if (response == null) {
            return;
        }
        AiMessage aiMessage = response.aiMessage();
        if (aiMessage != null && aiMessage.text() != null) {
            String text = limit(aiMessage.text());
            span.setAttribute("gen_ai.response.completion", text);
            span.addEvent("llm.response", Attributes.of(AttributeKey.stringKey("preview"), text));
        }
        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            if (usage.inputTokenCount() != null) {
                span.setAttribute("gen_ai.usage.input_tokens", usage.inputTokenCount());
            }
            if (usage.outputTokenCount() != null) {
                span.setAttribute("gen_ai.usage.output_tokens", usage.outputTokenCount());
            }
            if (usage.totalTokenCount() != null) {
                span.setAttribute("gen_ai.usage.total_tokens", usage.totalTokenCount());
            }
        }
    }

    private String resolveModelName() {
        return defaultModelName;
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

    private String summariseMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            String role = message.getClass().getSimpleName().replace("Message", "").toLowerCase(Locale.ROOT);
            String content = limit(extractContent(message));
            if (content.isEmpty()) {
                continue;
            }
            joiner.add(role + ": " + content);
        }
        return joiner.toString();
    }

    private String extractContent(ChatMessage message) {
        if (message instanceof UserMessage user) {
            return user.singleText();
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        return message.toString();
    }

    private String limit(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.strip();
        if (cleaned.length() <= MAX_TEXT_PREVIEW) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_TEXT_PREVIEW);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= MAX_TEXT_PREVIEW ? message : message.substring(0, MAX_TEXT_PREVIEW);
    }
}
