package io.github.hide212131.langchain4j.claude.skills.app.langfuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/**
 * 環境設定の確認とユーティリティ生成を行う簡単な接続テスト。
 * Note: These tests require LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY environment variables to be set.
 */
class LangfuseTraceUtilSimpleTest {

    @Test
    void testEnvironmentVariables() {
        String publicKey = System.getenv("LANGFUSE_PUBLIC_KEY");
        String secretKey = System.getenv("LANGFUSE_SECRET_KEY");

        System.out.println(
                "LANGFUSE_PUBLIC_KEY: "
                        + (publicKey != null ? "設定済み (長さ: " + publicKey.length() + ")" : "未設定"));
        System.out.println(
                "LANGFUSE_SECRET_KEY: "
                        + (secretKey != null ? "設定済み (長さ: " + secretKey.length() + ")" : "未設定"));
        System.out.println("LANGFUSE_BASE_URL: " + System.getenv("LANGFUSE_BASE_URL"));

        // Skip test if environment variables are not set
        assumeTrue(publicKey != null && secretKey != null,
                "Skipping test: LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY must be set");

        assertThat(publicKey).isNotNull();
        assertThat(secretKey).isNotNull();
    }

    @Test
    void testUtilityClassInstantiation() {
        String publicKey = System.getenv("LANGFUSE_PUBLIC_KEY");
        String secretKey = System.getenv("LANGFUSE_SECRET_KEY");
        String baseUrl = "http://localhost:3000";

        // Skip test if environment variables are not set
        assumeTrue(publicKey != null && secretKey != null,
                "Skipping test: LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY must be set");

        LangfuseTraceUtil util = new LangfuseTraceUtil(baseUrl, publicKey, secretKey);
        assertThat(util).isNotNull();

        System.out.println("LangfuseTraceUtilインスタンスの作成に成功しました");
    }

    @Test
    void testFromEnvironmentMethod() {
        // Skip test if environment variables are not set
        String publicKey = System.getenv("LANGFUSE_PUBLIC_KEY");
        String secretKey = System.getenv("LANGFUSE_SECRET_KEY");
        assumeTrue(publicKey != null && secretKey != null,
                "Skipping test: LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY must be set");

        LangfuseTraceUtil util = LangfuseTraceUtil.fromEnvironment();
        assertThat(util).isNotNull();

        System.out.println("fromEnvironment()でのインスタンス作成に成功しました");
    }
}
