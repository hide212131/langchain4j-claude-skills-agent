package io.github.hide212131.langchain4j.claude.skills.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmConfigurationLoaderTest {

    @Test
    @DisplayName("環境変数が空ならデフォルトで mock を選択する")
    void defaultIsMockWhenNoEnv() {
        LlmConfigurationLoader loader =
                new LlmConfigurationLoader(Map.of(), Dotenv.configure().ignoreIfMissing().ignoreIfMalformed().load());

        LlmConfiguration config = loader.load();

        assertThat(config.provider()).isEqualTo(LlmProvider.MOCK);
        assertThat(config.openAiApiKey()).isNull();
        assertThat(config.openAiBaseUrl()).isNull();
        assertThat(config.openAiModel()).isNull();
    }

    @Test
    @DisplayName("LLM_PROVIDER=openai でキーがなければ例外を返す")
    void errorWhenOpenAiKeyMissing() {
        LlmConfigurationLoader loader = new LlmConfigurationLoader(
                Map.of(LlmConfigurationLoader.ENV_LLM_PROVIDER, "openai"),
                Dotenv.configure().ignoreIfMissing().ignoreIfMalformed().load());

        assertThatThrownBy(loader::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    @DisplayName("環境変数が優先され、存在しない場合のみ .env を読む")
    void preferEnvironmentOverDotenv() throws IOException {
        Path tempDir = Files.createTempDirectory("llm-config-test");
        Path envFile = tempDir.resolve(".env");
        Files.writeString(
                envFile,
                """
                LLM_PROVIDER=openai
                OPENAI_API_KEY=from-dotenv
                OPENAI_BASE_URL=https://api.example.com
                OPENAI_MODEL=gpt-dotenv
                """,
                StandardCharsets.UTF_8);

        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .directory(tempDir.toString())
                .load();

        LlmConfigurationLoader loader = new LlmConfigurationLoader(
                Map.of(
                        LlmConfigurationLoader.ENV_LLM_PROVIDER, "mock",
                        LlmConfigurationLoader.ENV_OPENAI_API_KEY, "from-env",
                        LlmConfigurationLoader.ENV_OPENAI_BASE_URL, "",
                        LlmConfigurationLoader.ENV_OPENAI_MODEL, "gpt-env"),
                dotenv);

        LlmConfiguration config = loader.load();

        assertThat(config.provider()).isEqualTo(LlmProvider.MOCK);
        assertThat(config.openAiApiKey()).isEqualTo("from-env");
        assertThat(config.openAiBaseUrl()).isNull();
        assertThat(config.openAiModel()).isEqualTo("gpt-env");
        Files.deleteIfExists(envFile);
        Files.deleteIfExists(tempDir);
    }
}
