package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LangfuseApiErrorTest {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    LangfuseApiErrorTest() {
        // default
    }

    @Test
    @DisplayName("LangFuse API のエラー JSON から message を抽出できる")
    @SuppressWarnings("PMD.JUnitTestContainsTooManyAsserts")
    void parseUnauthorizedError() {
        // JSON response obtained from: curl -sS -L -H 'Accept: application/json'
        // https://cloud.langfuse.com/api/public/traces
        String json = """
                {"message":"No authorization header"}
                """;

        LangfuseApiError error = LangfuseApiError.tryParse(json);

        assertThat(error).isNotNull();
        assertThat(error.message()).contains("authorization");
    }
}
