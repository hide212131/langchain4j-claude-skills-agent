package io.github.hide212131.langchain4j.claude.skills.runtime;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.CodeExecutionEnvironmentFactory;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionBackend;
import io.github.hide212131.langchain4j.claude.skills.runtime.execution.ExecutionChainAgent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.AgentStatePayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.MetricsPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.PromptPayload;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.TokenUsage;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.TokenUsageExtractor;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEvent;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventMetadata;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventPublisher;
import io.github.hide212131.langchain4j.claude.skills.runtime.visibility.VisibilityEventType;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** OpenAI Official SDK を用いた Plan/Act/Reflect 実行フロー。 */
@SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.GuardLogStatement", "PMD.CouplingBetweenObjects",
        "PMD.UseObjectForClearerAPI", "PMD.TooManyMethods" })
final class OpenAiAgentFlow implements AgentFlow {

    private static final int PREVIEW_LIMIT = 400;
    private static final String KEY_PLAN = "plan";
    private static final String KEY_EXECUTION_PLAN = "executionPlan";
    private static final String KEY_EXECUTION_REPORT = "executionReport";
    private static final String KEY_REFLECT = "reflect";
    private static final String KEY_GOAL = "goal";
    private static final String KEY_SKILL_PATH = "skillPath";
    private static final String KEY_SKILL_CONTEXT = "skillContext";
    private static final String KEY_ARTIFACTS_DIR = "artifactsDir";

    private final LlmConfiguration configuration;
    private final ExecutionBackend executionBackend;

    OpenAiAgentFlow(LlmConfiguration configuration, ExecutionBackend executionBackend) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.executionBackend = Objects.requireNonNull(executionBackend, "executionBackend");
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
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
        CodeExecutionEnvironmentFactory environmentFactory = new CodeExecutionEnvironmentFactory(executionBackend);

        PlannerAgent planner = AgenticServices.agentBuilder(PlannerAgent.class).chatModel(chatModel).outputKey(KEY_PLAN)
                .listener(createAgentListener(log, basicLog, runId, document.id(), KEY_PLAN, events)).build();

        ExecutionPlanAgent actor = AgenticServices.agentBuilder(ExecutionPlanAgent.class).chatModel(chatModel)
                .outputKey(KEY_EXECUTION_PLAN)
                .listener(createAgentListener(log, basicLog, runId, document.id(), "plan.exec", events)).build();

        ReflectAgent reflect = AgenticServices.agentBuilder(ReflectAgent.class).chatModel(chatModel)
                .outputKey(KEY_REFLECT)
                .listener(createAgentListener(log, basicLog, runId, document.id(), KEY_REFLECT, events)).build();

        ExecutionChainAgent executor = new ExecutionChainAgent(environmentFactory);

        UntypedAgent workflow = AgenticServices.sequenceBuilder().subAgents(planner, actor, executor, reflect)
                .outputKey(KEY_EXECUTION_REPORT)
                .listener(createAgentListener(log, basicLog, runId, document.id(), "workflow", events))
                .errorHandler(ctx -> handleError(log, runId, document.id(), ctx)).build();

        String skillContext = SkillContextLoader.load(Path.of(skillPath), document.body());
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("skillName", document.name());
        inputs.put("skillDescription", document.description());
        inputs.put("skillBody", document.body());
        inputs.put(KEY_GOAL, safeGoal);
        inputs.put(KEY_SKILL_PATH, skillPath);
        inputs.put(KEY_SKILL_CONTEXT, skillContext);
        inputs.put(KEY_ARTIFACTS_DIR, artifactsDir == null ? "" : artifactsDir);

        long workflowStart = System.nanoTime();
        try {
            ResultWithAgenticScope<?> result = workflow.invokeWithAgenticScope(inputs);
            AgenticScope scope = result.agenticScope();

            String planLog = stringOrFallback(scope.readState(KEY_PLAN), "Plan を取得できませんでした");
            String actLog = stringOrFallback(scope.readState(KEY_EXECUTION_PLAN), "実行計画を取得できませんでした");
            String reflectLog = stringOrFallback(scope.readState(KEY_REFLECT), "Reflect を取得できませんでした");
            String artifact = stringOrFallback(result.result(), "");

            publishWorkflowMetrics(events, runId, document.id(), nanosToMillis(workflowStart));
            return new AgentFlowResult(planLog, actLog, reflectLog, artifact);
        } catch (RuntimeException ex) {
            log.error(runId, document.id(), "error", "openai.run",
                    "OpenAI 実行経路で例外が発生しました (apiKey=" + configuration.maskedApiKey() + ")", "", "", ex);
            throw ex;
        }
    }

    private ChatModel buildChatModel(VisibilityLog log, boolean basicLog, String runId, String skillId,
            VisibilityEventPublisher events) {
        OpenAiOfficialChatModel.Builder builder = OpenAiOfficialChatModel.builder().apiKey(configuration.openAiApiKey())
                .listeners(List.of(new VisibilityChatModelListener(log, basicLog, runId, skillId, events,
                        configuration.openAiModel())));
        if (configuration.openAiBaseUrl() != null) {
            builder.baseUrl(configuration.openAiBaseUrl());
        }
        if (configuration.openAiModel() != null) {
            builder.modelName(configuration.openAiModel());
        }
        return builder.build();
    }

    private AgentListener createAgentListener(VisibilityLog log, boolean basicLog, String runId, String skillId,
            String phase, VisibilityEventPublisher events) {
        return new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                logAgentStart(log, basicLog, runId, skillId, phase, request, events);
            }

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                logAgentDone(log, basicLog, runId, skillId, phase, response, events);
            }
        };
    }

    private void logAgentStart(VisibilityLog log, boolean basicLog, String runId, String skillId, String phase,
            AgentRequest request, VisibilityEventPublisher events) {
        String inputPreview = preview(request.inputs());
        log.info(basicLog, runId, skillId, phase, "agent.start", "エージェントを呼び出します: " + request.agentName(),
                "inputs=" + inputPreview, "");
        publishAgentState(events, runId, skillId, phase, "agent.start", inputPreview, "start");
    }

    private void logAgentDone(VisibilityLog log, boolean basicLog, String runId, String skillId, String phase,
            AgentResponse response, VisibilityEventPublisher events) {
        String outputPreview = preview(response.output());
        log.info(basicLog, runId, skillId, phase, "agent.done", "エージェントが完了しました: " + response.agentName(), "",
                "output=" + outputPreview);
        publishAgentState(events, runId, skillId, phase, "agent.done", outputPreview, "done");
    }

    private ErrorRecoveryResult handleError(VisibilityLog log, String runId, String skillId, ErrorContext context) {
        log.warn(runId, skillId, "error", "agent.error", "エージェント実行で例外が発生しました", preview(context.agenticScope().state()),
                "", context.exception());
        return ErrorRecoveryResult.throwException();
    }

    private static void publishAgentState(VisibilityEventPublisher events, String runId, String skillId, String phase,
            String step, String decision, String summary) {
        VisibilityEventMetadata metadata = new VisibilityEventMetadata(runId, skillId, phase, step, null);
        events.publish(new VisibilityEvent(VisibilityEventType.AGENT_STATE, metadata,
                new AgentStatePayload(summary, decision, summary)));
    }

    private static void publishPrompt(VisibilityEventPublisher events, String runId, String skillId, String step,
            String prompt, String response, String model) {
        publishPrompt(events, runId, skillId, step, prompt, response, model, null);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void publishPrompt(VisibilityEventPublisher events, String runId, String skillId, String step,
            String prompt, String response, String model, TokenUsage usage) {
        VisibilityEventMetadata metadata = new VisibilityEventMetadata(runId, skillId, "llm", step, null);
        events.publish(new VisibilityEvent(VisibilityEventType.PROMPT, metadata,
                new PromptPayload(maskPreview(prompt), response, model, "assistant", usage)));
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
        if (startNanos <= 0L) {
            return 0L;
        }
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String stringOrFallback(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private String preview(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return maskPreview(text);
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
            publishPrompt(events, runId, skillId, "llm.request", request, null, model);
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

    public interface PlannerAgent {
        @UserMessage("""
                あなたは SKILL.md をもとにゴールを達成するプランナーです。
                スキル名: {{skillName}}
                説明: {{skillDescription}}
                スキル本文:
                {{skillBody}}

                ゴール: {{goal}}
                SKILL.md パス: {{skillPath}}
                参照資料:
                {{skillContext}}
                手順の概要を簡潔に日本語で返してください。
                """)
        @Agent(value = "planner", description = "Plan ステップを生成する")
        String plan(@V("skillName") String skillName, @V("skillDescription") String skillDescription,
                @V("skillBody") String skillBody, @V("goal") String goal, @V("skillPath") String skillPath,
                @V("skillContext") String skillContext);
    }

    public interface ExecutionPlanAgent {
        @UserMessage("""
                以下の Plan を実行するためのコマンド列を JSON で返してください。
                Plan:
                {{plan}}

                スキル本文:
                {{skillBody}}

                ゴール: {{goal}}

                出力フォーマット:
                {
                  "commands": [
                    "command1",
                    "command2"
                  ],
                  "outputPatterns": [
                    "**/*.pptx",
                    "**/*.png"
                  ]
                }

                ルール:
                - JSON 以外の文字は出力しない
                - commands は 1 件以上
                - pip/npm install は使わない
                - commands は 1 行で実行可能なシェルコマンドのみ
                - 説明文、コメント、箇条書き、括弧書きは commands に含めない
                - ゴールが曖昧な場合は仮の題材で 3 枚以上のスライドを作成し、そのための実行コマンドを返す
                - Python 実行は python3 を使う
                - 利用できる作成系スクリプトは scripts/html2pptx.js と scripts/thumbnail.py のみ
                """)
        @Agent(value = "executionPlanner", description = "実行計画を作成する")
        String planExecution(@V("plan") String plan, @V("skillBody") String skillBody, @V("goal") String goal,
                @V("skillPath") String skillPath, @V("skillContext") String skillContext);
    }

    public interface ReflectAgent {
        @UserMessage("""
                以下の Plan と 実行結果を振り返り、達成度と改善点を短くまとめてください。
                Plan:
                {{plan}}

                実行結果:
                {{executionReport}}

                ゴール: {{goal}}
                """)
        @Agent(value = "reflector", description = "Reflect ステップをまとめる")
        String reflect(@V("plan") String plan, @V("executionReport") String executionReport, @V("goal") String goal);
    }
}
