package io.github.hide212131.langchain4j.claude.skills.app;

import io.github.hide212131.langchain4j.claude.skills.bundle.DockerfileGeneratorCli;
import io.github.hide212131.langchain4j.claude.skills.bundle.SkillDepsGeneratorCli;
import io.github.hide212131.langchain4j.claude.skills.bundle.SkillDownloaderCli;
import io.github.hide212131.langchain4j.claude.skills.bundle.SkillImageBuilderCli;
import picocli.CommandLine.Command;

/**
 * setup サブコマンド群の親コマンド.
 */
@Command(name = "setup", description = "スキル環境のセットアップを行います。", mixinStandardHelpOptions = true, subcommands = {
        SkillDownloaderCli.class, SkillDepsGeneratorCli.class, DockerfileGeneratorCli.class,
        SkillImageBuilderCli.class })
public final class SetupCommand {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SetupCommand() {
        // for picocli
    }
}
