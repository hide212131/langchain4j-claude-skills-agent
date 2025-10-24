package io.github.hide212131.langchain4j.claude.skills.context;

import java.util.Objects;

public record CacheUpdateOutcome(
        String contextId,
        String docRef,
        boolean hit,
        PayloadType payloadType,
        String payload,
        int tokensBefore,
        int tokensAfter) {

    public CacheUpdateOutcome {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(docRef, "docRef");
        Objects.requireNonNull(payloadType, "payloadType");
        Objects.requireNonNull(payload, "payload");
        if (tokensBefore < 0) {
            throw new IllegalArgumentException("tokensBefore must be non-negative");
        }
        if (tokensAfter < 0) {
            throw new IllegalArgumentException("tokensAfter must be non-negative");
        }
    }

    public enum PayloadType {
        FULL,
        SUMMARY,
        DELTA
    }
}
