package io.github.hide212131.langchain4j.claude.skills.runtime.execution;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandPolicyTest {

    @SuppressWarnings("PMD.UnnecessaryConstructor")
    CommandPolicyTest() {
        // default
    }

    @ParameterizedTest
    @ValueSource(strings = { "pip install requests", "python -m pip install requests", "npm install lodash",
            "yarn add lodash" })
    @DisplayName("pip/npm のインストールコマンドを拒否する")
    void rejectsInstallCommands(String command) {
        assertThatThrownBy(() -> CommandPolicy.validate(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "node scripts/html2pptx.js", "python scripts/thumbnail.py output.pptx" })
    @DisplayName("通常のコマンドは許可する")
    void allowsNormalCommands(String command) {
        assertThatCode(() -> CommandPolicy.validate(command)).doesNotThrowAnyException();
    }
}
