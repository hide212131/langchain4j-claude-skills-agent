package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * ChatModel wrapper that adds OpenTelemetry tracing.
 * Exports traces to LangFuse or other OTLP-compatible observability platforms.
 */
public final class ObservableChatModel implements ChatModel {

    private final ChatModel delegate;
    private final Tracer tracer;

    public ObservableChatModel(ChatModel delegate, OpenTelemetry openTelemetry) {
        this.delegate = delegate;
        this.tracer = openTelemetry.getTracer("langchain4j-claude-skills-agent");
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        Span span = tracer.spanBuilder("chat_model_request").startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            if (request.messages() != null) {
                span.setAttribute("message_count", request.messages().size());
            }
            
            ChatResponse response = delegate.doChat(request);
            
            if (response.aiMessage() != null) {
                AiMessage aiMessage = response.aiMessage();
                if (aiMessage.text() != null) {
                    span.setAttribute("response_length", aiMessage.text().length());
                }
            }
            
            if (response.tokenUsage() != null) {
                if (response.tokenUsage().inputTokenCount() != null) {
                    span.setAttribute("token_usage.input", response.tokenUsage().inputTokenCount());
                }
                if (response.tokenUsage().outputTokenCount() != null) {
                    span.setAttribute("token_usage.output", response.tokenUsage().outputTokenCount());
                }
                if (response.tokenUsage().totalTokenCount() != null) {
                    span.setAttribute("token_usage.total", response.tokenUsage().totalTokenCount());
                }
            }
            
            span.setStatus(StatusCode.OK);
            return response;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}

