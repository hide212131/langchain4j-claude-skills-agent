package io.github.hide212131.langchain4j.claude.skills.runtime.workflow.act;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.github.hide212131.langchain4j.claude.skills.runtime.skill.SkillRuntime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LangChain4j tool wrapper that exposes the {@link SkillRuntime} as a single callable tool.
 */
public final class InvokeSkillTool {

    public static final String TOOL_NAME = "invokeSkill";

     private final SkillRuntime skillRuntime;
    private final ToolSpecification specification = ToolSpecification.builder()
            .name(TOOL_NAME)
            .description("Execute a skill identified by skillId with optional inputs bundle")
            .parameters(JsonObjectSchema.builder()
                    .description("Arguments for invokeSkill")
                    .addStringProperty("skillId", "Unique identifier of the skill to execute")
                    .addStringProperty(
                            "inputs", "Optional JSON-encoded representation of the skill inputs")
                    .required(List.of("skillId"))
                    .build())
            .build();

    public InvokeSkillTool(SkillRuntime skillRuntime) {
        this.skillRuntime = Objects.requireNonNull(skillRuntime, "skillRuntime");
    }

    public ToolSpecification specification() {
        return specification;
    }

    @Tool(name = TOOL_NAME, returnBehavior = ReturnBehavior.TO_LLM)
    public SkillRuntime.ExecutionResult invoke(
            @P("skillId") String skillId, @P(value = "inputs", required = false) Map<String, Object> inputs) {
        return skillRuntime.execute(skillId, inputs);
    }
}
