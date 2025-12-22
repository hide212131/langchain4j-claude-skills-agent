package io.github.hide212131.langchain4j.claude.skills.runtime.langfuse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/** LangFuse API のエラー JSON を表す。 */
public record LangfuseApiError(String message) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static LangfuseApiError tryParse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode message = node.get("message");
            if (message != null && message.isTextual()) {
                return new LangfuseApiError(message.asText());
            }
            return null;
        } catch (IOException ignored) {
            return null;
        }
    }
}
