package io.github.hide212131.langchain4j.claude.skills.bundle;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class SkillSourcesConfigLoader {

    private SkillSourcesConfigLoader() {
    }

    public static List<SkillSourceSpec> load(Path configPath) {
        Objects.requireNonNull(configPath, "configPath");
        if (!Files.exists(configPath)) {
            throw new IllegalArgumentException("skill-sources.yaml が見つかりません: " + configPath);
        }
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Object loaded = yaml.load(reader);
            if (!(loaded instanceof Map<?, ?> root)) {
                throw new IllegalArgumentException("skill-sources.yaml の形式が不正です。");
            }
            Object sources = root.get("sources");
            if (!(sources instanceof List<?> list)) {
                throw new IllegalArgumentException("sources が未定義、または配列ではありません。");
            }
            List<SkillSourceSpec> results = new ArrayList<>();
            for (Object entry : list) {
                if (!(entry instanceof String source)) {
                    throw new IllegalArgumentException("sources は文字列の配列である必要があります。");
                }
                results.add(SkillSourceSpec.parse(source));
            }
            return results;
        } catch (IOException ex) {
            throw new IllegalStateException("skill-sources.yaml の読み込みに失敗しました。", ex);
        }
    }
}
