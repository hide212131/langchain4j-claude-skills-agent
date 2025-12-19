# AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析

## Metadata

- Type: Analysis
- Status: Complete
  <!-- Draft: Initial exploration | Complete: Ready for requirements | Cancelled: Work intentionally halted | Archived: Analysis concluded -->

## Links

<!-- Internal project artifacts only. Replace or remove bullets as appropriate. -->

- Related Analyses:
  - N/A – 初期分析文書
- Related Requirements:
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../requirements/FR-hjz63-prompt-visibility-framework.md)
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
  - [FR-uu07e Progressive Disclosure 実装](../requirements/FR-uu07e-progressive-disclosure.md)
  - [NFR-kc6k1 Context Engineering 最適化](../requirements/NFR-kc6k1-context-engineering-optimization.md)
  - [NFR-mt1ve エラーハンドリングと堅牢性](../requirements/NFR-mt1ve-error-handling-resilience.md)
  - [NFR-3gjla セキュリティとリソース管理](../requirements/NFR-3gjla-security-resource-governance.md)
  - [NFR-yiown スキル検証・監査](../requirements/NFR-yiown-skill-verification-auditability.md)
  - [NFR-30zem Observability 統合](../requirements/NFR-30zem-observability-integration.md)
  - [NFR-mck7v 漸進的開発・評価サイクル支援](../requirements/NFR-mck7v-iterative-metrics-evaluation.md)
- Related ADRs:
  - [ADR-lsart LangChain4j Agentic AI 最新機能の検証と適用](../adr/ADR-lsart-langchain4j-agentic-verification.md)
  - [ADR-ckr1p Claude Skills 実装難易度カテゴリと段階導入方針](../adr/ADR-ckr1p-skill-implementation-leveling.md)
  - [ADR-38940 セキュリティ・リソース管理フレームワーク](../adr/ADR-38940-security-resource-management.md)
  - [ADR-ae6nw AgenticScope の活用シナリオ](../adr/ADR-ae6nw-agenticscope-scenarios.md)
  - [ADR-lq67e プロンプト改善メトリクスの定義と測定](../adr/ADR-lq67e-prompt-metrics-definition.md)
  - [ADR-ij1ew Observability 基盤の統合戦略](../adr/ADR-ij1ew-observability-integration.md)
  - [ADR-ehfcj スキル実行エンジン設計](../adr/ADR-ehfcj-skill-execution-engine.md)
  - [ADR-mpiub Context Engineering 実装方針（Progressive Disclosure）](../adr/ADR-mpiub-context-engineering-strategy.md)
  - [ADR-q333d Agentic パターンの選択基準](../adr/ADR-q333d-agentic-pattern-selection.md)
- Related Tasks:
  - [T-0mcn0 最小実行基盤タスク](../tasks/T-0mcn0-minimal-foundation/README.md)
  - [T-7k08g プロンプト・エージェント可視化タスク](../tasks/T-7k08g-prompt-visibility/README.md)
  - [T-9ciut コード品質ツールの統合](../tasks/T-9ciut-code-quality-tools/README.md)
  - [T-9t6cj 実LLM接続タスク](../tasks/T-9t6cj-llm-integration/README.md)

## Executive Summary

本分析は、LangChain4j v1.9.0 以降の最新 Agentic AI API（Plan/Act/Reflect パターン）を活用して、Claude Skills の仕様に基づいた Java エージェント実装の最小限の実行系構築に向けた問題空間の探索を行うもの。主な目的は 4 つ：

1. **LangChain4j の Agentic AI API 実装例の提示**
   - Workflow型 および Pure Agent型（Supervisor/SubAgents）の実装パターン
   - Context Engineering（コンテキスト最適化）の実装上の工夫
   - Plan/Act/Reflect サイクルの実装例

2. **Claude Skills 仕様を参考にした Java スキル実行系の構築**
   - Claude Skills の公開仕様（SKILL.md 構造など）から推測した実装
   - Java エージェントが自律的にスキルを選択・実行するパターン

3. **サーバサイド環境での安全で信頼可能な実装パターン確立**
   - Claude Skills は「自由度極めて高い」設計（任意 CLI、Python/Node.js コード実行）だが、LangChain4j のユースケースはサーバサイド（マルチテナント・多数ユーザリクエスト）が主流
   - スキル実行の安全性（命令種類固定化、サンドボックス化）とリソース管理（制限、隔離、監査）の実装パターン
   - マルチテナント環境でのクロステナント影響防止、スキル検証・許可フロー、ガバナンスの考慮

4. **プロンプト可視化・漸進的開発による AI 品質向上サイクルの確立**
   - LangFuse・Azure Application Insights による Observability 基盤の実装パターン
   - LLM へ送信するプロンプト内容・エージェント実行状態（AgenticScope コンテキスト、メモリ、実行履歴）の完全可視化
   - プロンプト改善メトリクスの定義（精度、トークン効率、レイテンシ、コスト）と定量評価
   - A/B テスト・多変量テストによる継続的な品質改善のための開発サイクル確立

本分析では、これら 4 つの目的を統合し、Agentic AI アーキテクチャの理解、Context Engineering、セキュリティ・リソース管理、Observability のトレードオフを整理し、サーバサイド環境での実装パターン確立に必要な要件を明確にする。

## Problem Space

### Current State

- **LangChain4j v1.9.0 以降**：最新 Agentic AI API（Agents モジュール）により、複数エージェントの協調（Supervisor/SubAgents）、Workflow 制御、AgenticScope による情報共有などが提供されている
  - 参照：[LangChain4j Agentic Support](https://docs.langchain4j.dev/tutorials/agents/)
- **実装パターンの課題**：
  - Workflow型（明示的な制御）と Pure Agent型（柔軟・適応的）のトレードオフ理解が不十分
  - Context Engineering（コンテキスト最適化）の実装パターンが示されていない
  - Plan/Act/Reflect サイクルを実装例で説明するドキュメントが少ない
- **LangChain4j での実用的なエージェント実装例の不足**：公開されている実装例や参考資料が限定的で、サーバサイド環境（マルチテナント・安全性を考慮）での実装パターンが不明確

### Desired State

- **LangChain4j Agentic AI API の実装パターン確立**
  - Workflow型・Pure Agent型の選択基準と実装例
  - Context Engineering の実装テクニック（プロンプト圧縮、段階的情報開示など）
  - Plan/Act/Reflect を実装したエージェント動作の具体例

- **Claude Skills 仕様に基づく Java スキル実行系**
  - SKILL.md を解析して、Java エージェントが必要なスキルを動的に選択・実行
  - エージェントが複数スキルを組み合わせて、複合的なタスク実行
  - スキル定義・登録・実行が統一されたインターフェース

- **Observability 基盤によるプロンプト可視化・漸進的開発**
  - **プロンプト可視化**：LLM へ送信されるプロンプト内容・AgenticScope のコンテキスト・メモリ状態を完全に可視化
  - **Observability ツール統合**：
    - ローカル開発環境：LangFuse による高速フィードバック・プロンプト改善
    - クラウド本番環境：Azure Application Insights による監視・分析
  - **プロンプト改善の定量評価**：精度（Accuracy）、トークン効率（Token Usage）、レイテンシ、コストなどのメトリクス定義と測定
  - **漸進的開発サイクル確立**：可視化データに基づくプロンプト・パラメータ改善 → 評価 → 改善の反復
  - **A/B テスト・多変量テスト**：異なるプロンプト・パラメータの効果を定量的に比較する仕組み

### Gap Analysis

1. **LangChain4j Agentic API の実装パターン欠落**
   - Workflow型 vs Pure Agent型の選択基準が明確でない
   - Supervisor/SubAgents の使い分けパターンが不明確
   - AgenticScope によるコンテキスト管理の実装例がない

2. **Context Engineering 実装方法の不明確性**
   - フレームワークレベルでのコンテキスト最適化（プロンプト圧縮、段階的情報提示）のパターンが示されていない
   - Progressive Disclosure パターン（必要なコンテキストを段階的に LLM へ提供）の実装方法が確立していない
   - LangChain4j の Pure Agent API ではコンテキスト管理が「ブラックボックス化」する問題がある
   - 層の自由度と効率性のトレードオフ理解が不足

3. **Claude Skills を参考にした Java 実装の不在**
   - フロントマター（name、description）のメタデータ抽出と本文（500 行以下の手順書）の構造化の方法が不明確
   - Progressive Disclosure パターン（Level 1: メタデータ常時 → Level 2: 本文は必要時 → Level 3: 参照リソースは実行時のみ）を Java エージェント実装に適用する方法が不明確

4. **エージェント・アーキテクチャの選択基準不足**
   - どのような要件（仕様の複雑性、柔軟性の必要性）で Workflow型 or Pure Agent型 を選ぶか、判断ガイドがない

5. **セキュリティ・マルチテナント環境での実行制約の欠落**
   - Claude Skills は CLI コマンド・任意の Python/Node.js コード実行など、「自由度極めて高い」設計
   - LangChain4j のユースケースはサーバサイド（多数ユーザリクエスト処理）が主流だが、セキュリティ考慮が不足
   - サーバサイド環境での課題：
     - **スキル実行の任意性と安全性のトレードオフ**：任意コード実行はリソース枯渇・セキュリティ侵害のリスク
     - **マルチテナント・マルチリクエスト環境での隔離**：スキル実行をサンドボックス化・リソース制限する必要
     - **スキル実行の検証・許可フロー**：信頼されたスキルのみ実行、実行前のコード分析・検証の仕組みが不明確
   - LangChain4j で実装する場合の安全なパターン（命令種類固定化、サンドボックス化、リソース管理）が提示されていない

6. **プロンプト可視化・漸進的開発パターンの欠落（Observability）**
   - 生成AI 開発で重要な「LLM へ送信するプロンプト内容」「エージェント実行状態（AgenticScope のコンテキスト・メモリ）」の可視化フレームワークが不在
   - LangFuse・Azure Application Insights などの Observability ツール統合パターンが提示されていない
   - プロンプト改善の定量評価方法（メトリクス定義、測定、A/B テスト）が不明確
   - ローカル開発環境（高速フィードバック）と本番環境（監視・分析）での Observability 使い分けが確立していない
   - 漸進的開発・評価サイクル（可視化 → 改善 → 評価の反復）の実装パターンが不足

## Stakeholder Analysis

| Stakeholder                         | Interest/Need                                       | Impact | Priority |
| ----------------------------------- | --------------------------------------------------- | ------ | -------- |
| Java 開発者（LangChain4j ユーザー） | LangChain4j の標準フローで Claude Skills が利用可能 | High   | P0       |
| Claude Platform 利用者              | エージェント機能が Java 環境で実装可能              | High   | P0       |
| プロジェクトメンテナー              | スキル統合の実装パターンが標準化・文書化される      | Medium | P1       |

## Research & Discovery

以下は、AN-f545x-claude-skills-agent.md に挿入すべき「User Feedback」および「Competitive Analysis」セクションの内容です。すべて日本語・原文ベース・省略なしで記述しています。

---

## Research & Discovery

### User Feedback

実際の OSS として、LangChain4j-examples や Quarkus / Spring Boot / MongoDB / Azure などの RAG・エージェント系サンプルは存在するものの、いずれもワークショップ／チュートリアル寄りであり、エンタープライズ開発者がアーキテクチャの参考にできる大規模な OSS 実装はまだ少ない。特に、Claude Skills × LangChain4j を組み合わせた現実的な OSS リファレンス実装へのニーズは満たされておらず、本プロジェクトはそのギャップを埋めることを狙っている。

### Competitive Analysis

- 近い概念としては OpenAI Function Calling / Tools、LangChain Agents（Python）、Semantic Kernel Skills、Parlant の Guideline Engine などがあるが、**SKILL.md のようなテキストベースのスキル記述 + 段階的読み込みを前提にした OSS ランタイムはほぼ存在しない**。
- OpenAI Codex / Code Interpreter / Function Calling は汎用的なコーディング・ツール実行には優れるものの、**契約書・設計書などのドメイン固有アーティファクトをスキルとして構造化・再利用する前提ではなく、Context Engineering の観点でギャップがある**。
- LangChain / Semantic Kernel は豊富なツール・プラグインを持つが、**多くが Python / .NET 前提であり Java からは別プロセス連携が必要**、かつ Progressive Disclosure や SKILL.md 互換フォーマットは提供していない。
- Parlant など一部の新興フレームワークは「ガイドラインの動的適用」という点で思想が近いものの、**Claude Skills 仕様に寄せた Java エージェント実装という観点では、実質的に競合が少なく本分析はほぼグリーンフィールドである**。

### Technical Investigation

#### 1. LangChain4j Agentic AI API（v1.9.0 以降）

**Agentic AI Components:**

- `Agent` インターフェース：LLM からの指示を解析、Tool 呼び出しを決定
- `Tool` インターフェース：入力スキーマ定義、関数型処理
- `Workflow`：逐次・ループ・並列・条件分岐などを制御（Workflow型）
- `Supervisor` + `SubAgents`：スーパバイザがエージェント群を動的に制御（Pure Agent型）
- `AgenticScope`：エージェント間の情報（メモリ、コンテキスト）を共有

**実装パターン:**

- **Workflow型**：明示的な制御フロー、決定的、可観測性が高い
- **Pure Agent型**：LLM 主導、柔軟・適応的、Context Engineering が重要

**参照:**

- [LangChain4j Agent Tutorials](https://docs.langchain4j.dev/tutorials/agents/)
- [LangChain4j Agents Module (GitHub)](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-agentic)

#### 2. Claude Skills 仕様（公開情報に基づく）

**スキル定義フォーマット（SKILL.md）：**

Claude Skills は **SKILL.md ファイル** で定義される。YAML frontmatter + Markdown 本文の構成：

```markdown
---
name: skill-identifier
description: スキルの説明。何ができるか、どんな時に使うか。（最大 1024 文字）
---

# Skill Name

Instructions - クロードが従うべき詳細な指示

## Examples

- 使用例 1
- 使用例 2

## Guidelines

- ガイドライン 1
- ガイドライン 2
```

**スキル定義の構成要素：**

1. **YAML Frontmatter（必須）**
   - `name`：スキル識別子（小文字・ハイフン・数字のみ、最大 64 文字）
   - `description`：スキル説明・使用場面（最大 1024 文字、第 3 人称）

2. **Markdown 本文（推奨）**
   - Main SKILL.md は 500 行以下を推奨（トークン効率重視）
   - セクション構成は Skills の用途により異なる
   - 一般的な構成例：Skill Name、Key Capabilities、Workflows、Design Principles など
   - 詳細内容は参照ファイルに分離（SKILL.md からの 1 段階参照）

3. **参照ファイル（オプション）**
   - Advanced workflows、複雑な手順、コード例などを別ファイルに分離
   - SKILL.md からの参照により、Progressive Disclosure を実現

**Progressive Disclosure メカニズム：**

Claude は必要なコンテキストのみを段階的にロードして効率化：

- **Level 1（メタデータ）**：YAML frontmatter（name、description）をシステムプロンプトに含める（\~100 トークン）→ スキル発見・選択時に常時参照
- **Level 2（Instructions）**：SKILL.md 本文をbash で読み取り、関連リクエスト時にコンテキストに含める（<500 行推奨）→ 手順書として必要時のみロード
- **Level 3（Resources/Code）**：スクリプト・参照ファイルは実行・参照時のみアクセス（スクリプト実行時は出力結果のみコンテキストに含まれ、コード自体はロードされない）

**参照:**

- [Claude Agent Skills Overview](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)（Progressive Disclosure の 3 段階ロードメカニズム）
- [Claude Agent Skills Best Practices](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices)（構造化ガイドライン）
- [Anthropic Skills Repository (GitHub)](https://github.com/anthropics/skills)（実装例）

#### 3. Context Engineering と実装上の課題

| 課題                                               | 説明                                                                                                                                                                                                                                                                      | 影響                                                               |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| コンテキスト管理の可視化                           | Pure Agent 型では LLM へのプロンプト内容が隠蔽される（ブラックボックス化）                                                                                                                                                                                                | Context Engineering の実装・最適化が困難                           |
| Workflow vs Pure Agent の選択                      | 仕様の複雑性、柔軟性要件に応じた選択基準がない                                                                                                                                                                                                                            | アーキテクチャ決定が曖昧                                           |
| SKILL.md フロントマター・本文のパース              | SKILL.md の YAML frontmatter（name、description）と Markdown 本文（Instructions、Examples、Guidelines）を解析し、Java オブジェクトに変換する処理                                                                                                                          | 実装複雑度が増加、YAML・Markdown パーサの選定・エラー対応が必要    |
| Progressive Disclosure（段階的コンテキスト効率化） | Claude Skills の 3 段階ロード（Level 1: メタデータ常時 → Level 2: 本文は必要時 → Level 3: リソースは実行時のみ）に基づくパターンを LangChain4j エージェント・スキル実装に適用。スキル定義・プロンプト・リソースを必要なタイミングでのみロードし、コンテキスト効率を最大化 | トークン消費削減、複数スキルの効率的管理、エージェント判断精度向上 |
| スキル実行のエラーハンドリング                     | 失敗時の再試行、フォールバック、Agent Loop 内での復帰戦略                                                                                                                                                                                                                 | システム堅牢性に必須                                               |

### Data Analysis

- LangChain4j Agent Tutorials ページのドキュメント状態：Agent パターンは定義されているが Claude 統合例は未提供

## Discovered Requirements

> Capture potential requirements as solution-agnostic problem statements focused on the problem to solve rather than any specific implementation.

### Functional Requirements (Potential)

**Note**: Agentic パターンの選択（Workflow型 vs Pure Agent型）と Context Engineering の実装方針は、本分析で明確にすべき **技術検証・トレードオフ分析** の一部であり、要件としては定義しない。これらは **ADR（Architecture Decision Record）で設計決定として記録される**。本 MVP で実装する機能は以下の通り：

- [ ] **FR-DRAFT-4**: プロンプト・エージェント状態の可視化フレームワーク
  - Rationale: LLM へ送信するプロンプト内容・エージェント内部状態を完全に可視化し、漸進的な開発・改善サイクルを確立。**本フレームワークは FR-DRAFT-1～3 の実装を支援・検証するための基盤であり、並行・密結合で開発される**
  - **Implementation Strategy**: FR-DRAFT-1～3 と同時に実装開始。各実装フェーズに伴走し、以下を可視化：
    - SKILL.md パース段階での入力・解析ステップ・出力
    - スキル実行エンジンの内部状態（スキル選択ロジック、実行パラメータ）
    - Progressive Disclosure の各レベル（Level 1/2/3）でのコンテキスト・トークン消費
  - Acceptance Criteria:
    1. SKILL.md パース段階での完全ログ（入力ファイル、解析ステップ、生成モデル）
    2. LLM へ送信されるプロンプト（入力、システムプロンプト、Context Engineering 後の形式）の完全ログ
    3. AgenticScope 内の状態（コンテキスト、メモリ、実行履歴）の可視化機構
    4. Plan/Act/Reflect サイクルの各ステップでの LLM 応答・エージェント決定の追跡
    5. スキル実行時の入力・出力パラメータのログと分析
    6. トークン消費、レイテンシ、エラー率などのメトリクス収集（NFR-DRAFT-5・6 と連携）

- [ ] **FR-DRAFT-1**: 単一スキルの簡単な手続きによる生成物実行
  - Rationale: Claude Skills 仕様に基づき、単一の SKILL.md（簡単な手続き）をパースし、Java エージェントが実行してテキスト生成物を出力する最小限のエンドツーエンド実装
  - **Implementation Strategy**: **FR-DRAFT-4（可視化フレームワーク）と並行実装**。SKILL.md パース～実行～生成物出力のフルサイクルを可視化し、検証・改善
  - **スコープ**: 単一スキル、シンプルな手続き（分岐なし、参照リソース不要、LLM による テキスト生成）
  - Acceptance Criteria:
    1. SKILL.md（YAML frontmatter + 簡単な Markdown 本文）のパース実装（JSON Schema → Java POJO）
    2. スキル実行インターフェース（入力パラメータ受け取り、LLM へのプロンプト生成）
    3. 生成物の出力（テキスト形式）
    4. パース～実行～出力のフルサイクルが FR-DRAFT-4 で可視化・追跡可能
    5. 単一スキルの成功パターン検証（テストスキルで end-to-end 動作確認）

- [ ] **FR-DRAFT-2**: 単一スキルの複雑な手続きによる生成物実行（スクリプト実行）
  - Rationale: FR-DRAFT-1 で実装した単一スキル実行を発展させ、複雑な手続き（条件分岐、参照リソース、スクリプト実行）を伴うスキル実行を実現。生成物はスクリプト実行によるバイナリファイル（pptx、pdf など）を想定
  - **Implementation Strategy**: FR-DRAFT-1 の実装完了後、FR-DRAFT-4 可視化の下で実装。スクリプト実行（Python、Bash など）の統合、参照リソース読み込み、バイナリ出力をトラッキング
  - **スコープ**: 単一スキル、複雑な手続き（条件分岐、参照リソース、スクリプト実行）、バイナリ生成物
  - **Note**: Claude Skills ライブラリの複雑度カテゴリ分けについて、ADR で詳細調査を実施（実装時に要件をさらに分割する可能性あり）
  - Acceptance Criteria:
    1. 複雑な Markdown 本文（条件分岐、参照リソースのメタデータ、スクリプト参照）のパース実装
    2. 条件分岐の制御（LLM が判定し、異なる処理フローを選択）
    3. 参照リソースの動的ロード（スキル実行時に外部ファイル・スクリプトを参照）
    4. スクリプト実行エンジン統合（Python、Bash など、LangChain4j Code Execution Engines を活用）
    5. バイナリ生成物の出力（pptx、pdf など）
    6. スクリプト実行の全トレーシングが FR-DRAFT-4 で可視化・検証可能

- [ ] **FR-DRAFT-3**: 複数スキルの組み合わせによる生成物実行
  - Rationale: 複数のスキルを組み合わせて、より複雑なタスクを実行し、最終的な生成物を出力する能力を実現
  - **Implementation Strategy**: FR-DRAFT-1・2 の実装完了後、FR-DRAFT-4 可視化の下で実装。複数スキル連鎖、スキル選択ロジック、エージェント決定をトラッキング
  - **スコープ**: 複数スキル、スキル間の連鎖実行、エージェントによるスキル選択
  - Acceptance Criteria:
    1. スキルストア（複数 SKILL.md の管理・検索）
    2. スキル検索・マッチング機構（タスク要件からスキルを自動選択）
    3. 複数スキルの組み合わせ実行（スキル A の出力 → スキル B の入力）
    4. エージェントの決定ロジック（スキル選択、パラメータ生成、再実行判定）
    5. 複数スキル実行フロー全体が FR-DRAFT-4 で可視化・監視可能

- [ ] **FR-DRAFT-4**: Progressive Disclosure 実装（段階的コンテキスト効率化）
  - Rationale: Claude Skills の 3 段階ロード（メタデータ → 本文 → 参照リソース）を LangChain4j エージェント実装に適用し、トークン消費を最適化。FR-DRAFT-1～3 での複数スキル運用を効率化
  - **Implementation Strategy**: FR-DRAFT-1～3 の実装進行に伴走。メタデータ常時ロード、本文必要時ロード、参照リソース実行時アクセスの 3 段階を実装。トークン消費削減を測定・可視化
  - Acceptance Criteria:
    1. Level 1（メタデータ常時）：frontmatter をシステムプロンプトに含める（\~100 トークン）
    2. Level 2（本文必要時）：Markdown 本文をコンテキストに動的ロード（スキル選択時のみ）
    3. Level 3（リソース実行時）：参照ファイルを実行時アクセス（実行時の出力のみコンテキストに含める）
    4. トークン削減効果が測定可能（フル ロード時 vs Progressive Disclosure 時の比較）
    5. 複数スキル運用時のコンテキスト効率（トークン消費削減、精度への影響）が FR-DRAFT-4 可視化フレームワークで追跡・検証可能

### Non-Functional Requirements (Potential)

- [ ] **NFR-DRAFT-1**: Context Engineering 最適化
  - Category: Performance / Design Quality
  - Rationale: 限られたトークン予算内で LLM エージェント性能を最大化
  - Target:
    - Progressive Disclosure による段階的情報提示で、コンテキスト効率を最大化
    - Observability による実行状況の可視化
    - 複数アーキテクチャパターンでのトークン消費・レイテンシ測定

- [ ] **NFR-DRAFT-2**: エラーハンドリング・堅牢性
  - Category: Reliability
  - Rationale: 本番環境での安定稼働
  - Target:
    - スキル実行エラーの適切なキャッチ・ロギング
    - Agent Loop 内での自動リトライ・フォールバック
    - 予期しない状態への graceful degradation

- [ ] **NFR-DRAFT-3**: セキュリティ・リソース管理
  - Category: Security / Reliability
  - Rationale: サーバサイド環境（マルチテナント・多数ユーザリクエスト）での安全で信頼可能な動作
  - Target:
    - スキル実行の命令種類固定化・許可リスト型による制約（Claude Skills の高自由度を段階的に制御可能にする）
    - サンドボックス化（LangChain4j Code Execution Engines を活用）のパターン提供
    - リソース制限（実行時間タイムアウト、メモリ上限、ファイル I/O 制限）の実装標準化
    - マルチテナント環境でのリソースクォータ管理・クロステナント影響防止

- [ ] **NFR-DRAFT-4**: スキル実行の検証・監査・追跡
  - Category: Security / Compliance
  - Rationale: スキル実行の信頼性確保、本番トラブル対応、ガバナンス要件充足
  - Target:
    - スキル信頼度レベル定義（信頼できる/検証済み/未検証など）と実行前検証ロジック
    - スキル実行のライフサイクル完全ログ（入力パラメータ、実行ユーザ、実行時刻、結果、リソース使用量）
    - 分散トレース対応によるエージェント・スキル実行チェーンの可視化
    - 監査ログ・セキュリティイベント（許可されていないスキル実行試行など）の記録

- [ ] **NFR-DRAFT-5**: Observability 基盤の統合（OTLP・LangFuse・Azure Application Insights）
  - Category: Development Quality / Operations
  - Rationale: ローカル開発環境での高速フィードバックと本番環境での長期監視・分析を両立
  - Target:
    - OTLP（OpenTelemetry Protocol）による標準化
    - ローカル開発：LangFuse による即座なプロンプト可視化・改善フィードバック
    - クラウド本番：Azure Application Insights による Observability 実装（カスタムイベント、メトリクス、ログ）
    - 統合戦略：ローカル → 本番への移行パターン、環境別ロギング・メトリクス設定の自動化

- [ ] **NFR-DRAFT-6**: 漸進的開発・評価サイクルの支援（メトリクス定義、A/B テスト）
  - Category: Development Quality / Process
  - Rationale: プロンプト改善の効果を定量的に測定し、継続的な品質向上を実現
  - Target:
    - メトリクス定義：精度（Accuracy）、トークン効率（Token Usage）、性能（Latency）、コスト、信頼性（Error Rate）
    - A/B テストフレームワーク：異なるプロンプト・アーキテクチャの定量比較
    - 改善サイクルの自動化：メトリクス収集 → 分析 → プロンプト改善 → 評価の反復実装

## Design Considerations

### Technical Constraints

1. **LangChain4j バージョン依存**
   - Agentic AI サポート：v1.9.0 以降（langchain4j-agentic, langchain4j-agentic-a2a モジュール必須）
   - Java Target Version：23 以上（LangChain4j 依存）

2. **Context Engineering の実装限界**
   - Pure Agent型では LLM へのプロンプト内容が隠蔽される（Context Engineering の可視化困難）
   - 層の自由度とコンテキスト管理の透明性はトレードオフ
   - 層の抽象度を低くするほど、Context Engineering の実装制御は容易に

3. **スキル定義・メタデータ処理**
   - SKILL.md （Markdown 形式）を Java オブジェクト へ変換・管理する実装
   - LangChain4j 標準の Structured Outputs（JSON Schema → Java POJO 自動マッピング）を活用し、スキル定義の型安全性・可保守性を確保

4. **スキル実行の制約**
   - スキルは同期関数として実装（非同期は Phase 2 以降）
   - 実行時間：LLM API タイムアウト内に完結（通常 30 秒以内）
   - エラーハンドリング：外部リソース（API、ファイル I/O）の失敗に対応必須

### Security & Resource Management

**サーバサイド環境での安全性・リソース管理方針：**

1. **スキル実行の安全性設計**
   - Claude Skills は「自由度極めて高い」設計（任意 CLI、Python/Node.js コード実行）
   - サーバサイド環境（マルチテナント・マルチリクエスト）での安全な実装パターン：
     - **命令種類の固定化**：許可リスト型の実行可能な操作を事前に定義
     - **サンドボックス化**：スキル実行を隔離環境（コンテナ、制限されたプロセス）で実行
     - **スキル実行の検証フロー**：信頼されたスキルのみを許可、実行前のコード分析

2. **リソース制限・管理**
   - **実行時間制限**：スキル実行タイムアウト設定（デフォルト 30 秒以内）
   - **メモリ制限**：スキル実行プロセスの最大メモリ使用量を事前定義
   - **ファイル I/O 制限**：読み取り可能なディレクトリ・ファイルサイズを制限
   - **並行実行制限**：同時実行スキル数を制御（スレッドプール化）

3. **監査・ロギング・追跡**
   - スキル実行のライフサイクル：入力パラメータ、実行ユーザ、実行時刻、結果を完全にログ
   - 予期しない動作・エラーの追跡可能性確保（トレーシング・分散トレース対応検討）

4. **マルチテナント環境での隔離**
   - テナント毎の実行リソース割当（リソースクォータ）
   - クロステナント・リスク（リソース枯渇による他テナント影響）の防止

### Observability & Development Environment

**プロンプト可視化・漸進的開発を支える Observability 基盤：**

1. **プロンプト・エージェント状態の可視化**
   - LLM へ送信されるプロンプト内容の完全ログ（入力、シスステムプロンプト、Context Engineering 後の圧縮版など）
   - AgenticScope の内部状態：コンテキスト、メモリ、実行履歴の可視化
   - Plan/Act/Reflect サイクルの各ステップでの LLM 応答・エージェント決定の追跡
   - スキル実行時の入力パラメータ・出力結果のログ

2. **Observability ツール統合戦略**
   - **ローカル開発環境**（高速フィードバック重視）：
     - LangFuse を活用し、プロンプト変更の影響を即座に確認
     - UI で LLM 呼び出し・トークン消費・レイテンシを可視化
   - **クラウド本番環境**（長期監視・分析）：
     - Azure Application Insights での Observability 実装
     - カスタムイベント、メトリクス、ログの集約・分析

3. **プロンプト改善メトリクスの定義**
   - **精度関連**：正解率（Accuracy）、F1 スコア、適合率・再現率（タスク固有）
   - **効率関連**：入力トークン数、出力トークン数、総トークン消費（Context Engineering 効果測定）
   - **性能関連**：エンドツーエンド レイテンシ、LLM API 応答時間、スキル実行時間
   - **コスト関連**：API コスト（トークン数ベース）、リソース消費量（サーバサイド計算コスト）
   - **信頼性関連**：エラー率、リトライ率、スキル実行成功率

4. **漸進的開発・評価サイクル**
   - **データ収集**：各実行のプロンプト・出力・メトリクスを Observability 基盤に記録
   - **分析**：メトリクス数値の変化傾向、異常検出、改善効果の定量評価
   - **プロンプト改善**：分析結果に基づいて、プロンプト・パラメータを改善
   - **A/B テスト**：異なるプロンプト・アーキテクチャを A/B テストで定量比較
   - **反復**: ← 改善効果が確認されたら本番環境に適用、反復

5. **開発環境の構築**
   - **ローカル環境**：Docker + LangFuse コンテナで即座にセットアップ可能
   - **クラウド環境**：Azure Application Insights の統合・設定テンプレート
   - **CI/CD 連携**：Observability データを CI/CD パイプラインに組み込み、品質管理自動化

### Potential Approaches

#### Option A: Workflow型 実装（エージェント制御を明示的に）

- 説明：LangChain4j `Workflow` builder を使用して、スキル選択・実行フローを明示的に定義。Plan/Act/Reflect を制御フローで実装
- Pros:
  - コンテキスト管理が完全に可視化（Context Engineering の実装制御が容易）
  - デバッグ・テストが容易
  - 予測可能な動作
- Cons:
  - 仕様変更時に制御フロー修正が必要（柔軟性低い）
  - 複雑な相互作用の表現が困難
  - LLM の自律性が制限される
- Effort: Medium
- 適用：仕様が比較的固定、決定的な制御が必要な場合

#### Option B: Pure Agent型 実装（Supervisor/SubAgents による動的制御）

- 説明：LangChain4j `AgenticServices.supervisorBuilder()` で Supervisor を構築。Supervisor が LLM 主導で SubAgents を動的に選択・実行
- Pros:
  - LLM の自律性・柔軟性を最大活用
  - 仕様変化への適応が容易（新規 SubAgent 追加のみ）
  - 複雑なタスク分解が自然
- Cons:
  - Context Engineering の実装が隠蔽される（ブラックボックス化）
  - デバッグが困難
  - トークン消費の最適化が難しい
- Effort: Medium
- 適用：仕様が流動的、LLM の判断が重要な場合

#### Option C: ハイブリッド：層別アプローチ（Workflow型 + Pure Agent型の組み合わせ）

- 説明：高レベル（Plan/Reflect）は Workflow で制御、中レベル（Act）は Pure Agent で実装。層ごとに最適なアプローチを選択
- Pros:
  - 柔軟性と可視性のバランス実現（JJUG CCC 発表の実装例に該当）
  - Layer の自由度と Context Engineering の制御を両立
  - スケーラビリティが高い
- Cons:
  - 設計・実装複雑度が増加
  - 層間の契約（Context 形式、エラーハンドリング）の管理が必要
- Effort: High
- 適用：長期的な大規模エージェントシステム、Context Engineering が重要な場合

### Architecture Impact

- **新規モジュール追加**：
  - `io.github.hide212131.langchain4j.claude-skills` パッケージ（スキル定義・メタデータモデル、エージェント実装、Context Engineering パターン）

- **既存との連携**：
  - LangChain4j Agents モジュールとの統合（Workflow/Pure Agent API）
  - LangChain4j AgenticScope によるコンテキスト管理
  - Claude Skills SKILL.md 仕様の Java モデリング

- **アーキテクチャ決定記録（ADR）が必要な項目**：
  1. **[ADR-q333d Agentic パターンの選択基準](../adr/ADR-q333d-agentic-pattern-selection.md)**：Workflow型 vs Pure Agent型 vs Hybrid（層状）の判定基準
  2. **[ADR-mpiub Context Engineering 実装方針](../adr/ADR-mpiub-context-engineering-strategy.md)**：Layer-based progressive disclosure、プロンプト圧縮の標準パターン
  3. **[ADR-ehfcj スキル実行エンジン設計](../adr/ADR-ehfcj-skill-execution-engine.md)**：複数スキル組み合わせ、エラーハンドリング・リトライ戦略、LangChain4j ネイティブ機構との整合性
  4. **[ADR-ae6nw AgenticScope の活用シナリオ](../adr/ADR-ae6nw-agenticscope-scenarios.md)**：Workflow型・Pure Agent型の両アーキテクチャにおけるコンテキスト管理とコンテキスト共有ルール
  5. **[ADR-38940 セキュリティ・リソース管理フレームワーク](../adr/ADR-38940-security-resource-management.md)**：命令種類固定化、LangChain4j Code Execution Engines を活用したサンドボックス化、リソース制限の実装方針
  6. **[ADR-ij1ew Observability 基盤の統合戦略](../adr/ADR-ij1ew-observability-integration.md)**：OTLP（OpenTelemetry Protocol）の採用、LangFuse（ローカル開発）と Azure Application Insights（本番）の選択基準と統合パターン
  7. **[ADR-lq67e プロンプト改善メトリクスの定義と測定](../adr/ADR-lq67e-prompt-metrics-definition.md)**：精度・効率・性能・コスト・信頼性メトリクスの標準化
  8. **[ADR-lsart LangChain4j Agentic AI 最新機能の検証と適用](../adr/ADR-lsart-langchain4j-agentic-verification.md)**：[Custom Agentic Patterns](https://docs.langchain4j.dev/tutorials/agents/) を含む最新の Agentic AI 機能が本要件に対応可能かの調査・検証、API 仕様の詳細確認

## Risk Assessment

| Risk                                                               | Probability | Impact | Mitigation Strategy                                                                                      |
| ------------------------------------------------------------------ | ----------- | ------ | -------------------------------------------------------------------------------------------------------- |
| LangChain4j Agentic API の仕様変更（v1.9.0 -> v2.0 など）          | Medium      | Medium | 版を明示的に固定し、API 変更ノートを継続監視；定期的な互換性チェック実装                                 |
| Pure Agent型のコンテキスト管理が「ブラックボックス化」             | High        | High   | 初期実装では Workflow型またはHybrid パターンを優先；Context 可視化の仕組みを設計                         |
| Workflow型の実装が複雑・保守性低下                                 | Medium      | Medium | 段階的実装（単純 Workflow → Supervisor/SubAgents）；テンプレート化・ドキュメント充実                     |
| スキル定義メタデータモデルと SKILL.md 仕様の乖離                   | Medium      | Medium | 仕様を明確化する ADR 作成；SKILL.md のバージョン管理を文書に含める                                       |
| AgenticScope でのコンテキスト共有制御の複雑化                      | Low         | Medium | MVP は単一 scope パターンのみ；複数 scope 運用は Phase 2 へ延期                                          |
| トークン消費量の予測不可（Context Engineering パターン選択に依存） | High        | High   | 早期ベンチマーク実施（複数パターン）；トークン計数の可視化ロジック実装                                   |
| Java リフレクションによるスキル定義解析の性能                      | Low         | Medium | Jackson など成熟した JSON ライブラリで解析；AOT コンパイル対応を検討                                     |
| JJUG CCC 発表例での実装例と実際の LangChain4j API の齟齬           | Medium      | Medium | 発表スライドの詳細確認と実装検証用 PoC；問題があれば設計段階で対応                                       |
| **スキル実行の任意性による安全性リスク**（セキュリティ）           | High        | High   | 初期実装で命令種類固定化・許可リスト型を採用；サンドボックス化・リソース制限を必須設計パターンとする     |
| **マルチテナント環境でのリソース枯渇**（クロステナント影響）       | Medium      | High   | リソースクォータ実装、スレッドプール制限、実行タイムアウト強制；監視・アラート設定                       |
| **スキル実行の検証フロー欠如**（信頼できないスキル実行）           | Medium      | High   | スキル信頼度レベル定義、実行前コード分析、管理者承認フロー等を design phase で明確化                     |
| **ログ・監査の不完全性**（本番トラブル対応困難）                   | Medium      | Medium | 実行ライフサイクル（入力・実行ユーザ・時刻・結果）の完全ログ、分散トレース対応を必須要件とする           |
| **Observability データの個人情報漏洩リスク**（セキュリティ）       | Medium      | High   | プロンプト内の PII（個人情報）マスキング、Observability ツールのアクセス制御、データ保護ポリシーの明確化 |
| **Observability による性能オーバーヘッド**（性能）                 | Medium      | Medium | ログレベル切り替え（本番は最小ロギング）、バッチ処理による Observability データ送信、遅延許容度の評価    |
| **開発環境と本番環境の Observability 差異**（開発効率）            | Medium      | Medium | ローカル（LangFuse）と本番（Application Insights）での可視化ギャップを埋めるアダプタ層実装               |

## Open Questions

- [ ] **Workflow型 vs Pure Agent型の実装複雑性の実測**
  - LangChain4j のドキュメントでは両者の学習曲線が明記されていない
  - 小規模 PoC で両パターンを実装し、実測による判定基準を確立する必要がある
    → Next step: 両パターンの最小実装例（単一スキル実行）を PoC で作成・測定

- [ ] **Claude Skills SKILL.md フォーマットの Java モデルマッピング**
  - Claude Skills 公開仕様では metadata、inputs、resources の構造が示されているが、Java での最適なモデリング方法が未決定
  - Jackson・Lombok・Record クラスなど、複数の実装パターンが考えられる
    → Next step: 仕様の詳細分析と、プロトタイプでの検証；ADR での設計決定記録

- [ ] **Context Engineering 実装の自由度と複雑性のバランス**
  - Layer-based progressive disclosure は有効だが、各層でのスキル定義・メタデータ管理がどの程度複雑化するか不明
  - MVP では単一層か多層か、決定基準が未確立
    → Method: JJUG CCC 発表例の詳細確認；設計段階での層構造の定義

- [ ] **LangChain4j Agentic API の本番環境への適合性**
  - v1.9.0 の安定性・パフォーマンス・本番利用の実績を確認する必要がある
  - エラーハンドリング・パフォーマンス・ホットリロード対応が必要かどうか、早期に検証する必要がある
    → Method: GitHub Issues、コミュニティチャットでの実装例収集；複数シナリオでの PoC 実施

- [ ] **AgenticScope での複数 Agent 間のコンテキスト共有の制御方法**
  - Supervisor/SubAgents パターンで複数エージェントが走る場合、コンテキスト管理のスコープ定義がどうなるか、ドキュメント不備
    → Method: LangChain4j の Supervisor・SubAgent サンプルコード詳細読解；実装時に ADR で明確化

- [ ] **スキル実行のサンドボックス化・隔離方法**（セキュリティ）
  - Claude Skills の「自由度極めて高い」設計（CLI、任意コード実行）をサーバサイド環境でどう実装するか
  - コンテナ隔離、プロセス隔離、言語・ランタイムごとの制約方法が不明確
  - Java プロセス内での命令種類固定化 vs 外部プロセス/コンテナ化のトレードオフが未決定
    → Method: セキュリティ PoC（サンドボックス実装例）を複数パターン作成；ADR で選択基準を明確化

- [ ] **マルチテナント環境でのリソース隔離・ 監視**（信頼性・セキュリティ）
  - 複数テナントが同一 JVM 内でスキル実行する場合、リソースクォータ・実行制限の実装方法
  - 1 テナントのリソース枯渇が他テナントに影響しない設計が必要だが、Java の標準機構では限界がある可能性
  - リソース監視・アラート設定の粒度（テナント単位、スキル単位）が未決定
    → Method: マルチテナント リソース管理の PoC（ThreadPoolExecutor、タイムアウト、メモリ制限等）を実装；ADR で方針決定

- [ ] **スキル実行の検証・許可フロー**（セキュリティ・ガバナンス）
  - どのスキルを「信頼できるか」定義する基準が不明確（署名検証、ソースコード分析、管理者承認など）
  - 未検証スキル実行の拒否・制限ポリシーをどう実装するか
  - スキル更新時の版管理・ロールバック仕組みが必要かどうか
    → Method: スキル信頼度レベル定義と、実行前検証ロジックを設計段階で明確化；ガバナンス ADR を作成

- [ ] **LangFuse vs Azure Application Insights の選択基準**（Observability）
  - LangFuse（ローカル開発、高速フィードバック）の利点と限界
  - Azure Application Insights（クラウド本番、長期監視）の利点と統合複雑性
  - 両ツールの統合アーキテクチャ（ローカル → 本番への移行パターン）が未定義
    → Method: 各ツールの PoC 実装による比較検証；ADR で統合戦略を明確化

- [ ] **AgenticScope 状態・プロンプトのデータモデル化**（Observability）
  - LLM へ送信されるプロンプト（入力、システムプロンプト、Context Engineering 後の形式）のログ形式
  - AgenticScope 内のコンテキスト・メモリ・実行履歴の可視化形式・粒度
  - Observability ツール固有の制約（ペイロードサイズ、カーディナリティ制限など）への対応方法
    → Method: AgenticScope のデータモデル設計、Observability スキーマ PoC を実装

- [ ] **プロンプト改善の定量評価フレームワーク**（Observability）
  - メトリクス間の関係性：精度向上がトークン消費増加につながる可能性
  - 多変量テスト設計（A/B テスト以上の複雑性）、統計的有意性の判定方法
  - 改善サイクルの自動化（プロンプト改善提案、ロールバック判定など）の実装度合い
    → Method: メトリクス定義の設計ドキュメント、A/B テストフレームワークの PoC を実装

- [ ] **ローカル・本番環境の Observability ギャップ管理**（開発効率）
  - ローカル（LangFuse）でのテスト→本番（Application Insights）への移行時の可視化差異
  - 本番環境で新たに発見される問題へのアダプタ層実装の負担
  - ログレベル・ロギング量の環境別切り替え戦略が不明確
    → Method: アダプタ層の設計、本番環境との検証環境（staging）での PoC 実施

## Recommendations

### Immediate Actions

1. **LangChain4j Agentic AI API の詳細調査**
   - GitHub リポジトリの `langchain4j-agentic` モジュール（[GitHub](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-agentic)）を精査
   - Workflow型、Pure Agent型、Supervisor/SubAgents の実装例を確認
   - AgenticScope によるコンテキスト共有パターンを理解
   - Plan/Act/Reflect サイクルの実装されたサンプルを確認

2. **Claude Skills ライブラリの複雑度カテゴリ分けを調査**
   - GitHub 公開リポジトリ [Anthropic Skills Repository](https://github.com/anthropics/skills) の実装例を分析
   - スキルの複雑度レベルを分類（簡単な手続き vs 複雑な手続き vs スクリプト実行）
   - 各レベルの実装パターン、必要な技術スタック、トークン消費パターンを記録
   - **結果に基づいて、FR-DRAFT-2 をさらに細分割する可能性を評価**
   - 調査結果を ADR へ反映

3. **Claude Skills 仕様の詳細確認**
   - [Claude Agent Skills Overview](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview) の精読
   - SKILL.md フォーマット・メタデータ構造の確認
   - GitHub の [Anthropic Skills Repository](https://github.com/anthropics/skills) でスキル定義例を複数確認

4. **JJUG CCC 2025 Fall 発表スライドの詳細分析**
   - P35 以降の Agentic AI アーキテクチャパターンの図解を精読
   - Plan/Act/Reflect パターン、Workflow型 vs Pure Agent型のトレードオフを理解
   - Context Engineering 実装例の詳細確認

5. **プロトタイプ実装による検証**
   - **PoC-1 (Workflow型)**：LangChain4j Workflow API を使用した単一スキル実行（3日）
   - **PoC-2 (Pure Agent型)**：LangChain4j Pure Agent API を使用した同一タスク実行（3日）
   - 両者の実装複雑性・トークン消費・コンテキスト可視性を比較測定

6. **セキュリティ・マルチテナント考慮の PoC**
   - **PoC-3 (サンドボックス化)**：Java プロセス内での命令種類固定化、外部プロセス隔離のパターン実装
   - **PoC-4 (リソース管理)**：ThreadPoolExecutor、タイムアウト、メモリ制限による複数スキルの並行実行管理
   - **PoC-5 (スキル検証)**：スキル実行前の信頼度検証、許可リスト管理の仕組みプロトタイプ
   - セキュリティ PoC で判明した実装課題を ADR へ反映

7. **Observability・プロンプト可視化の PoC**
   - **PoC-6 (LangFuse 統合)**：
     - ローカル開発環境での LangFuse セットアップ（Docker）
     - LLM へ送信するプロンプト・エージェント実行状態の可視化
     - プロンプト改善効果のリアルタイム測定（トークン消費、レイテンシ）
   - **PoC-7 (Azure Application Insights 統合)**：
     - クラウド本番環境での Application Insights 統合
     - カスタムイベント・メトリクス・ログの設計・実装
     - ローカル（LangFuse）との Observability ギャップ分析
   - **PoC-8 (プロンプト改善フレームワーク)**：
     - メトリクス定義（精度、トークン効率、レイテンシ、コスト）
     - A/B テストフレームワークの実装例
     - 改善サイクル（可視化 → 分析 → プロンプト改善 → 評価）の検証
   - Observability PoC で判明した設計課題を ADR へ反映

### Next Steps

1. [ ] **Analysis 承認後、Requirements ドキュメントを正式化**
   - 本分析から発見された FR-DRAFT-1 ～ FR-DRAFT-4（4個）、NFR-DRAFT-1 ～ NFR-DRAFT-6（6個）を FR-<id>、NFR-<id> 形式に正式化
   - 各要件の受け入れ基準（Acceptance Criteria）を詳細に記述

2. [ ] **Architecture Decision Record（ADR）を 8 件作成**
   - **[ADR-q333d Agentic パターンの選択基準](../adr/ADR-q333d-agentic-pattern-selection.md)**：Workflow型 vs Pure Agent型 vs Hybrid の判定基準
   - **[ADR-mpiub Context Engineering 実装方針](../adr/ADR-mpiub-context-engineering-strategy.md)**：Progressive Disclosure による段階的ロード
   - **[ADR-ehfcj スキル実行エンジン設計](../adr/ADR-ehfcj-skill-execution-engine.md)**：複数スキル組み合わせ、エラーハンドリング
   - **[ADR-ae6nw AgenticScope の活用シナリオ](../adr/ADR-ae6nw-agenticscope-scenarios.md)**：Workflow型・Pure Agent型の両パターン
   - **[ADR-38940 セキュリティ・リソース管理フレームワーク](../adr/ADR-38940-security-resource-management.md)**：命令種類固定化、LangChain4j Code Execution Engines 活用、リソース制限
   - **[ADR-ij1ew Observability 基盤の統合戦略](../adr/ADR-ij1ew-observability-integration.md)**：OTLP、LangFuse、Azure Application Insights の統合
   - **[ADR-lq67e プロンプト改善メトリクスの定義と測定](../adr/ADR-lq67e-prompt-metrics-definition.md)**：精度・効率・性能・コスト・信頼性
   - **[ADR-lsart LangChain4j Agentic AI 最新機能の検証と適用](../adr/ADR-lsart-langchain4j-agentic-verification.md)**：Custom Agentic Patterns を含む

3. [ ] **Task パッケージを作成し、MVP Design & Plan フェーズへ**
   - Design: 選択された Agentic パターン、モジュール構成、Progressive Disclosure 層の設計
   - Plan: PoC・統合テストの詳細計画

4. [ ] **PoC 検証の結果に基づき、要件・ADR・Task 計画を反復**
   - Agentic パターン比較検証（実装複雑性、トークン消費、コンテキスト可視性）
   - 必要に応じて、既存の要件・ADR を修正

### Out of Scope

- **本 MVP に含めない**：
  - **マルチターン対話の複雑なシナリオ**：複数回のエージェント・ループ、コンバーセーション履歴管理、メモリ持続化
  - **スキルの動的生成・削除機構**：スキル定義の実行時上書き・ホットリロード
  - **複数エージェント間の並行実行・同期制御**：Supervisor/SubAgents は単一シナリオ（決定的パターン）のみ
  - **Web UI・REST API・gRPC 外部インターフェース**：Java ライブラリ形式、CLI のみ
  - **マイグレーション・バージョン互換性**：初期バージョンで長期サポート戦略は不要

- **スコープ内（実装環境）**：
  - **LLM 選択**：初期実装は OpenAI API（gpt-5.1 など）を標準とする（最もスタンダードで、LangChain4j の統合が充実）。Claude API は Phase 2 以降で追加

- **検討対象外（Phase 2 以降）**：
  - **他 LLM（Gemini、Ollama など）への対応**：OpenAI パターン確立後
  - **スキル実行の分散処理・キューイング**：単一 JVM 内実行が前提
  - **スキルメディエーション・トランザクション管理**：原子性要件なし（ステートレス想定）
  - **監視・ロギング・トレーシング基盤**：標準的な SLF4J 統合のみ

## Appendix

### References

**LangChain4j Agentic AI API**

- [LangChain4j Agent Tutorials](https://docs.langchain4j.dev/tutorials/agents/)
- [LangChain4j Agents Module (GitHub)](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-agentic)
- [LangChain4j AgenticServices API Documentation](https://docs.langchain4j.dev/tutorials/agents/)

**Claude Skills 仕様**

- [Claude Agent Skills Overview](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)
- [Claude Agent Skills Best Practices](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices)（構造化ガイドライン）
- [Anthropic Skills Repository (GitHub)](https://github.com/anthropics/skills)

**Agentic AI アーキテクチャ参考文献**

- JJUG CCC 2025 Fall - LangChain4j presentation（P35 以降：Plan/Act/Reflect パターン、Workflow型 vs Pure Agent型、Context Engineering について詳述）

### Raw Data

- LangChain4j Version: v1.9.0 or later (check Maven Central for latest)
- Java Target Version: 23 or later (LangChain4j 依存)

---

## Template Usage

For detailed instructions and key principles, see [Template Usage Instructions](../templates/README.md#analysis-template-analysismd) in the templates README.
