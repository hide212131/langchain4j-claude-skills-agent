package io.github.hide212131.langchain4j.claude.skills.runtime.visibility;

import dev.langchain4j.model.chat.response.ChatResponse;
import java.lang.reflect.Method;

/** LangChain4j の ChatResponse からトークン使用量を抽出する。 */
public final class TokenUsageExtractor {

    private TokenUsageExtractor() {
    }

    public static TokenUsage from(ChatResponse response) {
        if (response == null) {
            return null;
        }
        Object rawUsage = invokeNoArg(response, "tokenUsage");
        if (rawUsage == null) {
            return null;
        }
        Long input = longOrNull(invokeNoArg(rawUsage, "inputTokenCount"));
        Long output = longOrNull(invokeNoArg(rawUsage, "outputTokenCount"));
        Long total = longOrNull(invokeNoArg(rawUsage, "totalTokenCount"));
        if (input == null && output == null && total == null) {
            input = longOrNull(invokeNoArg(rawUsage, "inputTokens"));
            output = longOrNull(invokeNoArg(rawUsage, "outputTokens"));
            total = longOrNull(invokeNoArg(rawUsage, "totalTokens"));
        }
        if (input == null && output == null && total == null) {
            return null;
        }
        return new TokenUsage(input, output, total);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Long longOrNull(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
