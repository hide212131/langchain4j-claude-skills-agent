# ADR-mpiub Context Engineering 実装方針（Progressive Disclosure）

## Metadata

- Type: ADR
- Status: Approved

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)
- Related ADRs:
  - [ADR-1 Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md)
  - [ADR-2 スキル定義メタデータモデル](ADR-xoqky-skill-metadata-model.md)
- Impacted Requirements:
  - FR-DRAFT-4（Progressive Disclosure 実装）
  - NFR-DRAFT-1（Context Engineering 最適化）

## Context

Context Engineering は、限られたトークン予算内で LLM エージェント性能を最大化する設計手法。Claude Skills は以下の 3 段階ロード（Progressive Disclosure）メカニズムを提供：

1. **Level 1（メタデータ）**：YAML frontmatter（name、description）をシステムプロンプトに常時含める（\~100 トークン）
2. **Level 2（本文）**：SKILL.md 本文（Instructions、Examples、Guidelines）を必要時にコンテキストに含める（<500 行推奨）
3. **Level 3（参照リソース）**：スクリプト・参照ファイルは実行・参照時のみアクセス（出力結果のみコンテキストに含める）

本 ADR では、LangChain4j エージェント実装に、Claude Skills の Progressive Disclosure パターンを適用する実装方針を確立する。

**制約・仮定**：

- トークン予算：制限あり（例：入力 4K トークン以下を目標）
- 複数スキル環境：スキル数増加に伴い、コンテキスト効率化が重要
- AgenticScope：エージェント間のコンテキスト共有・管理に使用

## Success Metrics

- メトリック 1：`Progressive Disclosure 未適用時 vs 適用時のトークン消費削減率が測定される（目標：30～50%削減）`
- メトリック 2：`段階的ロード（Level 1/2/3）が正常に機能し、各レベルでのコンテキスト精度が確認される`
- メトリック 3：`複数スキル管理環境（5～10 スキル）でのコンテキスト効率が測定され、スケーラビリティが確認される`

## Decision

### Considered Options

- **Option A: 完全ロード（Context Engineering なし）**
  - すべてのスキル定義（frontmatter + 本文 + 参照ファイル）を常時コンテキストに含める
  - シンプルだが、トークン消費が膨大（複数スキル環境ではスケーリング不可）

- **Option B: Progressive Disclosure の 3 段階ロード**
  - Claude Skills と同じ 3 段階アプローチ：メタデータ常時 → 本文必要時 → リソース実行時
  - トークン効率が高いが、実装複雑度が増加

- **Option C: ハイブリッド：動的圧縮 + Progressive Disclosure**
  - Progressive Disclosure に加え、LLM へ送信するプロンプト内容をリアルタイム圧縮（Prompt Compression、LLM 要約など）
  - 最高のトークン効率だが、実装・運用複雑度が最大

### Option Analysis

| 観点                                   | 完全ロード             | Progressive Disclosure       | ハイブリッド（動的圧縮）             |
| -------------------------------------- | ---------------------- | ---------------------------- | ------------------------------------ |
| **トークン消費（単一スキル）**         | 高い（\~600 T）        | 中程度（\~300 T）            | 低い（\~150 T）                      |
| **複数スキル環境でのスケーラビリティ** | 低い（スキル数に線形） | 中程度（段階的ロードで抑制） | 高い（圧縮で最適化）                 |
| **実装複雑度**                         | 低い                   | 中程度                       | 高い                                 |
| **LLM 精度への影響**                   | なし（全情報利用）     | 低い（必要な情報は含まれる） | 中程度（圧縮による情報損失の可能性） |
| **デバッグ容易性**                     | 容易                   | 中程度                       | 困難（圧縮プロセスが不透明）         |

## Rationale

**Progressive Disclosure の 3 段階ロード**を Phase 1 で採用し、Phase 2 で動的圧縮への拡張を検討する。

理由：

1. **トークン効率と実装バランス**
   - 単一・複数スキル環境での効果が明確（30～50%削減が期待）
   - 実装複雑度が許容範囲内（Layer-based approach で管理可能）

2. **LLM 精度の維持**
   - スキル選択・実行に必要な情報は常に提供（Level 1 フルロード + 必要時 Level 2）
   - Context Engineering による精度低下リスクが最小

3. **デバッグ・Observability の容易性**
   - 各レベルでのコンテキスト内容が明確
   - Observability フレームワーク（FR-DRAFT-4）での可視化が直結

4. **段階的なスケーリング**
   - Phase 1：Progressive Disclosure で基盤確立
   - Phase 2：動的圧縮（Prompt Compression、LLM 要約）を追加
   - Phase 3：カスタム Context Engineering パターン（特定ユースケース向け）

## Consequences

### Positive

- トークン消費が 30～50%削減され、LLM API コスト低下・レイテンシ短縮 -複数スキル環境（5～10 スキル）での スケーラビリティ確保
- 各段階のコンテキスト内容が明確で、Observability での追跡が容易
- Progressive Disclosure ロジック自体が自己ドキュメント化（段階的な実装）

### Negative

- 段階的ロード（Level 2・3）のスキル選択・実行ロジックが複雑化
- レベル間でのコンテキスト同期・管理が必要（AgenticScope 設計で対応）
- LLM の判断誤り（必要な情報が Level 2・3 の遅延ロード に含まれる場合）の可能性

### Neutral

- Phase 2 での動的圧縮追加時に、既存 Progressive Disclosure ロジックの大規模修正が不要
  - インターフェース設計（リスク軽減策：ADR-5 で明確化）で、拡張可能な構造を事前準備

## Implementation Notes

### Level 1（メタデータ）実装

```java
// スキル frontmatter をシステムプロンプトに常時含める
String systemPrompt = """
    利用可能なスキル：
    """ + skillMetadataList.stream()
    .map(m -> String.format("- %s: %s", m.name(), m.description()))
    .collect(Collectors.joining("\n"));
```

**目標トークン数**：\~100 T（複数スキル環境でも 5～10 スキルで \~500 T に収まる）

### Level 2（スキル本文）実装

```java
// スキル選択後、本文を動的にコンテキストに追加
String skillContent = skillStore.loadContent(selectedSkillId);

String contextWithSkill = """
    選択されたスキル詳細：
    """ + skillContent.instructions();

// LLM へはメタデータ + 本文を送信
```

**目標トークン数**：<500 T（SKILL.md 本文）

**トリガー**：LLM が特定スキルを選択した場合のみロード

### Level 3（参照リソース）実装

```java
// スキル実行時、参照ファイルをアクセス・実行
List<SkillResource> resources = skillStore.loadResources(selectedSkillId);

// スクリプト実行（Python、Bash など）
ExecutionResult result = scriptExecutor.execute(resources);

// 実行結果のみをコンテキストに含める（スクリプト本体は含めない）
String contextWithResult = """
    スキル実行結果：
    """ + result.output();
```

**重要**：スクリプト・ファイル本体はコンテキストに含めず、実行結果のみを含める

### AgenticScope での段階的ロード管理

```java
// AgenticScope で各レベルのコンテキスト管理
class AgenticContextManager {
    void loadLevel1(String skillId) {
        // メタデータをロード
        SkillMetadata metadata = skillStore.loadMetadata(skillId);
        scope.addMetadata(skillId, metadata);
    }

    void loadLevel2(String skillId) {
        // 本文をロード
        SkillContent content = skillStore.loadContent(skillId);
        scope.addContent(skillId, content);
    }

    void loadLevel3(String skillId) {
        // 参照リソースをロード・実行
        List<SkillResource> resources = skillStore.loadResources(skillId);
        ExecutionResult result = executeResources(resources);
        scope.addExecutionResult(skillId, result);
    }
}
```

### Observability でのトークン消費測定

```java
// 各レベルでのトークン消費を計測
ObservabilityEvent event = ObservabilityEvent.builder()
    .level(1)
    .skillId(skillId)
    .tokenCount(calculateTokens(systemPrompt))
    .timestamp(System.currentTimeMillis())
    .build();

observabilityPublisher.publish(event);
```

## Platform Considerations

- **ファイルシステム**：SKILL.md ファイルの読み取り・キャッシング（Level 2・3 の遅延ロードで性能向上）
- **メモリ管理**：複数スキルのメタデータを記憶（Level 1 常時）、本文・リソースはオンデマンドロード

## Security & Privacy

- **参照リソース実行時のセキュリティ**（Level 3）：
  - スクリプト実行（Python、Bash など）のサンドボックス化・リソース制限が必須（ADR-6 参照）
  - 実行結果（stdout）のみをコンテキストに含める（ログ記録対象）

- **コンテキストデータの機密性**：
  - ユーザ入力・スキル実行結果に個人情報が含まれる可能性
  - Observability ツール（LangFuse・Application Insights）へ送信時に PII マスキング

## Monitoring & Logging

- **段階的ロード（Progressive Disclosure）のメトリクス**：
  - 各レベルのロード時刻、トークン消費量、メモリ使用量
  - コンテキスト効率の測定（フル ロード時 vs Progressive Disclosure 時の比較）

- **Observability の実装**：
  - LangFuse でのリアルタイム可視化（ローカル開発環境）
  - Azure Application Insights でのログ・メトリクス集約（本番環境）

## Open Questions

- [ ] **キャッシング戦略**
      → Method: PoC-1 でレベル別キャッシング効果（CPU・メモリ）を測定、最適キャッシュ方針を ADR で設定

- [ ] **複数スキルのコンテキスト管理（AgenticScope との相互作用）**
      → Next step: ADR-5（AgenticScope 活用シナリオ）で詳細設計

- [ ] **LLM の判断精度（Level 2 の遅延ロード による情報不足）**
      → Method: PoC-1・2 で複数スキル環境（5～10 スキル）での実測、精度・トークン消費のトレードオフ評価

- [ ] **Phase 2 での動的圧縮（Prompt Compression）の導入判定基準**
      → Next step: Phase 1 測定結果に基づき、追加の圧縮効果が必要か判定（ADR-10 参照）

## External References

- [Claude Agent Skills Best Practices - Progressive Disclosure Patterns](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices)
- [LangChain4j AgenticScope Documentation](https://docs.langchain4j.dev/tutorials/agents/)
- [Prompt Compression Techniques in LLMs](https://arxiv.org/abs/2305.11848) - 参考文献（Phase 2 向け）
