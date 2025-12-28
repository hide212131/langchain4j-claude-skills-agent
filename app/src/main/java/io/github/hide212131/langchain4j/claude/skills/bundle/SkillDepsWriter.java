package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public final class SkillDepsWriter {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    public SkillDepsWriter() {
    }

    public void write(SkillDependency dependency, Path outputPath) {
        Objects.requireNonNull(dependency, "dependency");
        Objects.requireNonNull(outputPath, "outputPath");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("commands", dependency.commands());
        if (!dependency.warnings().isEmpty()) {
            root.put("warnings", dependency.warnings());
        }
        writeYaml(outputPath, root);
    }

    private void writeYaml(Path outputPath, Map<String, Object> content) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String dumped = yaml.dump(content);
            Files.writeString(outputPath, dumped, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("skill-deps.yaml の書き込みに失敗しました: " + outputPath, ex);
        }
    }
}
