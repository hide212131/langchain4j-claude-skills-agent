package io.github.hide212131.langchain4j.claude.skills.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
        // Note: "Assistant draft" is no longer present as we removed the direct llmClient.complete() call
        .contains("Tokens in/out/total: 0/0/0 (calls=1")
        .contains("Invoked skills: brand-guidelines, document-skills/pptx, with-reference")
        .contains("Act outputs:")
        .contains("with-reference: ");
    }

    @Test
    void runCommandWithGoalFile(@TempDir Path tempDir) throws Exception {
        // Create a temporary goal file
        Path goalFile = tempDir.resolve("goal.txt");
        Files.writeString(goalFile, "demo goal from file");

        CommandLine commandLine = SkillsCliApp.commandLineInstance();
        StringWriter out = new StringWriter();
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute(
                "run",
                "--goal-file",
                goalFile.toString(),
                "--dry-run",
                "--skills-dir",
                "src/test/resources/test-skills");

        assertThat(exitCode).isZero();
        String output = out.toString();
        assertThat(output).contains("Attempt 1: plan -> act");
    }

    @Test
    void runCommandWithMissingGoalFile(@TempDir Path tempDir) {
        CommandLine commandLine = SkillsCliApp.commandLineInstance();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));

        int exitCode = commandLine.execute(
                "run",
                "--goal-file",
                tempDir.resolve("nonexistent.txt").toString(),
                "--dry-run");

        assertThat(exitCode).isNotZero();
        String errOutput = err.toString();
        assertThat(errOutput).contains("goal file not found");
    }

    @Test
    void runCommandWithEmptyGoalFile(@TempDir Path tempDir) throws Exception {
        Path goalFile = tempDir.resolve("empty.txt");
        Files.writeString(goalFile, "");

        CommandLine commandLine = SkillsCliApp.commandLineInstance();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));

        int exitCode = commandLine.execute(
                "run",
                "--goal-file",
                goalFile.toString(),
                "--dry-run");

        assertThat(exitCode).isNotZero();
        String errOutput = err.toString();
        assertThat(errOutput).contains("goal file is empty");
    }

    @Test
    void runCommandWithoutGoal() {
        CommandLine commandLine = SkillsCliApp.commandLineInstance();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));

        int exitCode = commandLine.execute(
                "run",
                "--dry-run");

        assertThat(exitCode).isNotZero();
        String errOutput = err.toString();
        assertThat(errOutput).contains("either --goal or --goal-file must be provided");
    }
}
