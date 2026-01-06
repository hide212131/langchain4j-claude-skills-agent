package io.github.hide212131.langchain4j.claude.skills.runtime.planning;

import dev.langchain4j.model.output.structured.Description;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * 実行タスクの出力情報。
 */
public record ExecutionTaskOutput(@Description("出力種別。text/stdout/file/directory/none のいずれか。") OutputType type,
        @Description("ファイル出力の場合のパス。該当しない場合は空文字列。") String path, @Description("出力内容の説明。空で問題ない。") String description) {

    public ExecutionTaskOutput(OutputType type, String path, String description) {
        this.type = type == null ? OutputType.NONE : type;
        this.path = path == null ? "" : path;
        this.description = description == null ? "" : description;
    }

    public enum OutputType {
        TEXT, STDOUT, FILE, DIRECTORY, NONE;

        @JsonCreator
        public static OutputType fromString(String value) {
            if (value == null || value.isBlank()) {
                return NONE;
            }
            try {
                return OutputType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "output.type は text/stdout/file/directory/none のいずれかにしてください: " + value, ex);
            }
        }

    }
}
