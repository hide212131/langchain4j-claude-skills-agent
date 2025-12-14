package io.github.hide212131.langchain4j.claude.skills.runtime;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentRequest;
import dev.langchain4j.agentic.agent.AgentResponse;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI Official SDK を用いた Plan/Act/Reflect 実行フロー。
 */
final class OpenAiAgentFlow implements AgentFlow {

    private static final int PREVIEW_LIMIT = 400;

    private final LlmConfiguration configuration;

    OpenAiAgentFlow(LlmConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public AgentFlowResult run(SkillDocument document, String goal, VisibilityLog log, boolean basicLog, String runId) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(runId, "runId");

        String safeGoal = goal == null ? "" : goal.trim();
        ChatModel chatModel = buildChatModel(log, basicLog, runId, document.id());

        PlannerAgent planner = AgenticServices.agentBuilder(PlannerAgent.class)
                .chatModel(chatModel)
                .outputKey("plan")
                .beforeAgentInvocation(req -> logAgentStart(log, basicLog, runId, document.id(), "plan", req))
                .afterAgentInvocation(res -> logAgentDone(log, basicLog, runId, document.id(), "plan", res))
                .build();

        ActorAgent actor = AgenticServices.agentBuilder(ActorAgent.class)
                .chatModel(chatModel)
                .outputKey("artifact")
                .beforeAgentInvocation(req -> logAgentStart(log, basicLog, runId, document.id(), "act", req))
                .afterAgentInvocation(res -> logAgentDone(log, basicLog, runId, document.id(), "act", res))
                .build();

        ReflectAgent reflect = AgenticServices.agentBuilder(ReflectAgent.class)
                .chatModel(chatModel)
                .outputKey("reflect")
                .beforeAgentInvocation(req -> logAgentStart(log, basicLog, runId, document.id(), "reflect", req))
                .afterAgentInvocation(res -> logAgentDone(log, basicLog, runId, document.id(), "reflect", res))
                .build();

        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(planner, actor, reflect)
                .outputKey("artifact")
                .beforeAgentInvocation(req -> logAgentStart(log, basicLog, runId, document.id(), "workflow", req))
                .afterAgentInvocation(res -> logAgentDone(log, basicLog, runId, document.id(), "workflow", res))
                .errorHandler(ctx -> handleError(log, runId, document.id(), ctx))
                .build();

        Map<String, Object> inputs = Map.of(
                "skillName", document.name(),
                "skillDescription", document.description(),
                "skillBody", document.body(),
                "goal", safeGoal);

        try {
            ResultWithAgenticScope<?> result = workflow.invokeWithAgenticScope(inputs);
            AgenticScope scope = result.agenticScope();

            String planLog = stringOrFallback(scope.readState("plan"), "Plan を取得できませんでした");
            String actLog = stringOrFallback(scope.readState("artifact"), "Act を取得できませんでした");
            String reflectLog = stringOrFallback(scope.readState("reflect"), "Reflect を取得できませんでした");
            String artifact = stringOrFallback(result.result(), "");

            return new AgentFlowResult(planLog, actLog, reflectLog, artifact);
        } catch (RuntimeException ex) {
            log.error(
                    runId,
                    document.id(),
                    "error",
                    "openai.run",
                    "OpenAI 実行経路で例外が発生しました (apiKey=" + configuration.maskedApiKey() + ")",
                    "",
                    "",
                    ex);
            throw ex;
        }
    }

    private ChatModel buildChatModel(VisibilityLog log, boolean basicLog, String runId, String skillId) {
        OpenAiOfficialChatModel.Builder builder = OpenAiOfficialChatModel.builder()
                .apiKey(configuration.openAiApiKey())
                .listeners(List.of(new VisibilityChatModelListener(log, basicLog, runId, skillId)));
        if (configuration.openAiBaseUrl() != null) {
            builder.baseUrl(configuration.openAiBaseUrl());
        }
        if (configuration.openAiModel() != null) {
            builder.modelName(configuration.openAiModel());
        }
        return builder.build();
    }

    private void logAgentStart(
            VisibilityLog log, boolean basicLog, String runId, String skillId, String phase, AgentRequest request) {
        log.info(
                basicLog,
                runId,
                skillId,
                phase,
                "agent.start",
                "エージェントを呼び出します: " + request.agentName(),
                "inputs=" + preview(request.inputs()),
                "");
    }

    private void logAgentDone(
            VisibilityLog log, boolean basicLog, String runId, String skillId, String phase, AgentResponse response) {
        log.info(
                basicLog,
                runId,
                skillId,
                phase,
                "agent.done",
                "エージェントが完了しました: " + response.agentName(),
                "",
                "output=" + preview(response.output()));
    }

    private ErrorRecoveryResult handleError(VisibilityLog log, String runId, String skillId, ErrorContext context) {
        log.warn(
                runId,
                skillId,
                "error",
                "agent.error",
                "エージェント実行で例外が発生しました",
                preview(context.agenticScope().state()),
                "",
                context.exception());
        return ErrorRecoveryResult.throwException();
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
        if (text.length() <= PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, PREVIEW_LIMIT) + "...(truncated)";
    }

    private static final class VisibilityChatModelListener implements ChatModelListener {

        private final VisibilityLog log;
        private final boolean basicLog;
        private final String runId;
        private final String skillId;

        VisibilityChatModelListener(VisibilityLog log, boolean basicLog, String runId, String skillId) {
            this.log = Objects.requireNonNull(log, "log");
            this.basicLog = basicLog;
            this.runId = Objects.requireNonNull(runId, "runId");
            this.skillId = Objects.requireNonNull(skillId, "skillId");
        }

        @Override
        public void onRequest(ChatModelRequestContext ctx) {
            String request = ctx.chatRequest() == null ? "(none)" : ctx.chatRequest().toString();
            log.info(
                    basicLog,
                    runId,
                    skillId,
                    "llm",
                    "llm.request",
                    "OpenAI へリクエストを送信します",
                    "",
                    request);
        }

        @Override
        public void onResponse(ChatModelResponseContext ctx) {
            ChatResponse response = ctx.chatResponse();
            String output = response != null && response.aiMessage() != null
                    ? response.aiMessage().text()
                    : "(empty)";
            log.info(
                    basicLog,
                    runId,
                    skillId,
                    "llm",
                    "llm.response",
                    "OpenAI から応答を受信しました",
                    "",
                    output);
        }

        @Override
        public void onError(ChatModelErrorContext ctx) {
            log.error(
                    runId,
                    skillId,
                    "error",
                    "llm.error",
                    "OpenAI 呼び出しでエラーが発生しました",
                    "",
                    "",
                    ctx.error());
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
                手順の概要を簡潔に日本語で返してください。
                """)
        @Agent(value = "planner", description = "Plan ステップを生成する")
        String plan(
                @V("skillName") String skillName,
                @V("skillDescription") String skillDescription,
                @V("skillBody") String skillBody,
                @V("goal") String goal);
    }

    public interface ActorAgent {
        @UserMessage("""
                以下の Plan を実行してください。
                Plan:
                {{plan}}
                
                スキル本文:
                {{skillBody}}
                
                ゴール: {{goal}}
                実行結果のみを日本語で出力してください。
                """)
        @Agent(value = "actor", description = "Act ステップを実行する")
        String act(@V("plan") String plan, @V("skillBody") String skillBody, @V("goal") String goal);
    }

    public interface ReflectAgent {
        @UserMessage("""
                以下の Plan と Act 結果を振り返り、達成度と改善点を短くまとめてください。
                Plan:
                {{plan}}
                
                Act 結果:
                {{artifact}}
                
                ゴール: {{goal}}
                """)
        @Agent(value = "reflector", description = "Reflect ステップをまとめる")
        String reflect(@V("plan") String plan, @V("artifact") String artifact, @V("goal") String goal);
    }
}
