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
    String output = out.toString();

    assertThat(output)
        .contains("Attempt 1: plan -> act")
        .contains("Plan skills: brand-guidelines -> document-skills/pptx -> with-reference")
        .contains("Assistant draft: dry-run-plan")
        .contains("Tokens in/out/total: 0/0/0 (calls=2")
        .contains("Invoked skills: brand-guidelines, document-skills/pptx, with-reference")
        .contains("Act outputs:")
        .contains("with-reference: ");
    }
}
