package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
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

    public ExecutionTaskList plan(SkillDocument document, String goal, String inputFilePath, String outputDirectoryPath,
            String planSummary) {
        Objects.requireNonNull(document, "document");
        String safeGoal = goal == null ? "" : goal.trim();
        String safeInputFilePath = inputFilePath == null ? "" : inputFilePath.trim();
        String safeOutputDirectoryPath = outputDirectoryPath == null ? "" : outputDirectoryPath.trim();
        String safePlan = planSummary == null ? "" : planSummary.trim();
        ExecutionTaskList plan = agent.plan(document.name(), document.description(), document.body(), safeGoal,
                safeInputFilePath, safeOutputDirectoryPath, safePlan);
        if (plan == null) {
            return ExecutionTaskList.empty(safeGoal);
        }
        return plan;
    }

    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public interface PlanningAgent {
        @SystemMessage("""
                ゴールを達成するためのスキル実行をする前準備として**実行計画のタスクリストを作成**してください。必要に応じてツールを使って情報収集してください。

                事前条件:
                - スキルは、スキルディレクトリ直下の SKILL.md に記載された内容や、そこから参照される追加ファイルの情報に基づいて実行します。
                  適切な実行計画を立てるには、必要に応じて追加ファイルの内容を参照する必要があります。
                - スキルディレクトリ上の追加ファイルの情報を読むには ExecutionEnvironmentTool を使います。

                要求事項:
                - タスクは実行環境で実行可能なコマンドに分解してください。
                - 各タスクは具体的かつ実行可能である必要があります。
                - 各タスクには title, description, command, output を含めてください。
                - タスクは順序付けられたリストとして提供してください。
                """)
        @UserMessage("""
                ゴール: {{goal}}
                入力ファイル: {{inputFilePath}}
                出力フォルダ: {{outputDirectoryPath}}

                スキル名: {{skillName}}
                説明: {{skillDescription}}
                スキル本文:
                {{skillBody}}

                事前プラン:
                {{planSummary}}

                """)
        @Agent(value = "executionPlanningAgent", description = "実行計画を作成する")
        ExecutionTaskList plan(@V("skillName") String skillName, @V("skillDescription") String skillDescription,
                @V("skillBody") String skillBody, @V("goal") String goal, @V("inputFilePath") String inputFilePath,
                @V("outputDirectoryPath") String outputDirectoryPath, @V("planSummary") String planSummary);
    }
}
