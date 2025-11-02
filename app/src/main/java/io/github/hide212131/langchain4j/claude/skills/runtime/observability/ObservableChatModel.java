package io.github.hide212131.langchain4j.claude.skills.runtime.observability;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ChatModel wrapper that emits spans with prompt/response payloads for Langfuse.
 */
public final class ObservableChatModel implements ChatModel {

    private static final AttributeKey<String> PROMPT_KEY = AttributeKey.stringKey("llm.prompt");
    private static final AttributeKey<String> RESPONSE_KEY = AttributeKey.stringKey("llm.response");
    private static final AttributeKey<String> MESSAGES_KEY = AttributeKey.stringKey("llm.messages");

    private final ChatModel delegate;
    private final Tracer tracer;

    public ObservableChatModel(ChatModel delegate, OpenTelemetry openTelemetry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(openTelemetry, "openTelemetry");
        this.tracer = openTelemetry.getTracer("langchain4j-claude-skills-agent");
    }

    public ChatModel delegate() {
        return delegate;
    }

    @Override
    public String chat(String prompt) {
        Span span = tracer.spanBuilder("chat_model.chat").startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (prompt != null && !prompt.isEmpty()) {
                span.setAttribute(PROMPT_KEY, prompt);
            }
            String reply = delegate.chat(prompt);
            if (reply != null) {
                span.setAttribute(RESPONSE_KEY, reply);
                span.setAttribute("response.length", reply.length());
            }
            span.setStatus(StatusCode.OK);
            return reply;
        } catch (Exception ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getMessage());
            throw ex;
        } finally {
            span.end();
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        Span span = tracer.spanBuilder("chat_model.request").startSpan();
        try (Scope scope = span.makeCurrent()) {
            if (request.messages() != null && !request.messages().isEmpty()) {
                span.setAttribute("message.count", request.messages().size());
                String content = request.messages().stream()
                        .map(this::formatMessage)
                        .collect(Collectors.joining("\n---\n"));
                span.setAttribute(MESSAGES_KEY, content);
                if (request.messages().size() == 1) {
                    ChatMessage first = request.messages().get(0);
                    String prompt = extractMessageText(first);
                    if (prompt != null && !prompt.isEmpty()) {
                        span.setAttribute(PROMPT_KEY, prompt);
                    }
                }
            }

            ChatResponse response = delegate.doChat(adaptRequest(request));
            if (response.aiMessage() != null && response.aiMessage().text() != null) {
                String reply = response.aiMessage().text();
                span.setAttribute("response.length", reply.length());
                span.setAttribute(RESPONSE_KEY, reply);
            }
            if (response.tokenUsage() != null) {
                if (response.tokenUsage().inputTokenCount() != null) {
                    span.setAttribute("token.input", response.tokenUsage().inputTokenCount());
                }
                if (response.tokenUsage().outputTokenCount() != null) {
                    span.setAttribute("token.output", response.tokenUsage().outputTokenCount());
                }
                if (response.tokenUsage().totalTokenCount() != null) {
                    span.setAttribute("token.total", response.tokenUsage().totalTokenCount());
                }
            }

            span.setStatus(StatusCode.OK);
            return response;
        } catch (Exception ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getMessage());
            throw ex;
        } finally {
            span.end();
        }
    }

    private String formatMessage(ChatMessage message) {
        String role = message.type().toString();
        String text = extractMessageText(message);
        return "[" + role + "]: " + (text == null ? "(empty)" : text);
    }

    private ChatRequest adaptRequest(ChatRequest request) {
        if (!(delegate instanceof OpenAiChatModel) || request == null) {
            return request;
        }
        if (request.parameters() instanceof OpenAiChatRequestParameters) {
            return request;
        }
        return ChatRequest.builder()
                .messages(request.messages())
                .parameters(OpenAiChatRequestParameters.builder().build())
                .build();
    }

    private String extractMessageText(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        return null;
    }
}

