package io.github.hide212131.langchain4j.claude.skills.runtime;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironmentFactory;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionEnvironmentTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionPlanningAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.ExecutionTaskList;
import io.github.hide212131.langchain4j.claude.skills.runtime.planning.LocalResourceTool;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.MetricsPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.PromptPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.TokenUsage;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.TokenUsageExtractor;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventType;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 実行計画作成にフォーカスした LLM フロー。 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.GuardLogStatement", "PMD.CouplingBetweenObjects" })
final class ExecutionPlanningFlow implements AgentFlow {

    private static final int PREVIEW_LIMIT = 400;

    private final LlmConfiguration configuration;
    private final ExecutionBackend executionBackend;

    ExecutionPlanningFlow(LlmConfiguration configuration, ExecutionBackend executionBackend) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.executionBackend = Objects.requireNonNull(executionBackend, "executionBackend");
    }

    @Override
    public AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir) {
        return run(document, goal, log, basicLog, runId, skillPath, artifactsDir, VisibilityEventPublisher.noop());
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId,
            String skillPath, String artifactsDir, VisibilityEventPublisher events) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(skillPath, "skillPath");
        Objects.requireNonNull(events, "events");

        String safeGoal = goal == null ? "" : goal.trim();
        ChatModel chatModel = buildChatModel(log, basicLog, runId, document.id(), events);
        long start = System.nanoTime();
        try {
            ExecutionTaskList taskList = buildTaskList(chatModel, document, safeGoal, skillPath, "", log, runId);
            String planLog = taskList.formatForLog();
            log.info(basicLog, runId, document.id(), "plan", "plan.tasks", "実行計画を作成しました", "", planLog);
            publishWorkflowMetrics(events, runId, document.id(), nanosToMillis(start));
            String note = "実行計画のみを作成しました。";
            return new AgentFlowResult(planLog, note, note, "");
        } catch (RuntimeException ex) {
            log.error(runId, document.id(), "error", "planning.run",
                    "実行計画の作成中に例外が発生しました (apiKey=" + configuration.maskedApiKey() + ")", "", "", ex);
            throw ex;
        }
    }

    private ChatModel buildChatModel(VisibilityLog log, boolean basicLog, String runId, String skillId,
            VisibilityEventPublisher events) {
        OpenAiOfficialChatModel.Builder builder = OpenAiOfficialChatModel.builder().apiKey(configuration.openAiApiKey())
                .listeners(List.of(new VisibilityChatModelListener(log, basicLog, runId, skillId, events,
                        configuration.openAiModel())))
                .supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA)).strictJsonSchema(true);
        if (configuration.openAiBaseUrl() != null) {
            builder.baseUrl(configuration.openAiBaseUrl());
        }
        if (configuration.openAiModel() != null) {
            builder.modelName(configuration.openAiModel());
        }
        return builder.build();
    }

    private ExecutionTaskList buildTaskList(ChatModel chatModel, SkillDocument document, String goal, String skillPath,
            String planSummary, VisibilityLog log, String runId) {
        try {
            Path skillMdPath = Path.of(skillPath);
            LocalResourceTool resourceTool = new LocalResourceTool(skillMdPath);
            ExecutionEnvironmentTool environmentTool = new ExecutionEnvironmentTool(
                    new CodeExecutionEnvironmentFactory(executionBackend), skillMdPath);
            ExecutionPlanningAgent planner = new ExecutionPlanningAgent(chatModel, resourceTool, environmentTool);
            return planner.plan(document, goal, skillPath, planSummary);
        } catch (RuntimeException ex) {
            log.warn(runId, document.id(), "plan", "plan.tasks", "実行計画の作成に失敗しました", "", "", ex);
            return ExecutionTaskList.empty(goal);
        }
    }

    private static void publishPrompt(VisibilityEventPublisher events, String runId, String skillId, String step,
            String prompt, String response, String model, TokenUsage usage) {
        VisibilityEventMetadata metadata = new VisibilityEventMetadata(runId, skillId, "llm", step, null);
        events.publish(new VisibilityEvent(VisibilityEventType.PROMPT, metadata,
                new PromptPayload(prompt, response, model, "assistant", usage)));
    }

    private static String maskPreview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, PREVIEW_LIMIT) + "...(truncated)";
    }

    private static void publishWorkflowMetrics(VisibilityEventPublisher events, String runId, String skillId,
            long latencyMillis) {
        VisibilityEventMetadata metadata = new VisibilityEventMetadata(runId, skillId, "workflow", "workflow.done",
                null);
        events.publish(new VisibilityEvent(VisibilityEventType.METRICS, metadata,
                new MetricsPayload(null, null, latencyMillis, null)));
    }

    private static void publishLlmMetrics(VisibilityEventPublisher events, String runId, String skillId,
            TokenUsage usage, long latencyMillis) {
        Long input = usage == null ? null : usage.inputTokens();
        Long output = usage == null ? null : usage.outputTokens();
        VisibilityEventMetadata metadata = new VisibilityEventMetadata(runId, skillId, "llm", "llm.metrics", null);
        events.publish(new VisibilityEvent(VisibilityEventType.METRICS, metadata,
                new MetricsPayload(input, output, latencyMillis, null)));
    }

    private static long nanosToMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static final class VisibilityChatModelListener implements ChatModelListener {

        private final VisibilityLog log;
        private final boolean basicLog;
        private final String runId;
        private final String skillId;
        private final VisibilityEventPublisher events;
        private final String model;
        private long lastRequestStartNanos;

        VisibilityChatModelListener(VisibilityLog log, boolean basicLog, String runId, String skillId,
                VisibilityEventPublisher events, String model) {
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
            log.info(basicLog, runId, skillId, "llm", "llm.request", "OpenAI へリクエストを送信します", "", request);
            publishPrompt(events, runId, skillId, "llm.request", request, null, model, null);
        }

        @Override
        public void onResponse(ChatModelResponseContext ctx) {
            ChatResponse response = ctx.chatResponse();
            String output = response != null && response.aiMessage() != null ? response.aiMessage().text() : "(empty)";
            log.info(basicLog, runId, skillId, "llm", "llm.response", "OpenAI から応答を受信しました", "", output);
            TokenUsage usage = TokenUsageExtractor.from(response);
            publishPrompt(events, runId, skillId, "llm.response", "(llm-response)", output, model, usage);
            publishLlmMetrics(events, runId, skillId, usage, nanosToMillis(lastRequestStartNanos));
        }

        @Override
        public void onError(ChatModelErrorContext ctx) {
            log.error(runId, skillId, "error", "llm.error", "OpenAI 呼び出しでエラーが発生しました", "", "", ctx.error());
        }
    }
}
