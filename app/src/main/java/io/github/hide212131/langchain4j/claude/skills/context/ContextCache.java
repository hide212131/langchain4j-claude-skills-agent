package io.github.hide212131.langchain4j.claude.skills.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ContextCache {

    private final ExcerptSummarizer summarizer;
    private final TokenCounter tokenCounter;
    private final Map<String, Map<String, ContextEntry>> contexts = new HashMap<>();
    private final Map<String, StatsAccumulator> statistics = new HashMap<>();

    public ContextCache(ExcerptSummarizer summarizer) {
        this(summarizer, new WhitespaceTokenCounter());
    }

    public ContextCache(ExcerptSummarizer summarizer, TokenCounter tokenCounter) {
        this.summarizer = Objects.requireNonNull(summarizer, "summarizer");
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter");
    }

    public CacheUpdateOutcome putExcerpt(String contextId, ContextExcerpt excerpt) {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(excerpt, "excerpt");

        Map<String, ContextEntry> docCache =
                contexts.computeIfAbsent(contextId, id -> new HashMap<>());

        ContextEntry existing = docCache.get(excerpt.docRef());
        String content = excerpt.content();
        int tokensBefore = tokenCounter.countTokens(content);

        if (existing == null) {
            String summary = requireSummary(excerpt.docRef(), content);
            docCache.put(excerpt.docRef(), new ContextEntry(content, summary));
            return new CacheUpdateOutcome(
                    contextId,
                    excerpt.docRef(),
                    false,
                    CacheUpdateOutcome.PayloadType.FULL,
                    content,
                    tokensBefore,
                    tokensBefore);
        }

        if (content.equals(existing.fullContent)) {
            int tokensAfter = tokenCounter.countTokens(existing.summary);
            return new CacheUpdateOutcome(
                    contextId,
                    excerpt.docRef(),
                    true,
                    CacheUpdateOutcome.PayloadType.SUMMARY,
                    existing.summary,
                    tokensBefore,
                    tokensAfter);
        }

        if (content.startsWith(existing.fullContent)) {
            String delta = content.substring(existing.fullContent.length());
            String summary = requireSummary(excerpt.docRef(), content);
            docCache.put(excerpt.docRef(), new ContextEntry(content, summary));
            int tokensAfter = tokenCounter.countTokens(delta);
            return new CacheUpdateOutcome(
                    contextId,
                    excerpt.docRef(),
                    true,
                    CacheUpdateOutcome.PayloadType.DELTA,
                    delta,
                    tokensBefore,
                    tokensAfter);
        }

        String summary = requireSummary(excerpt.docRef(), content);
        docCache.put(excerpt.docRef(), new ContextEntry(content, summary));
        int tokensAfter = tokenCounter.countTokens(summary);
        return new CacheUpdateOutcome(
                contextId,
                excerpt.docRef(),
                true,
                CacheUpdateOutcome.PayloadType.SUMMARY,
                summary,
                tokensBefore,
                tokensAfter);
    }

    public Optional<CachedExcerpt> getExcerpt(String contextId, String docRef) {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(docRef, "docRef");

        Map<String, ContextEntry> docCache = contexts.get(contextId);
        if (docCache == null) {
            return Optional.empty();
        }
        ContextEntry entry = docCache.get(docRef);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new CachedExcerpt(docRef, entry.fullContent, entry.summary));
    }

    public ContextStatisticsSnapshot recordStats(String contextId, CacheUpdateOutcome outcome) {
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(outcome, "outcome");

        StatsAccumulator accumulator =
                statistics.computeIfAbsent(contextId, id -> new StatsAccumulator());
        accumulator.record(outcome);
        return accumulator.snapshot(contextId);
    }

    private String requireSummary(String docRef, String content) {
        String summary = summarizer.summarize(docRef, content);
        return Objects.requireNonNullElse(summary, "");
    }

    private static final class ContextEntry {
        private final String fullContent;
        private final String summary;

        private ContextEntry(String fullContent, String summary) {
            this.fullContent = fullContent;
            this.summary = summary;
        }
    }

    private static final class StatsAccumulator {
        private int requests;
        private int hits;
        private int tokensBefore;
        private int tokensAfter;

        private void record(CacheUpdateOutcome outcome) {
            requests += 1;
            tokensBefore += outcome.tokensBefore();
            tokensAfter += outcome.tokensAfter();
            if (outcome.hit()) {
                hits += 1;
            }
        }

        private ContextStatisticsSnapshot snapshot(String contextId) {
            int misses = requests - hits;
            double hitRate = requests == 0 ? 0.0d : (double) hits / requests;
            return new ContextStatisticsSnapshot(
                    contextId, requests, hits, misses, tokensBefore, tokensAfter, hitRate);
        }
    }

    private static final class WhitespaceTokenCounter implements TokenCounter {

        @Override
        public int countTokens(String text) {
            if (text == null || text.isBlank()) {
                return 0;
            }
            return text.trim().split("\\s+").length;
        }
    }
}
