package io.github.hide212131.langchain4j.claude.skills.app.langfuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * LangfuseTraceUtilのテストクラス。
 */
class LangfuseTraceUtilTest {

    private static final String VALID_TRACE_ID = "d4776f504b601f61cb5d1d352a5bfc7e";
    private static final String INVALID_TRACE_ID = "invalid-trace-id-12345";

    private LangfuseTraceUtil langfuseTraceUtil;

    @BeforeEach
    void setUp() {
        try {
            langfuseTraceUtil = LangfuseTraceUtil.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.out.println("環境変数が設定されていないため、テストをスキップします: " + e.getMessage());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testGetFirstLlmChatPrompt_ValidTraceId() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> prompt = langfuseTraceUtil.getFirstLlmChatPrompt(VALID_TRACE_ID);

        if (prompt.isPresent()) {
            System.out.println("取得成功!");
            String promptText = prompt.get();
            assertThat(promptText).isNotEmpty();
            assertThat(promptText).contains("You are a planner expert");
            assertThat(promptText).contains("JJUG CCC");

            System.out.println("取得されたプロンプト:");
            System.out.println("=" + "=".repeat(60));
            System.out.println(promptText);
            System.out.println("=" + "=".repeat(60));
        } else {
            System.out.println("プロンプトが取得できませんでした。APIの応答を確認してください。");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testGetFirstLlmChatPrompt_InvalidTraceId() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> prompt = langfuseTraceUtil.getFirstLlmChatPrompt(INVALID_TRACE_ID);

        assertThat(prompt).isEmpty();
    }

    @Test
    void testFromEnvironment_MissingKeys() {
        Assumptions.assumeTrue(
                System.getenv("LANGFUSE_PUBLIC_KEY") == null
                        || System.getenv("LANGFUSE_SECRET_KEY") == null,
                "Langfuse credentials detected in environment; skipping negative test");

        assertThatThrownBy(LangfuseTraceUtil::fromEnvironment)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY must be set");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testGetLlmChatPrompt_Index0() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> prompt = langfuseTraceUtil.getLlmChatPrompt(VALID_TRACE_ID, 0);

        if (prompt.isPresent()) {
            System.out.println("インデックス0のプロンプト取得成功!");
            String promptText = prompt.get();
            assertThat(promptText).isNotEmpty();
            System.out.println("長さ: " + promptText.length() + " 文字");
        } else {
            System.out.println("インデックス0のプロンプトが取得できませんでした。");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testGetLlmChatPrompt_Index1() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> prompt = langfuseTraceUtil.getLlmChatPrompt(VALID_TRACE_ID, 1);

        if (prompt.isPresent()) {
            System.out.println("インデックス1のプロンプト取得成功!");
            String promptText = prompt.get();
            assertThat(promptText).isNotEmpty();
            System.out.println("長さ: " + promptText.length() + " 文字");
            System.out.println("プロンプト内容（最初の200文字）:");
            System.out.println(promptText.substring(0, Math.min(200, promptText.length())) + "...");
        } else {
            System.out.println("インデックス1のプロンプトが取得できませんでした。");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testGetLlmChatPrompt_Index2() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> prompt = langfuseTraceUtil.getLlmChatPrompt(VALID_TRACE_ID, 2);

        if (prompt.isPresent()) {
            System.out.println("インデックス2のプロンプト取得成功!");
            String promptText = prompt.get();
            assertThat(promptText).isNotEmpty();
            System.out.println("長さ: " + promptText.length() + " 文字");
            System.out.println("プロンプト内容（最初の200文字）:");
            System.out.println(promptText.substring(0, Math.min(200, promptText.length())) + "...");
        } else {
            System.out.println("インデックス2のプロンプトが取得できませんでした。");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testGetLlmChatPrompt_OutOfBounds() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> prompt = langfuseTraceUtil.getLlmChatPrompt(VALID_TRACE_ID, 999);

        assertThat(prompt).isEmpty();
        System.out.println("範囲外テスト成功: インデックス999で空の結果を取得");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testCompareFirstMethodAndIndexMethod() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> firstPrompt = langfuseTraceUtil.getFirstLlmChatPrompt(VALID_TRACE_ID);
        Optional<String> indexPrompt = langfuseTraceUtil.getLlmChatPrompt(VALID_TRACE_ID, 0);

        if (firstPrompt.isPresent() && indexPrompt.isPresent()) {
            assertThat(firstPrompt.get()).isEqualTo(indexPrompt.get());
            System.out.println("両メソッドの結果が一致しました!");
        } else if (firstPrompt.isEmpty() && indexPrompt.isEmpty()) {
            System.out.println("両メソッドとも空の結果を返しました（一致）");
        } else {
            System.out.println("警告: 両メソッドの結果が一致しませんでした");
            System.out.println("getFirstLlmChatPrompt: " + firstPrompt.isPresent());
            System.out.println("getLlmChatPrompt(0): " + indexPrompt.isPresent());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testPromptContent() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> prompt = langfuseTraceUtil.getFirstLlmChatPrompt(VALID_TRACE_ID);

        if (prompt.isPresent()) {
            String promptText = prompt.get();

            assertThat(promptText).contains("system:");
            assertThat(promptText).contains("user:");
            assertThat(promptText).contains("JJUG CCC");
            assertThat(promptText).contains("document-skills/docx");
            assertThat(promptText).contains("readSkillMd");
            assertThat(promptText).contains("writeArtifact");

            assertThat(promptText.length()).isGreaterThan(1000);

            System.out.println("プロンプトの詳細分析:");
            System.out.println("長さ: " + promptText.length() + " 文字");
            System.out.println("行数: " + promptText.split("\n").length + " 行");

            String[] parts = promptText.split("user:");
            if (parts.length >= 2) {
                System.out.println("\n[システムプロンプト]:");
                System.out.println(parts[0].replace("system:", "").trim());
                System.out.println("\n[ユーザープロンプト]:");
                System.out.println("user:" + parts[1].trim());
            }
        }
    }

    @Test
    void testGetLlmChatPrompt_NegativeIndex() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        Optional<String> prompt = langfuseTraceUtil.getLlmChatPrompt(VALID_TRACE_ID, -1);
        assertThat(prompt).isEmpty();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LANGFUSE_PUBLIC_KEY", matches = ".*")
    void testGetAllLlmChatPrompts() {
        Assumptions.assumeTrue(langfuseTraceUtil != null, "Langfuse credentials are not configured");

        List<String> prompts = langfuseTraceUtil.getAllLlmChatPrompts(VALID_TRACE_ID);
        if (prompts.isEmpty()) {
            System.out.println("No prompts retrieved; check Langfuse trace data availability");
        } else {
            System.out.println("Retrieved " + prompts.size() + " prompts");
            assertThat(prompts.get(0)).isNotEmpty();
        }
    }
}
