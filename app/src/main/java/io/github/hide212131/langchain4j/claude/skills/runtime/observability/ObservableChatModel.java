package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.stream.Collectors;

/**
 * ChatModel wrapper that adds OpenTelemetry tracing.
 * Exports traces to LangFuse or other OTLP-compatible observability platforms.
 * Captures prompt content, response, and detailed metrics for context engineering analysis.
 */
public final class ObservableChatModel implements ChatModel {

    private final ChatModel delegate;
    private final Tracer tracer;
    private static final AttributeKey<String> PROMPT_KEY = AttributeKey.stringKey("llm.prompt");
    private static final AttributeKey<String> RESPONSE_KEY = AttributeKey.stringKey("llm.response");
    private static final AttributeKey<String> MESSAGES_KEY = AttributeKey.stringKey("llm.messages");

    public ObservableChatModel(ChatModel delegate, OpenTelemetry openTelemetry) {
        this.delegate = delegate;
        this.tracer = openTelemetry.getTracer("langchain4j-claude-skills-agent");
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        Span span = tracer.spanBuilder("chat_model_request").startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Capture message details
            if (request.messages() != null && !request.messages().isEmpty()) {
                span.setAttribute("message_count", request.messages().size());
                
                // Capture full message content for prompt analysis
                String messagesContent = request.messages().stream()
                    .map(this::formatMessage)
                    .collect(Collectors.joining("\n---\n"));
                span.setAttribute(MESSAGES_KEY, messagesContent);
                
                // For single message, also set as prompt
                if (request.messages().size() == 1) {
                    ChatMessage firstMessage = request.messages().get(0);
                    String messageText = extractMessageText(firstMessage);
                    if (messageText != null) {
                        span.setAttribute(PROMPT_KEY, messageText);
                    }
                }
            }
            
            ChatResponse response = delegate.doChat(request);
            
            // Capture response details
            if (response.aiMessage() != null) {
                AiMessage aiMessage = response.aiMessage();
                if (aiMessage.text() != null) {
                    span.setAttribute("response_length", aiMessage.text().length());
                    span.setAttribute(RESPONSE_KEY, aiMessage.text());
                }
            }
            
            // Capture token usage
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

    private String extractMessageText(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage userMessage) {
            return userMessage.singleText();
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        return message.toString();
    }

    private String formatMessage(ChatMessage message) {
        String role = message.type().toString();
        String content = extractMessageText(message);
        return String.format("[%s]: %s", role, content != null ? content : "(empty)");
    }
}

