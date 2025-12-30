package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import io.github.hide212131.langchain4j.claude.skills.bundle.SkillImageTagGenerator;
import java.nio.file.Path;
import java.util.Objects;

public final class CodeExecutionEnvironmentFactory {

    private final ExecutionBackend backend;

    public CodeExecutionEnvironmentFactory(ExecutionBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public CodeExecutionEnvironment create(Path skillMdPath) {
        Objects.requireNonNull(skillMdPath, "skillMdPath");
        return switch (backend) {
        case DOCKER -> createDockerEnvironment(skillMdPath);
        case ACADS -> throw new UnsupportedOperationException("ACADS 実装は Phase3 で対応予定です");
        };
    }

    private CodeExecutionEnvironment createDockerEnvironment(Path skillMdPath) {
        String skillPath = SkillPathResolver.resolveSkillPath(skillMdPath);
        String imageTag = SkillImageTagGenerator.generateImageTag(skillPath);
        return new DockerCodeExecutionEnvironment(imageTag, skillMdPath);
    }
}
