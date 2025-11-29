# ADR-lq67e プロンプト改善メトリクスの定義と測定

## Metadata

- Type: ADR
- Status: Approved

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)
- Related ADRs:
  - [ADR-3 Context Engineering 実装方針](ADR-mpiub-context-engineering-strategy.md)
  - [ADR-8 Observability 基盤の統合戦略](ADR-ij1ew-observability-integration.md)
- Impacted Requirements:
  - NFR-DRAFT-6（漸進的開発・評価サイクル）

## Context

プロンプト改善の効果を定量的に測定するため、以下のメトリクスを定義・測定する必要がある：

1. **精度関連メトリクス**：エージェント・スキル実行の正確性（Accuracy、F1 スコアなど）
2. **効率関連メトリクス**：トークン消費・コスト（Input Token、Output Token、推定 API コスト）
3. **性能関連メトリクス**：実行速度（End-to-End Latency、LLM 応答時間、スキル実行時間）
4. **信頼性関連メトリクス**：エラー率・リトライ率・成功率
5. **コスト関連メトリクス**：API 利用コスト・リソース消費コスト

**制約・仮定**：

- Phase 1：メトリクス定義・基本的な測定
- Phase 2：A/B テスト・多変量テストフレームワーク
- 業務ドメイン固有の精度メトリクス（タスク・ユースケース毎）

なお、本 ADR で採用する 5 つのメトリクスカテゴリ
（精度・効率・性能・信頼性・コスト）は、LLM / AI エージェント評価や
LLMOps 領域で広く用いられている標準的な評価軸
（quality/accuracy, latency/performance, reliability, cost/efficiency など）と
対応させている。

## Success Metrics

- メトリック 1：`5 つのメトリクスカテゴリ（精度・効率・性能・信頼性・コスト）が定義され、自動測定される`
- メトリック 2：`複数プロンプト・パラメータの効果を定量的に比較可能である`
- メトリック 3：`メトリクス改善の効果が可視化され、ダッシュボードで監視可能である`

## Decision

### Considered Options

- **Option A: カテゴリ別メトリクス（分類による体系化）**
  - 5 つのカテゴリで 15～20 個のメトリクスを定義
  - 各カテゴリで統一的に測定・分析

- **Option B: 統合スコア（複数メトリクスを 1 つのスコアに統合）**
  - Pareto Front や加重平均で、複数メトリクスを単一スコアに圧縮
  - シンプルな比較が可能だが、個別メトリクスの洞察が失われる

- **Option C: ハイブリッド：カテゴリ別 + 統合スコア**
  - カテゴリ別メトリクスで詳細分析
  - 統合スコアで全体的な改善を判定

### Option Analysis

| 観点                     | カテゴリ別 | 統合スコア                   | ハイブリッド |
| ------------------------ | ---------- | ---------------------------- | ------------ |
| **詳細分析の容易性**     | 最高       | 低い                         | 最高         |
| **比較の シンプルさ**    | 中程度     | 最高                         | 中程度       |
| **実装複雑度**           | 中程度     | 低い                         | 中程度       |
| **メトリクス改善の洞察** | 最高       | 低い（トレードオフ見え難い） | 最高         |

## Rationale

**ハイブリッド：カテゴリ別メトリクス + 統合スコア**を採用する。

理由：

1. **詳細な改善分析が可能**
   - カテゴリ別で各メトリクスの変動を追跡
   - 精度向上がトークン消費増加につながった場合など、トレードオフが可視化

2. **シンプルな全体比較**
   - 統合スコアで、複数プロンプト・パラメータの「総合的な最適性」を判定
   - Phase 2 での A/B テストで有用

3. **段階的な最適化**
   - 初期実装：カテゴリ別メトリクスで詳細分析
   - Phase 2：統合スコアに基づく自動最適化

## Consequences

### Positive

- メトリクス改善の詳細な原因分析が可能
- トレードオフ（精度 vs コスト など）が明確に可視化
- Phase 2 での A/B テスト・自動最適化への拡張が容易

### Negative

- メトリクス定義・測定・分析の複雑度が増加
- ダッシュボード設計・表示項目の集約が必要

### Neutral

- Phase 2 での A/B テスト導入時、メトリクス統合スコア算出アルゴリズムの改善が可能
  - リスク軽減策：統合スコア計算式をパラメータ化し、実験的に調整可能な設計

## Implementation Notes

### メトリクス定義（5 カテゴリ）

#### 1. 精度関連メトリクス

```java
record AccuracyMetrics(
    double accuracyScore,           // 0.0 - 1.0（スキル実行結果が期待通りか）
    double f1Score,                 // F1 スコア（精度・再現率のバランス）
    double precisionScore,          // 精度（TP / (TP + FP)）
    double recallScore,             // 再現率（TP / (TP + FN)）
    int correctExecutions,          // 成功したスキル実行数
    int totalExecutions             // 合計スキル実行数
) {
    public double successRate() {
        return totalExecutions > 0 ? (double) correctExecutions / totalExecutions : 0.0;
    }
}
```

**測定方法**：

- スキル実行後、出力結果をテストケースと比較（精度判定ロジックはタスク固有）
- 手動検証（初期段階）→ 自動化（テストケース充実後）

#### 2. 効率関連メトリクス

```java
record EfficiencyMetrics(
    int inputTokens,                // 入力トークン数
    int outputTokens,               // 出力トークン数
    int totalTokens,                // 合計トークン数
    double estimatedCostUsd,        // 推定 API コスト（USD）
    double tokensPerDollar,         // コスト効率（トークン/ドル）
    double contextEfficiency        // Context Engineering による削減率（0.0 - 1.0）
) {
    public double cost() {
        return estimatedCostUsd;
    }
}
```

**測定方法**：

- LLM API の TokenUsage から自動取得
- API レート：OpenAI gpt-4o の場合、入力 $0.015/1M、出力 $0.06/1M

#### 3. 性能関連メトリクス

```java
record PerformanceMetrics(
    long endToEndLatencyMs,         // エンドツーエンド実行時間
    long llmLatencyMs,              // LLM API 応答時間
    long skillExecutionTimeMs,      // スキル実行時間
    double throughputRequestsPerSec // スループット（リクエスト/秒）
) {}
```

**測定方法**：

- System.currentTimeMillis() で各フェーズのタイミングを計測
- 複数実行の平均値・パーセンタイル（p50、p95、p99）を計算

#### 4. 信頼性関連メトリクス

```java
record ReliabilityMetrics(
    double successRate,             // スキル実行成功率
    double errorRate,               // エラー率
    int retryCount,                 // リトライ回数
    double retrySuccessRate,        // リトライで成功した率
    int totalErrors,                // 累計エラー数
    Map<String, Integer> errorTypes // エラー種別ごとのカウント
) {}
```

**測定方法**：

- スキル実行結果（success / error）を自動ログ
- エラー種別（timeout、resource exhaustion、api error など）を分類

#### 5. コスト関連メトリクス

```java
record CostMetrics(
    double apiCostUsd,              // LLM API コスト
    double computeCostUsd,          // サーバサイド計算コスト（概算）
    double totalCostUsd,            // 合計コスト
    double costPerRequest,          // リクエスト当たりのコスト
    double costPerSuccessfulRequest // 成功したリクエストのみのコスト
) {}
```

**測定方法**：

- API コスト：トークン消費量 × レート
- 計算コスト：実行時間 × サーバリソース単価（概算）

### 統合スコア計算

```java
public class MetricsAggregator {
    // 各カテゴリの重み（カスタマイズ可能）
    private static final double ACCURACY_WEIGHT = 0.30;
    private static final double EFFICIENCY_WEIGHT = 0.25;
    private static final double PERFORMANCE_WEIGHT = 0.20;
    private static final double RELIABILITY_WEIGHT = 0.20;
    private static final double COST_WEIGHT = 0.05;

    public ComprehensiveMetrics aggregate(
            AccuracyMetrics accuracy,
            EfficiencyMetrics efficiency,
            PerformanceMetrics performance,
            ReliabilityMetrics reliability,
            CostMetrics cost) {

        // 各カテゴリスコアを計算（0.0 - 1.0 に正規化）
        double accuracyScore = normalizeAccuracy(accuracy.accuracyScore());
        double efficiencyScore = normalizeEfficiency(efficiency);
        double performanceScore = normalizePerformance(performance);
        double reliabilityScore = normalizeReliability(reliability.successRate());
        double costScore = normalizeCost(cost.costPerRequest());

        // 統合スコア = 加重平均
        double comprehensiveScore =
            accuracyScore * ACCURACY_WEIGHT +
            efficiencyScore * EFFICIENCY_WEIGHT +
            performanceScore * PERFORMANCE_WEIGHT +
            reliabilityScore * RELIABILITY_WEIGHT +
            costScore * COST_WEIGHT;

        return new ComprehensiveMetrics(
            comprehensiveScore,
            Map.of(
                "accuracy", accuracyScore,
                "efficiency", efficiencyScore,
                "performance", performanceScore,
                "reliability", reliabilityScore,
                "cost", costScore
            ),
            accuracy,
            efficiency,
            performance,
            reliability,
            cost
        );
    }

    private double normalizeEfficiency(EfficiencyMetrics metrics) {
        // トークン効率の逆数（少ないほど高スコア）
        // 目標：100 トークン/ドル以上
        double targetTokensPerDollar = 100.0;
        return Math.min(metrics.tokensPerDollar() / targetTokensPerDollar, 1.0);
    }

    private double normalizePerformance(PerformanceMetrics metrics) {
        // 実行時間の逆数（短いほど高スコア）
        // 目標：2 秒以下
        long targetLatencyMs = 2000;
        return Math.max(1.0 - ((double) metrics.endToEndLatencyMs() / targetLatencyMs), 0.0);
    }

    // その他の正規化関数...
}

record ComprehensiveMetrics(
    double comprehensiveScore,      // 統合スコア（0.0 - 1.0）
    Map<String, Double> categoryScores,  // カテゴリ別スコア
    AccuracyMetrics accuracy,
    EfficiencyMetrics efficiency,
    PerformanceMetrics performance,
    ReliabilityMetrics reliability,
    CostMetrics cost
) {}
```

### メトリクス計測フレームワーク

```java
public class MetricsFramework {
    private final MetricsCollector collector;

    public ComprehensiveMetrics measureExecution(
            Workflow workflow,
            SkillRequest request,
            SkillResponse response,
            long executionTimeMs,
            TokenUsage tokenUsage) {

        // 各カテゴリのメトリクスを計算
        AccuracyMetrics accuracy = calculateAccuracy(response);
        EfficiencyMetrics efficiency = calculateEfficiency(tokenUsage);
        PerformanceMetrics performance = calculatePerformance(executionTimeMs, tokenUsage);
        ReliabilityMetrics reliability = calculateReliability(response);
        CostMetrics cost = calculateCost(tokenUsage, executionTimeMs);

        // 統合スコアを計算
        MetricsAggregator aggregator = new MetricsAggregator();
        ComprehensiveMetrics comprehensive = aggregator.aggregate(
            accuracy, efficiency, performance, reliability, cost
        );

        // Observability へ発行
        collector.recordMetrics(comprehensive);

        return comprehensive;
    }
}
```

### ダッシュボード表示例

```
=== プロンプト改善メトリクス ===

統合スコア: 0.78 / 1.0

カテゴリ別スコア:
  ✓ 精度（Accuracy）：0.85 / 1.0
  ✓ 効率（Efficiency）：0.72 / 1.0  ← 改善対象
  ✓ 性能（Performance）：0.75 / 1.0
  ✓ 信頼性（Reliability）：0.90 / 1.0
  ✓ コスト（Cost）：0.68 / 1.0  ← 改善対象

詳細:
  精度：
    - 正解率：85.0%
    - F1 スコア：0.84
  効率：
    - 入力トークン：1,250
    - 出力トークン：450
    - 推定コスト：$0.0198
  性能：
    - 実行時間：1.5 秒
    - LLM 応答時間：1.2 秒
  信頼性：
    - 成功率：90.0%
    - エラー率：10.0%
  コスト：
    - リクエスト当たり：$0.0198
```

## Platform Considerations

- **精度判定の自動化**：タスク固有の判定ロジック実装が必須（ビジネス要件に応じてカスタマイズ）

## Security & Privacy

- **メトリクスデータの保護**：個人情報・機密情報が含まれていないことを確認

## Monitoring & Logging

- **メトリクス監視**：定期的にメトリクス値を確認、改善傾向を分析
- **ダッシュボード**：LangFuse・Application Insights でのリアルタイム可視化

## Open Questions

- [ ] **業務ドメイン固有の精度メトリクス定義**
      → Method: ユースケース毎に詳細な判定基準を設計（PoC-1・2 で検証）

- [ ] **統合スコアの重み付け最適化（Phase 2）**
      → Method: A/B テスト結果に基づく重み調整

- [ ] **目標値（Normalization の基準値）の設定**
      → Method: PoC での実測結果に基づい目標値を設定、定期的に見直し

## External References

- [Metrics Aggregation in LLM Systems](https://arxiv.org/abs/2305.13626)
- [A/B Testing Best Practices](https://en.wikipedia.org/wiki/A/B_testing)
