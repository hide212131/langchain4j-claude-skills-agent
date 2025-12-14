# ADR-ae6nw AgenticScope の活用シナリオ

本 ADR は Workflow 型だけでなく、単一スキルを Pure Agent（Supervisor/SubAgent）として実装する場合の AgenticScope 共有も対象とし、Phase 1 からの適用方針を整理する。

## Metadata

- Type: ADR
- Status: Approved

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)

- Related ADRs:
  - [ADR-1 Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md)
  - [ADR-3 Context Engineering 実装方針](ADR-mpiub-context-engineering-strategy.md)
  - [ADR-4 スキル実行エンジン設計](ADR-ehfcj-skill-execution-engine.md)

- Impacted Requirements:
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
  - [FR-uu07e Progressive Disclosure 実装](../requirements/FR-uu07e-progressive-disclosure.md)
  - [NFR-kc6k1 Context Engineering 最適化](../requirements/NFR-kc6k1-context-engineering-optimization.md)
  - [NFR-30zem Observability 統合](../requirements/NFR-30zem-observability-integration.md)

## Context

LangChain4j の `AgenticScope` は、エージェント間の情報共有（メモリ、コンテキスト、実行履歴）を管理するメカニズム。本プロジェクトでは以下の活用が必要：

1. **単一 Workflow 内でのコンテキスト共有**：複数スキル実行時、前のステップの結果を次のステップで利用
2. **複数エージェント間での情報共有**：単一スキルでも Supervisor/SubAgent を用いる Pure Agent（Phase 1 から採用）での AgenticScope 共有と、Phase 2 の複数エージェント連携に備えた階層化
3. **Progressive Disclosure との連携**：各段階のコンテキスト（Level 1/2/3）を AgenticScope で管理
4. **Observability データの集約**：実行履歴・メトリクス・プロンプト内容を AgenticScope に記録するが、LangChain4j の自動記録に加えて不要なログを積極的に書き込まないよう制御し、Pure Agent のコンテキストを膨らませないようにする

**制約・仮定**：

- Phase 1：単一 Workflow もしくは単一スキルの Pure Agent（Supervisor/SubAgent 1 階層）で共通 AgenticScope を共有
- Phase 2：複数 AgenticScope・テナント間隔離・Supervisor/SubAgent の多層化を検討（Phase 1 実装を拡張）

## Success Metrics

- メトリック 1：`単一 Workflow 内での複数スキル連鎖実行で、AgenticScope を通じた前のステップ結果の参照が正常に動作する`
- メトリック 2：`AgenticScope にログされた実行履歴が、Observability フレームワークで完全に可視化される`
- メトリック 3：`Progressive Disclosure の各段階でコンテキストが AgenticScope で正しく管理される`

## Decision

**Phase 1 は Option B（単一 AgenticScope 共有）、Phase 2 は Option C（階層型 AgenticScope）に移行する。**

### Considered Options

- **Option A: スコープレス（各ステップで独立した context）**
  - AgenticScope を使用せず、Workflow の各ステップが独立した context を管理
  - シンプルだが、スキル間のデータ共有が困難・明示的な受け渡しが必須

- **Option B: 単一 AgenticScope（全ステップで共有）**
  - 単一 AgenticScope をすべてのステップで共有
  - スキル間データ共有が容易だが、scope の管理・cleanup が必要

- **Option C: 階層型 AgenticScope（レイヤー別・複数スコープ）**
  - 高レベル（Supervisor）と中レベル（SubAgent）で異なるスコープを管理
  - Phase 1 からの単一スキル Pure Agent 運用を踏まえ、Phase 2 の複数エージェント連携に対応できるよう設計

### Option Analysis

| 観点                           | スコープレス              | 単一 AgenticScope        | 階層型 AgenticScope         |
| ------------------------------ | ------------------------- | ------------------------ | --------------------------- |
| **スキル間データ共有の容易性** | 低い（手動で受け渡し）    | 高い（scope から参照）   | 最高（階層的な参照）        |
| **Scope 管理の複雑度**         | 低い                      | 中程度                   | 高い                        |
| **Phase 2 への拡張性**         | 低い                      | 中程度（修正必須）       | 最高（拡張可能）            |
| **実装複雑度（Phase 1）**      | 低い                      | 低い                     | 中程度                      |
| **パフォーマンス**             | 高い（scope コスト なし） | 中程度（scope アクセス） | 低い（複数 scope アクセス） |

### 階層化イメージ

**Phase 1（単一スコープ）**：Workflow もしくは単一スキル Pure Agent が 1 つの AgenticScope を共有し、スキル呼び出しごとにキーで区分けする。

```text
[Client/Input]
      |
 [AgenticScope (単一)]
      | keys: workflow.step1.*, workflow.step2.* / pure.sub1.*, pure.sub2.*
  (Workflow steps) または (Supervisor → SubAgent)
      |
   [Outputs]
```

**Phase 2（階層スコープ）**：Supervisor ごとに上位スコープを持ち、SubAgent は必要に応じて局所スコープを作成。共有したい最小データのみ親へ昇格させ、不要なログは子スコープ内に留める。

```text
[Client/Input]
      |
 [AgenticScope (Supervisor 上位)]
      | keys: supervisor.plan.*, shared.context.*
      +--> SubAgent A 用 Scope (局所)
      |         | keys: subA.*
      |         +-- 必要データのみ親へコピー/参照
      +--> SubAgent B 用 Scope (局所)
                | keys: subB.*
                +-- 必要データのみ親へコピー/参照
      |
   [Aggregated Outputs / Logs]
```

運用指針：

- **共有は最小限に昇格**：SubAgent で生成した情報は、親で再利用するものだけをコピーし、その他は子スコープに留める。
- **キー命名で衝突回避**：`supervisor.*` / `subA.*` / `workflow.stepN.*` のようにプレフィックスを固定し、Progressive Disclosure のレベルごとに `metadata.*` / `content.*` / `resources.*` を併用する。
- **Observability の抑制**：上位スコープはサマリのみ、詳細ログは子スコープ側で保持し、不要なログが親スコープに自動昇格しないようにする。

## Rationale

**Phase 1 では単一 AgenticScope を採用し、Phase 2 での階層型への拡張を視野に設計**する。

理由：

1. **複数スキル連鎖での必須要件**
   - スキル A の出力 → スキル B の入力という連鎖実行で、前のステップ結果の参照が必須
   - AgenticScope を使用することで、明示的な受け渡しが不要

2. **Progressive Disclosure との統合**
   - 各段階のコンテキスト（Level 1/2/3）を AgenticScope で中央管理
   - Context Engineering の最適化が AgenticScope の層別管理で実現

3. **Observability 統合の容易性**
   - 実行履歴・メトリクス・プロンプト内容を AgenticScope に集約

- Observability フレームワーク（NFR-30zem）がスコープから直接参照可能

4. **段階的な拡張（Phase 2 対応）**
   - Phase 1 の単一スコープ設計は Phase 2 での多階層スコープへの移行が容易
   - インターフェース設計で複数スコープ対応を念頭に置く

## Consequences

### Positive

- 複数スキル連鎖実行で、スキル間のデータ共有が直感的
- Observability フレームワークでの実行履歴・コンテキスト可視化が統一
- Phase 2 での複数エージェント連携や多階層スコープへの拡張が容易

### Negative

- AgenticScope の初期化・cleanup 処理が必要（リソースリーク対策）
- 複数テナント環境での scope 隔離が必要になる可能性（Phase 2）
- scope 内の共有オブジェクトのスレッドセーフティ確保が必須

### Neutral

- Phase 2 での多階層スコープ移行時に、既存の単一スコープ実装の大規模修正が発生
  - リスク軽減策：インターフェース・アブストラクション層で、スコープタイプの抽象化（Strategy パターン活用）

## Implementation Notes

### AgenticScope 利用方針

LangChain4j-agentic が提供する組み込み `AgenticScope` をそのまま使用し、独自のデータモデルを新設しない。スコープはエージェントシステム起動時に自動生成され、共有変数・呼び出し履歴・レスポンスを内部で管理する。

- 出力は `@Agent(outputKey = "...")` でスコープへ書き込む。
- 既存値は `@V("key")` でスコープから読み出す。
- 呼び出しシーケンスは自動でスコープに記録され、Observability 連携にもそのまま活用できる。
- Progressive Disclosure / 複数スキル連鎖ではキー命名規則のみを設計し、クラス拡張は不要。

#### スコープ入出力の例

```java
public interface CreativeWriter {
    @Agent(outputKey = "story", description = "トピックから物語を生成")
    String write(@V("topic") String topic);
}
```

### Workflow での AgenticScope 使用例

```java
UntypedAgent workflow = AgenticServices.sequenceBuilder()
    .subAgents(creativeWriter, audienceEditor, styleEditor)
    .outputKey("story")
    .build();

Map<String, Object> input = Map.of(
    "topic", topic,
    "audience", audience,
    "style", style
);

var response = workflow.run(input); // AgenticScope は内部で生成・共有される
```

### Progressive Disclosure / Observability への適用

- Progressive Disclosure ではスコープキーを段階ごとに分離し（例：`metadata.<id>` / `content.<id>` / `resources.<id>`）、段階的ロード後に不要な値は破棄してトークン消費を抑制する。
- Observability では AgenticScope に自動で残る呼び出し履歴・プロンプト・応答をそのまま収集し、追加のスコープ拡張や手動ロギングは行わない。

## Platform Considerations

- **マルチスレッド環境**：AgenticScope の並行アクセスはライブラリ側で管理される前提。アプリ側は長時間ロック・静的キャッシュを避け、短時間アクセスに徹する。
- **メモリ管理**：大規模実行履歴の scope 保持による OOM リスク → ライフサイクルに応じた cleanup フックで破棄し、不要なキーは逐次削除する。

## Security & Privacy

- **Scope 内の機密データ**：個人情報・API キーが context に含まれる可能性
  - Observability へ発行時に PII マスキング
  - scope cleanup 時のメモリ安全性確保

- **テナント隔離**（Phase 2）：複数テナント環境で、各テナントの scope が隔離される設計

## Monitoring & Logging

- **Observability 統合**：
  - 実行履歴（各ステップの実行時間・入出力）
  - コンテキストレベル管理（トークン消費・ロード時刻）
  - LLM へ送信されたプロンプト・LLM 応答

## Open Questions

- [ ] **複数 AgenticScope の管理（Phase 2）**
      → Next step: Supervisor/SubAgent パターンでの scope 設計（ADR-1 参照）

- [ ] **scope cleanup・メモリ管理**
      → Method: PoC-1 での長時間実行テスト、メモリ使用量の監視・cleanup タイミングの検証

- [ ] **テナント隔離（Phase 2）**
      → Next step: マルチテナント環境での scope 隔離設計（ADR-6 参照）

## External References

- [LangChain4j AgenticScope API](https://docs.langchain4j.dev/tutorials/agents/#introducing-the-agenticscope)
- [Concurrent Collections in Java](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html)
