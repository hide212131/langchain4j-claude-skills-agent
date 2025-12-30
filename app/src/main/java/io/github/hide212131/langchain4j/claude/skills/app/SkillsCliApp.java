package io.github.hide212131.langchain4j.claude.skills.app;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Skills CLI のルートコマンド。 */
@Command(name = "skills", description = "Skills エージェント CLI。", mixinStandardHelpOptions = true, subcommands = {
        RunCommand.class, SetupCommand.class })
public final class SkillsCliApp {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SkillsCliApp() {
        // for picocli
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SkillsCliApp()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        CommandLine cmd = new CommandLine(new SkillsCliApp());
        cmd.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));
        cmd.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));
        return cmd.execute(args);
    }
}
