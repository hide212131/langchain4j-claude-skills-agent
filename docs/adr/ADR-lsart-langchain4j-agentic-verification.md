# ADR-lsart LangChain4j Agentic AI 最新機能の検証と適用

## Metadata

- Type: ADR
- Status: Approved

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)
- Related ADRs:
  - [ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md)
  - [ADR-ehfcj スキル実行エンジン設計](ADR-ehfcj-skill-execution-engine.md)
- Impacted Requirements:
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)

## Context

LangChain4j v1.9.0 以降の Agentic AI API は、複数の実装パターン・機能を提供している。本プロジェクトでは、これらの最新機能がプロジェクト要件を満たすか、詳細に調査・検証する必要がある：

1. **Workflow API**：明示的な制御フロー（[ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md) で候補として検討）
2. **Supervisor/SubAgents パターン**：LLM 主導の動的制御
3. **Custom Agentic Patterns**：ユーザ定義の Agentic パターン実装（PR [#3929](https://github.com/langchain4j/langchain4j/pull/3929) で導入された `Planner` 抽象、Goal-Oriented/P2P サンプルを含む）
4. **AgenticScope**：エージェント間のコンテキスト共有
5. **Tool Execution Engine**：LangChain4j 標準のツール実行機構
6. **Error Handling・Retry 機構**：フレームワークレベルのエラー処理

**制約・仮定**：

- LangChain4j バージョン：v1.9.0 以降
- 検証方法：GitHub リポジトリのサンプルコード・ドキュメント確認、PoC 実装

## Success Metrics

- メトリック 1：`LangChain4j v1.9.0 以降の Agentic AI API が、プロジェクト要件（FR-hjz63/FR-mcncb/FR-cccz4/FR-2ff4z/FR-uu07e）に対応可能か確認される`
- メトリック 2：`Workflow・Supervisor/SubAgents・Custom Agentic Patterns の各パターンが、技術的に実装可能か検証される`
- メトリック 3：`LangChain4j のエラーハンドリング・リトライ機構が、プロジェクトのセキュリティ・信頼性要件に対応可能か確認される`
- メトリック 4：`検証結果に基づき、[ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md)・[ADR-ehfcj スキル実行エンジン設計](ADR-ehfcj-skill-execution-engine.md) の設計決定に反映される`

## Decision

本 ADR では、LangChain4j v1.8 以降が提供する Agentic AI API（Workflow API, AgenticScope, Tool Execution Engine, Error Handling 等）を本プロジェクトの標準エージェント基盤として採用することを決定する。

そのうえで、FR-mcncb/FR-cccz4/FR-2ff4z および AN-f545x で定義された要求を満たせるかを PoC ベースで検証し、結果を [ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md) および [ADR-ehfcj スキル実行エンジン設計](ADR-ehfcj-skill-execution-engine.md) に反映する。

### Custom Agentic Patterns（新機能の焦点）

- **Planner 抽象の採用**：`Planner` インターフェースにより、エージェントの実行計画をカスタム実装可能。標準パターン（Sequence/Parallel/Supervisor 等）も同抽象で再実装されており、カスタムとの親和性が高い。
- **Goal-Oriented Planner (GOAP)**：サブエージェントの入出力キーを前提条件・成果物として扱い、最短パスでゴール（`outputKey`）に到達するシーケンスを自動探索する。SKILL.md の frontmatter/本文を入出力のマッピング源として利用することで、AN-f545x で求める段階的実行（Level 1/2/3 Progressive Disclosure）に適合。
- **P2P Planner**：AgenticScope の状態変化をトリガーに複数エージェントを再入呼び出しする分散型パターン。リトライや再検証をスコープの更新で駆動でき、スキル検証フロー（ADR-ehfcj スキル実行エンジン設計）や観測/メトリクス収集との併用が容易。
- **適用方針**：Custom Agentic Patterns を PoC の中心テーマとし、既存 Workflow/Supervisor との差分（決定性/柔軟性/可観測性）を計測・評価する。

### 検証対象領域

#### 1. Workflow API の詳細確認

- **対象**：
  - Workflow builder インターフェース
  - ステップ間のコンテキスト・パラメータ受け渡し
  - 条件分岐・ループ・並列化の表現能力

- **検証内容**：
  - 複数スキル連鎖実行（A → B → C）が実装可能か
  - ステップ内での LLM 呼び出し・外部 API 呼び出しが容易か
  - エラーハンドリング・リトライが標準サポートされているか

- **PoC 計画**：PoC-1（Workflow型）で実装・検証

#### 2. Supervisor/SubAgents パターンの詳細確認

- **対象**：
  - Supervisor の構築方法
  - SubAgent の登録・ロード
  - Supervisor による SubAgent の動的選択・制御

- **検証内容**：
  - スキル定義に基づく SubAgent の動的生成が可能か
  - Supervisor が複雑なタスク分解を行えるか
  - AgenticScope でのコンテキスト共有が機能するか

- **PoC 計画**：PoC-2（Pure Agent型）で実装・検証

#### 3. Custom Agentic Patterns

- **対象**：
  - Custom Agentic Patterns の実装方法（Planner 抽象）
  - Goal-Oriented Planner / Peer-to-Peer Planner の適用可否
  - SKILL.md 入出力（frontmatter+本文）のプリコンディション・ポストコンディション変換
  - AgenticScope の状態遷移を用いた分散実行と再トリガー制御

- **検証内容**：
  - GOAP で SKILL.md のメタデータから依存グラフを生成し、最短パス実行が可能か
  - P2P パターンでスキル再実行（検証・再採点）を AgenticScope だけで駆動できるか
  - Planner カスタマイズ（ハード制約：最大反復回数、ソフト制約：スコア閾値）で安全性・効率を両立できるか
  - 層状（Workflow + Pure Agent）ハイブリッドパターンを Planner API 経由で統合できるか

- **PoC 計画**：
  - PoC-2（Pure Agent型）を GOAP/P2P 両方で実装し、従来 Supervisor との品質差を計測
  - Phase 2 で Planner カスタマイズ（制約・優先度付け・状態監査フック）を実装

#### 4. AgenticScope のコンテキスト管理

- **対象**：
  - AgenticScope の初期化・使用方法
  - 複数エージェント間のスコープ管理
  - スコープ のライフサイクル

- **検証内容**：
  - スコープの初期化タイミング
  - スコープメモリ・コンテキストへのアクセス制御
  - 複数スコープ・テナント隔離の可能性

- **PoC 計画**：PoC-1・2 での実装過程で確認

#### 5. Tool Execution Engine

- **対象**：
  - LangChain4j の Tool インターフェース
  - Tool 登録・検出機構
  - Tool 呼び出し・パラメータ生成・実行

- **検証内容**：
  - JSON Schema ベースのパラメータ生成が機能するか
  - Tool 実行エラーの標準ハンドリングが提供されているか
  - Tool 実行のリトライ・タイムアウト設定が可能か

- **PoC 計画**：PoC-1・4 での実装で確認

#### 6. エラーハンドリング・リトライ機構

- **対象**：
  - フレームワークレベルのエラー処理
  - 組み込みリトライ戦略
  - カスタムエラーハンドリング

- **検証内容**：
  - Tool 実行失敗時の自動リトライがサポートされているか
  - リトライ戦略（指数バックオフなど）の設定が可能か
  - フェイルセーフ・フォールバック機構が提供されているか

- **PoC 計画**：PoC-1・4 での実装で確認

## Rationale

LangChain4j v1.9.0 以降の Agentic AI API は、プロジェクト要件（複数スキル実行、Context Engineering、Observability など）を満たす可能性が高い。以下の理由で、詳細な技術検証を重視：

1. **フレームワーク選択の妥当性確認**
   - Workflow・Supervisor/SubAgents の実装成熟度
   - Custom Agentic Patterns での拡張性

2. **実装設計への反映**
   - [ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md) での判定基準の明確化
   - [ADR-ehfcj スキル実行エンジン設計](ADR-ehfcj-skill-execution-engine.md) での Tool 利用方針の確定

3. **リスク軽減**
   - API 仕様変更・非互換変更への早期発見
   - フレームワークの限界・制約の把握

## Consequences

### Positive

- LangChain4j の標準機能（Workflow、AgenticScope、Tool など）の活用で、実装シンプル化
- フレームワークのレベルアップに伴う自動的なセキュリティ・機能向上の恩恵
- コミュニティ・ドキュメント・サンプルが豊富

### Negative

- LangChain4j フレームワークへの強い依存（他フレームワークへの移行困難）
- API 仕様変更（v1.8 → v2.0 など）への追従必要性
- 検証期間による初期開発スケジュール延長の可能性

また、本 ADR により「LangChain4j Agentic AI を採用しない」という選択肢は、LangChain4j 側の仕様上の制約や重大な不具合が PoC により確認された場合にのみ再検討することとし、現時点では他フレームワークや自前オーケストレーターを第一候補とはしない。

### Neutral

- 検証結果に基づき、ADR の設計決定が変更される可能性
  - リスク軽減策：検証結果を迅速に ADR に反映、意思決定をアジャイルに実施

## AN-f545x への適用箇所

- **問題 1/4（パターン選択・コンテキスト管理の欠落）**：GOAP により SKILL.md 入出力を前提/成果として扱い、最短パス決定的実行を提供。Pure Agent のブラックボックス性を低減し、Progressive Disclosure を deterministically 準拠させる。
- **問題 2/3（Context Engineering・段階的読み込み）**：Goal-Oriented Planner の状態遷移を AgenticScope に記録し、Level 1/2/3 の段階開示を状態キーで制御（`metadata`→`body`→`resources`）。P2P で不足情報を自動再収集。
- **問題 5（セキュリティ/リソース制約）**：Planner に最大反復・スコア閾値・タイムアウトを組み込み、AgenticScope の更新イベントで強制停止/フォールバックを実行。ADR-38940 のリソース管理策と連動。
- **問題 6（Observability）**：Planner の `Action`/AgenticScope 変化を LangFuse/AI Insights に送出し、ルーティング決定理由を可視化。PoC-4 と連動してメトリクス化。

## Implementation Notes

### 検証計画（PoC）

#### PoC-1：Workflow型 実装（3 日）

```
目標：
  - Workflow API で単一・複数スキル連鎖実行を実装
  - Context Engineering（Progressive Disclosure）の可行性確認

実装内容：
  1. Workflow builder で 3 段階ワークフロー定義
     a. スキル選択（LLM で choice を生成）
     b. スキル実行（Tool 実行）
     c. 結果集約
  2. AgenticScope でコンテキスト・メモリを管理
  3. エラーハンドリング・リトライの標準機構を確認

測定項目：
  - 実装日数・複雑度（ARC)
  - トークン消費（全体・段階別）
  - レイテンシ・スループット

検証チェックリスト：
  - [ ] Workflow 定義の学習曲線は許容可能か
  - [ ] AgenticScope での複数スキルのコンテキスト共有が機能するか
  - [ ] エラーハンドリング・リトライが標準で提供されているか
  - [ ] Progressive Disclosure（Level 1/2/3）が実装可能か
```

#### PoC-2：Pure Agent型 実装（3 日）

```
目標：
  - Supervisor/SubAgents で複数スキルの動的制御を実装
  - LLM 主導のスキル選択が有効か検証

実装内容：
  1. Supervisor 構築
  2. 複数 SubAgent（スキル毎）を登録
  3. Supervisor が LLM で SubAgent を選択・実行
  4. AgenticScope でのコンテキスト・メモリ共有確認

測定項目：
  - PoC-1 との実装複雑度比較
  - トークン消費量・精度
  - コンテキスト可視性・デバッグ性

検証チェックリスト：
  - [ ] Supervisor/SubAgents の学習曲線は許容可能か
  - [ ] LLM が複数 SubAgent を正確に選択できるか
  - [ ] スキル選択・実行のエラーハンドリング・リトライが機能するか
  - [ ] コンテキスト可視化（ブラックボックス化）の課題がないか
```

#### PoC-3：セキュリティ・リソース管理（2 日）

```
目標：
  - Tool 実行のタイムアウト・リソース制限が設定可能か
  - 許可リスト型の Tool 実行制約が実装可能か

実装内容：
  1. Tool のタイムアウト設定
  2. Tool 実行のリソース制限（Java の Thread Pool + Monitor）
  3. Tool 実行前の検証ロジック

検証チェックリスト：
  - [ ] Tool 実行のタイムアウト・メモリ制限が機能するか
  - [ ] Tool 実行失敗時のリトライ・フォールバックが機能するか
  - [ ] Tool 実行ログ・監査が可能か
```

#### PoC-4：Observability 統合（2 日）

```
目標：
  - LangChain4j Callback での Observability データ収集が可能か
  - LangFuse・Application Insights との統合が容易か

実装内容：
  1. LangChain4j Callback 実装
  2. LangFuse への Observability イベント送信
  3. メトリクス計測・ダッシュボード表示

検証チェックリスト：
  - [ ] Tool 呼び出し・LLM 呼び出しのコールバックが発火するか
  - [ ] トークン消費・実行時間が正確に計測されるか
  - [ ] LangFuse UI でリアルタイム可視化ができるか
```

### 検証結果テンプレート

```
=== LangChain4j Agentic AI 検証レポート ===

実施日：YYYY-MM-DD
検証者：...

1. Workflow API
   - 学習曲線：[簡単 / 中程度 / 難しい]
   - 複雑度：[低 / 中 / 高]
   - 実装可能性：[可能 / 検討要 / 不可]
   - 課題・制約：...

2. Supervisor/SubAgents
   - 学習曲線：...
   - 実装可能性：...
   - PoC-1 との比較：...

3. Custom Agentic Patterns
   - カスタマイズ可能性：[高 / 中 / 低]
   - 層状パターン実装：[可能 / 検討要 / 不可]

4. AgenticScope
   - コンテキスト管理の容易性：...
   - 複数スコープ管理：...

5. Tool Execution Engine
   - JSON Schema サポート：[完全 / 部分 / 非対応]
   - エラーハンドリング・リトライ：[完全 / 部分 / 自実装必要]
   - タイムアウト・リソース制限：[標準搭載 / カスタム必要]

6. Observability 統合
   - Callback 機構：...
   - LangFuse 統合：...

推奨事項：
  - [ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md) への反映内容
  - [ADR-ehfcj スキル実行エンジン設計](ADR-ehfcj-skill-execution-engine.md) への反映内容
  - その他の設計変更提案

リスク：
  - v1.8 の安定性・本番利用実績
  - v2.0 への移行計画の必要性
```

## Platform Considerations

- **LangChain4j バージョン管理**：v1.9.0 以降の変更ノートを継続監視
- **互換性テスト**：定期的に最新 LangChain4j バージョンでの互換性を確認

## Security & Privacy

- **フレームワークレベルのセキュリティ機能**：
  - Tool 実行のセキュリティ制約
  - Input Validation・Sanitization
  - Output Encoding

## Monitoring & Logging

- **LangChain4j Callback のログレベル調整**（本番でのパフォーマンス最適化）

## Open Questions

- [ ] **LangChain4j v2.0 のロードマップ・互換性**
      → Method: LangChain4j GitHub Issues・Discussions で情報収集

- [ ] **Custom Agentic Patterns での層状設計の実装可能性**
      → Method: PoC-2 で詳細検証、必要に応じて独自実装を検討

- [ ] **本番環境での LangChain4j Agentic AI の利用実績**
      → Method: GitHub・Stack Overflow での実装例・課題を調査

## External References

- [LangChain4j Agents Module (GitHub)](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-agentic)
- [LangChain4j Agent Tutorials](https://docs.langchain4j.dev/tutorials/agents/)
- [LangChain4j Release Notes](https://github.com/langchain4j/langchain4j/releases)
- [LangChain4j Custom Agentic Patterns](https://docs.langchain4j.dev/tutorials/agents/)
- [LangChain4j GitHub Issues](https://github.com/langchain4j/langchain4j/issues)
