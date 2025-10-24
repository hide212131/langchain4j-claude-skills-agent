package io.github.hide212131.langchain4j.claude.skills.context;

import java.util.Objects;

public record ContextStatisticsSnapshot(
        String contextId,
        int requests,
        int hits,
        int misses,
        int tokensBefore,
        int tokensAfter,
        double hitRate) {

    public ContextStatisticsSnapshot {
        Objects.requireNonNull(contextId, "contextId");
        if (requests < 0 || hits < 0 || misses < 0) {
            throw new IllegalArgumentException("request counts must be non-negative");
        }
        if (hits > requests) {
            throw new IllegalArgumentException("hits cannot exceed requests");
        }
        if (tokensBefore < 0 || tokensAfter < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
        if (Double.isNaN(hitRate) || Double.isInfinite(hitRate)) {
            throw new IllegalArgumentException("hitRate must be a finite number");
        }
    }
}
