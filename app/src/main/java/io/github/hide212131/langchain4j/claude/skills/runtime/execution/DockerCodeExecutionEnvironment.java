package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import io.github.hide212131.langchain4j.claude.skills.bundle.SkillImageBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

public class DockerCodeExecutionEnvironment implements CodeExecutionEnvironment {

    private static final String DOCKER_COMMAND = "docker";

    private final DockerWorkspace workspace;
    private final String containerId;

    public DockerCodeExecutionEnvironment(String imageTag, Path skillMdPath) {
        this(imageTag, DockerWorkspace.createTemporary(), skillMdPath);
    }

    DockerCodeExecutionEnvironment(String imageTag, DockerWorkspace workspace, Path skillMdPath) {
        Objects.requireNonNull(imageTag, "imageTag");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(skillMdPath, "skillMdPath");
        if (!SkillImageBuilder.isDockerAvailable()) {
            throw new IllegalStateException("Docker が利用できません");
        }
        this.workspace = workspace;
        syncSkillWorkspace(skillMdPath);
        ensureImageAvailable(imageTag);
        this.containerId = startContainer(imageTag, workspace);
    }

    @Override
    @Agent(description = "Docker コンテナでコマンドを実行する", outputKey = "executionResult")
    public ExecutionResult executeCommand(@V("command") String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command は空にできません");
        }
        String resolvedCommand = "export NODE_PATH=\"$(npm root -g)\" " + "&& export PATH=\"$(npm bin -g):$PATH\" "
                + "&& " + command;
        List<String> dockerCommand = List.of(DOCKER_COMMAND, "exec", "-w", workspace.containerPath(), containerId, "sh",
                "-c", resolvedCommand);
        return DockerProcessRunner.run(dockerCommand, command);
    }

    private void syncSkillWorkspace(Path skillMdPath) {
        Path skillDir = skillMdPath.getParent();
        if (skillDir == null) {
            throw new IllegalArgumentException("SKILL.md の親ディレクトリを取得できません: " + skillMdPath);
        }
        Path workspaceRoot = workspace.workspaceRootPath();
        try (var paths = Files.walk(skillDir)) {
            paths.forEach(path -> {
                Path relative = skillDir.relativize(path);
                Path destination = workspaceRoot.resolve(relative.toString());
                try {
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                        return;
                    }
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    throw new IllegalStateException("スキル作業ディレクトリの同期に失敗しました", ex);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("スキル作業ディレクトリの同期に失敗しました", ex);
        }
    }

    @Override
    public String uploadFile(Path localPath) {
        return workspace.uploadFile(localPath);
    }

    @Override
    public byte[] downloadFile(String remotePath) {
        return workspace.downloadFile(remotePath);
    }

    @Override
    public List<String> listFiles(String pattern) {
        return workspace.listFiles(pattern);
    }

    @Override
    public void close() {
        IllegalStateException failure = null;
        try {
            stopContainer(containerId);
        } catch (IllegalStateException ex) {
            failure = ex;
        }
        try {
            workspace.deleteWorkspace();
        } catch (IllegalStateException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void ensureImageAvailable(String imageTag) {
        ExecutionResult result = DockerProcessRunner.run(List.of(DOCKER_COMMAND, "image", "inspect", imageTag),
                "docker image inspect");
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Docker イメージが見つかりません: " + imageTag);
        }
    }

    private static String startContainer(String imageTag, DockerWorkspace workspace) {
        List<String> command = List.of(DOCKER_COMMAND, "run", "-d", "--rm", "--network", "none", "-v",
                workspace.workspaceRootPath() + ":" + workspace.containerPath(), imageTag, "sleep", "infinity");
        ExecutionResult result = DockerProcessRunner.run(command, "docker run");
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            throw new IllegalStateException("Docker コンテナの起動に失敗しました: " + imageTag);
        }
        return result.stdout().trim();
    }

    private static void stopContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return;
        }
        ExecutionResult result = DockerProcessRunner.run(List.of(DOCKER_COMMAND, "stop", containerId), "docker stop");
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Docker コンテナの停止に失敗しました: " + containerId);
        }
    }

}
