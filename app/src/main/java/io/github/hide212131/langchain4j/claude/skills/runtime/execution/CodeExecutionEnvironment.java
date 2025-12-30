package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import java.nio.file.Path;
import java.util.List;

public interface CodeExecutionEnvironment extends AutoCloseable {

    ExecutionResult executeCommand(String command);

    String uploadFile(Path localPath);

    byte[] downloadFile(String remotePath);

    List<String> listFiles(String pattern);

    @Override
    void close();
}
