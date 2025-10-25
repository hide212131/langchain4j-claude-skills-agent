package io.github.hide212131.langchain4j.claude.skills.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class SkillsCliAppTest {

    @Test
    void runCommandEmitsStageSequence() {
        CommandLine commandLine = SkillsCliApp.commandLineInstance();
        StringWriter out = new StringWriter();
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute(
                "run",
                "--goal",
                "demo",
                "--dry-run",
                "--skills-dir",
                "src/test/resources/test-skills");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("Stage: plan")
                .contains("Stage: act")
                .contains("Stage: reflect")
                .contains("Plan:")
                .contains("brand-guidelines")
                .contains("Assistant: dry-run-plan")
                .contains("Tokens in/out/total:")
                .contains("calls=1")
                .contains("Skills: brand-guidelines, document-skills/pptx")
                .contains("Artifact: ");
    }
}
