package io.github.hide212131.langchain4j.claude.skills.runtime;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.MetricsPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.PromptPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.TokenUsage;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.TokenUsageExtractor;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.SkillEventType;
import java.util.Objects;

/** LLM リクエスト/レスポンスをスキルイベントへ送るリスナー。 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.GuardLogStatement" })
final class SkillChatModelListener implements ChatModelListener {

    private static final String PHASE_LLM = "llm";
    private static final String STEP_LLM_REQUEST = "llm.request";
    private static final String STEP_LLM_RESPONSE = "llm.response";
    private static final String STEP_LLM_METRICS = "llm.metrics";

    private final SkillLog log;
    private final boolean basicLog;
    private final String runId;
    private final String skillId;
    private final SkillEventPublisher events;
    private final String model;
    private long lastRequestStartNanos;

    SkillChatModelListener(SkillLog log, boolean basicLog, String runId, String skillId,
            SkillEventPublisher events, String model) {
        this.log = Objects.requireNonNull(log, "log");
        this.basicLog = basicLog;
        this.runId = Objects.requireNonNull(runId, "runId");
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.events = Objects.requireNonNull(events, "events");
        this.model = model;
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        String request = ctx.chatRequest() == null ? "(none)" : ctx.chatRequest().toString();
        lastRequestStartNanos = System.nanoTime();
        log.info(basicLog, runId, skillId, PHASE_LLM, STEP_LLM_REQUEST, "OpenAI へリクエストを送信します", "", request);
        publishPrompt(events, runId, skillId, STEP_LLM_REQUEST, request, null, model, null);
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        ChatResponse response = ctx.chatResponse();
        String output = response != null && response.aiMessage() != null ? response.aiMessage().text() : "(empty)";
        log.info(basicLog, runId, skillId, PHASE_LLM, STEP_LLM_RESPONSE, "OpenAI から応答を受信しました", "", output);
        TokenUsage usage = TokenUsageExtractor.from(response);
        publishPrompt(events, runId, skillId, STEP_LLM_RESPONSE, "(llm-response)", output, model, usage);
        publishLlmMetrics(events, runId, skillId, usage, nanosToMillis(lastRequestStartNanos));
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        log.error(runId, skillId, "error", "llm.error", "OpenAI 呼び出しでエラーが発生しました", "", "", ctx.error());
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void publishPrompt(SkillEventPublisher events, String runId, String skillId, String step,
            String prompt, String response, String model, TokenUsage usage) {
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, PHASE_LLM, step, null);
        events.publish(new SkillEvent(SkillEventType.PROMPT, metadata,
                new PromptPayload(prompt, response, model, "assistant", usage)));
    }

    private static void publishLlmMetrics(SkillEventPublisher events, String runId, String skillId,
            TokenUsage usage, long latencyMillis) {
        Long input = usage == null ? null : usage.inputTokens();
        Long output = usage == null ? null : usage.outputTokens();
        SkillEventMetadata metadata = new SkillEventMetadata(runId, skillId, PHASE_LLM, STEP_LLM_METRICS,
                null);
        events.publish(new SkillEvent(SkillEventType.METRICS, metadata,
                new MetricsPayload(input, output, latencyMillis, null)));
    }

    private static long nanosToMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
