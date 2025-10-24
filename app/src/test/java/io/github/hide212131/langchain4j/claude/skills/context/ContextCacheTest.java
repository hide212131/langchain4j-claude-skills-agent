package io.github.hide212131.langchain4j.claude.skills.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextCacheTest {

    private final TokenCounter tokenCounter = String::length;
    private final ExcerptSummarizer summarizer = (docRef, content) -> "S:" + content.substring(0, Math.min(content.length(), 5));
    private ContextCache cache;

    @BeforeEach
    void setUp() {
        cache = new ContextCache(summarizer, tokenCounter);
    }

    @Test
    void cacheHitReturnsSummaryAndReducesTokens() {
        ContextExcerpt excerpt = new ContextExcerpt("brand-guidelines", "brand palette colors");

        CacheUpdateOutcome first = cache.putExcerpt("ctx-1", excerpt);
        ContextStatisticsSnapshot firstStats = cache.recordStats("ctx-1", first);

        assertThat(first.hit()).isFalse();
        assertThat(first.payloadType()).isEqualTo(CacheUpdateOutcome.PayloadType.FULL);
        assertThat(first.payload()).isEqualTo("brand palette colors");
        assertThat(first.tokensBefore()).isEqualTo(first.tokensAfter());
        assertThat(firstStats.tokensBefore()).isEqualTo(first.tokensBefore());
        assertThat(firstStats.tokensAfter()).isEqualTo(first.tokensAfter());
        assertThat(firstStats.hitRate()).isZero();

        CacheUpdateOutcome second = cache.putExcerpt("ctx-1", excerpt);
        ContextStatisticsSnapshot snapshot = cache.recordStats("ctx-1", second);

        assertThat(second.hit()).isTrue();
        assertThat(second.payloadType()).isEqualTo(CacheUpdateOutcome.PayloadType.SUMMARY);
        assertThat(second.payload()).isEqualTo("S:brand");
        assertThat(second.tokensAfter()).isLessThan(second.tokensBefore());

        assertThat(snapshot.tokensBefore()).isEqualTo(first.tokensBefore() + second.tokensBefore());
        assertThat(snapshot.tokensAfter()).isEqualTo(first.tokensAfter() + second.tokensAfter());
        assertThat(snapshot.hitRate()).isEqualTo(0.5d);

        Optional<CachedExcerpt> cached = cache.getExcerpt("ctx-1", "brand-guidelines");
        assertThat(cached)
                .contains(new CachedExcerpt("brand-guidelines", "brand palette colors", "S:brand"));
    }

    @Test
    void diffOnlyAddsNewContentWhenExcerptExpands() {
        ContextExcerpt initialExcerpt = new ContextExcerpt("brand-guidelines", "primary palette");
        CacheUpdateOutcome first = cache.putExcerpt("ctx-2", initialExcerpt);
        cache.recordStats("ctx-2", first);

        ContextExcerpt expandedExcerpt = new ContextExcerpt("brand-guidelines", "primary palette\nsecondary typography");
        CacheUpdateOutcome second = cache.putExcerpt("ctx-2", expandedExcerpt);
        ContextStatisticsSnapshot snapshot = cache.recordStats("ctx-2", second);

        assertThat(second.hit()).isTrue();
        assertThat(second.payloadType()).isEqualTo(CacheUpdateOutcome.PayloadType.DELTA);
        assertThat(second.payload()).isEqualTo("\nsecondary typography");
        assertThat(second.tokensAfter()).isEqualTo(tokenCounter.countTokens(second.payload()));
        assertThat(second.tokensAfter()).isLessThan(second.tokensBefore());

        assertThat(snapshot.hits()).isEqualTo(1);
        assertThat(snapshot.misses()).isEqualTo(1);
        assertThat(snapshot.hitRate()).isEqualTo(0.5d);
        assertThat(snapshot.tokensBefore()).isEqualTo(first.tokensBefore() + second.tokensBefore());
        assertThat(snapshot.tokensAfter()).isEqualTo(first.tokensAfter() + second.tokensAfter());
    }
}
