package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
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
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * {@link ChatModelListener} that emits OpenTelemetry spans for each LLM request/response.
 */
public final class ObservabilityChatModelListener implements ChatModelListener {

    private static final AttributeKey<String> PROMPT_EVENT_KEY = AttributeKey.stringKey("preview");
    private static final AttributeKey<String> RESPONSE_EVENT_KEY = AttributeKey.stringKey("preview");
    private static final String ATTR_SPAN = ObservabilityChatModelListener.class.getName() + ".span";
    private static final String ATTR_SCOPE = ObservabilityChatModelListener.class.getName() + ".scope";
    private static final String ATTR_START = ObservabilityChatModelListener.class.getName() + ".start";
    private static final int ERROR_MESSAGE_PREVIEW_LIMIT = 4096;

    private final Tracer tracer;
    private final String system;
    private final String defaultModelName;

    public ObservabilityChatModelListener(Tracer tracer, String system, String defaultModelName) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.system = system == null || system.isBlank() ? "unknown" : system;
        this.defaultModelName = defaultModelName;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        Span span = tracer.spanBuilder("llm.chat")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        span.setAttribute("gen_ai.system", system);
        String modelName = resolveModelName(requestContext.modelProvider());
        if (modelName != null && !modelName.isBlank()) {
            span.setAttribute("gen_ai.request.model", modelName);
        }
        span.setAttribute("gen_ai.request.type", "chat");

        String promptPreview = summariseMessages(requestContext.chatRequest().messages());
        if (!promptPreview.isBlank()) {
            span.setAttribute("gen_ai.request.prompt", promptPreview);
            span.addEvent("llm.prompt", Attributes.of(PROMPT_EVENT_KEY, promptPreview));
        }
        emitAgenticScopeSnapshot(span, "input");

        Scope scope = span.makeCurrent();
        requestContext.attributes().put(ATTR_SPAN, span);
        requestContext.attributes().put(ATTR_SCOPE, scope);
        requestContext.attributes().put(ATTR_START, Instant.now());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Map<Object, Object> attributes = responseContext.attributes();
        Span span = (Span) attributes.remove(ATTR_SPAN);
        Scope scope = (Scope) attributes.remove(ATTR_SCOPE);
        Instant start = (Instant) attributes.remove(ATTR_START);

        if (scope != null) {
            scope.close();
        }
        if (span == null) {
            return;
        }

        try {
            if (start != null) {
                span.setAttribute("gen_ai.response.latency_ms", Duration.between(start, Instant.now()).toMillis());
            }
            ChatResponse chatResponse = responseContext.chatResponse();
            if (chatResponse != null) {
                recordResponse(span, chatResponse);
            }
            emitAgenticScopeSnapshot(span, "output");
            span.setStatus(StatusCode.OK);
        } catch (RuntimeException ex) {
            span.setStatus(StatusCode.ERROR, ex.getMessage());
            span.recordException(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Map<Object, Object> attributes = errorContext.attributes();
        Span span = (Span) attributes.remove(ATTR_SPAN);
        Scope scope = (Scope) attributes.remove(ATTR_SCOPE);
        Instant start = (Instant) attributes.remove(ATTR_START);

        if (scope != null) {
            scope.close();
        }
        if (span == null) {
            return;
        }

        if (start != null) {
            span.setAttribute("gen_ai.response.latency_ms", Duration.between(start, Instant.now()).toMillis());
        }
        emitAgenticScopeSnapshot(span, "error");
        span.recordException(errorContext.error());
        span.setStatus(StatusCode.ERROR, safeMessage(errorContext.error()));
        span.end();
    }

    private void recordResponse(Span span, ChatResponse chatResponse) {
        if (chatResponse.aiMessage() != null && chatResponse.aiMessage().text() != null) {
            String text = limit(chatResponse.aiMessage().text());
            span.setAttribute("gen_ai.response.completion", text);
            span.addEvent("llm.response", Attributes.of(RESPONSE_EVENT_KEY, text));
        }
        TokenUsage usage = chatResponse.tokenUsage();
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
        if (chatResponse.metadata() != null) {
            if (chatResponse.metadata().modelName() != null) {
                span.setAttribute("gen_ai.response.model", chatResponse.metadata().modelName());
            }
            if (chatResponse.metadata().finishReason() != null) {
                span.setAttribute("gen_ai.response.finish_reason", chatResponse.metadata().finishReason().name());
            }
            if (chatResponse.metadata().id() != null) {
                span.setAttribute("gen_ai.response.id", chatResponse.metadata().id());
            }
        }
    }

    private void emitAgenticScopeSnapshot(Span span, String phase) {
        if (span == null) {
            return;
        }
        AgenticScope scope = currentAgenticScope();
        if (scope == null) {
            return;
        }
        AgenticScopeSnapshots.snapshot(scope).ifPresent(snapshot -> {
            String eventName = "agentic.scope." + phase;
            span.addEvent(
                    eventName,
                    Attributes.builder()
                            .put("phase", phase)
                            .put("state", snapshot)
                            .build());
            span.setAttribute(eventName, snapshot);
        });
    }

    private AgenticScope currentAgenticScope() {
        return AgenticScopeContext.current();
    }

    private String resolveModelName(ModelProvider provider) {
        if (defaultModelName != null && !defaultModelName.isBlank()) {
            return defaultModelName;
        }
        return provider != null ? provider.name().toLowerCase(Locale.ROOT) : null;
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
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        return message.toString();
    }

    private String limit(String text) {
        if (text == null) {
            return "";
        }
        return text.strip();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= ERROR_MESSAGE_PREVIEW_LIMIT
                ? message
                : message.substring(0, ERROR_MESSAGE_PREVIEW_LIMIT);
    }
}
