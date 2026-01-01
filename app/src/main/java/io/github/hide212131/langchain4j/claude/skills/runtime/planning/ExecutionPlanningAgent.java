package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.github.hide212131.langchain4j.claude.skills.runtime.SkillDocument;
import java.util.Objects;

/**
 * 実行計画を作成するエージェント。
 */
public final class ExecutionPlanningAgent {

    private final PlanningAgent agent;

    public ExecutionPlanningAgent(ChatModel chatModel, LocalResourceTool resourceTool,
            ExecutionEnvironmentTool environmentTool) {
        Objects.requireNonNull(chatModel, "chatModel");
        Objects.requireNonNull(resourceTool, "resourceTool");
        Objects.requireNonNull(environmentTool, "environmentTool");
        this.agent = AgenticServices.agentBuilder(PlanningAgent.class).chatModel(chatModel)
                .tools(resourceTool, environmentTool).build();
    }

    public ExecutionTaskList plan(SkillDocument document, String goal, String skillPath, String planSummary) {
        Objects.requireNonNull(document, "document");
        String safeGoal = goal == null ? "" : goal.trim();
        String safePlan = planSummary == null ? "" : planSummary.trim();
        String safePath = skillPath == null ? "" : skillPath.trim();
        ExecutionTaskList plan = agent.plan(document.name(), document.description(), document.body(), safeGoal,
                safePath, safePlan);
        if (plan == null) {
            return ExecutionTaskList.empty(safeGoal);
        }
        return plan;
    }

    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public interface PlanningAgent {
        @UserMessage("""
                あなたはスキル実行計画エージェントです。必要に応じてツールを使って情報収集してください。

                スキル名: {{skillName}}
                説明: {{skillDescription}}
                スキル本文:
                {{skillBody}}

                ゴール: {{goal}}
                SKILL.md パス: {{skillPath}}
                事前プラン:
                {{planSummary}}

                ルール:
                - status は PENDING を使う
                - location は LOCAL または REMOTE
                - command は REMOTE の場合のみ設定する
                - コマンド実行が必要な作業は REMOTE とし、command を必ず設定する
                - ファイル変換や抽出などの実行作業は REMOTE とする
                - output.type は text/stdout/file/none のいずれか
                - task は 1 件以上にする
                """)
        @Agent(value = "executionPlanningAgent", description = "実行計画を作成する")
        ExecutionTaskList plan(@V("skillName") String skillName, @V("skillDescription") String skillDescription,
                @V("skillBody") String skillBody, @V("goal") String goal, @V("skillPath") String skillPath,
                @V("planSummary") String planSummary);
    }
}
