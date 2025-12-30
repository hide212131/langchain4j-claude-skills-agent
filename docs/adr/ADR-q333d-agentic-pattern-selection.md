# ADR-q333d Agentic パターンの選択基準

## Metadata

- Type: ADR
- Status: Approved
  <!-- Draft: Under discussion | Approved: Ready to be implemented | Rejected: Considered but not approved | Deprecated: No longer recommended | Superseded: Replaced by another ADR -->

## Links

<!-- Internal project artifacts only. The Links section is mandatory for traceability. Replace or remove bullets as appropriate. -->

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)
- Impacted Requirements:
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../requirements/FR-hjz63-prompt-visibility-framework.md)
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
  - [FR-uu07e Progressive Disclosure 実装](../requirements/FR-uu07e-progressive-disclosure.md)
- Related Tasks:
  - [T-0mcn0 最小実行基盤タスク](../tasks/T-0mcn0-minimal-foundation/README.md)
  - [T-7k08g プロンプト・エージェント可視化タスク](../tasks/T-7k08g-prompt-visibility/README.md)
  - [T-9t6cj 実LLM接続タスク](../tasks/T-9t6cj-llm-integration/README.md)

## Context

<!-- What problem or architecturally significant requirement motivates this decision? Include constraints, assumptions, scope boundaries, and prior art. Keep value-neutral and explicit. -->

LangChain4j v1.10.0 以降は、エージェント・アーキテクチャの実装パターンとして、以下の 2 つの主要アプローチを提供している：

1. **Workflow 型**（明示的な制御フロー）：
   - LangChain4j `Workflow` builder を使用して、スキル選択・実行フローを制御フローで定義
   - Plan/Act/Reflect を制御フローで実装
   - コンテキスト管理が完全に可視化

2. **Pure Agent 型**（LLM 主導）：
   - LangChain4j `AgenticServices.supervisorBuilder()` で Supervisor を構築
   - Supervisor が LLM 主導で SubAgents を動的に選択・実行
   - 柔軟性・適応性が高い

本プロジェクトでは、Claude Skills 仕様に基づいた Java エージェント実装において、どのパターンを採用するか、また、両者のハイブリッド（層状）アプローチを検討するか、の判定基準を確立する必要がある。

**制約・仮定**：

- LangChain4j v1.10.0 以降の API に依存
- サーバサイド環境（マルチテナント・多数ユーザリクエスト）での運用を想定
- Context Engineering（Progressive Disclosure）の実装が重要

**トレードオフ**：

- **Workflow 型**：可視性・予測可能性 ↔ 柔軟性・LLM 自律性
- **Pure Agent 型**：柔軟性・適応性 ↔ コンテキスト可視化・デバッグ性

## Success Metrics

- メトリック 1：`Workflow 型・Pure Agent 型の選択基準が明文化され、判定フローがドキュメント化される`
- メトリック 2：`PoC 実装（Workflow 型・Pure Agent 型各パターン）のトークン消費・レイテンシ・実装複雑度が測定される`
- メトリック 3：`複数スキル組み合わせシナリオで、各パターンの実装可能性が確認される`

## Decision

本 ADR では、**ハイブリッド / 層状アプローチ（Option C）を標準パターンとして採用**し、Workflow / Pure Agent の使い分けを以下のポリシーで運用する：

1. **単一スキル内で多種多様な処理パターンが発生する場合は、抽象度の高い Pure Agent を用いる**
   - 例：[ADR-ckr1p Claude Skills 実装難易度カテゴリと段階導入方針](ADR-ckr1p-skill-implementation-leveling.md) のレベル 3/4 相当の複雑なスキル実行（外部プロセス実行、ビルドパイプライン、多段実行など）
   - 処理フローが動的で、実行時の状況に応じて柔軟に判断・適応する必要があるケース

2. **上記以外の処理（比較的決定的・手続き的な処理）は Workflow を用いる**
   - 既に業務フローが明確な単一スキル内の処理
   - 「入力 → バリデーション → 変換 → 出力」のように、ステップが安定したパイプライン処理

3. **Pure Agent の抽象度が高すぎて Progressive Disclosure / Context Engineering の制御が困難な場合は、抽象度を下げる方向にリファクタリングする**
   - 「高レベル Pure Agent → 中レベルの Workflow / SubAgent」へと段階的に分解し、実装側で制御できる粒度まで抽象度を下げる

### Decision Drivers

- Context Engineering / Progressive Disclosure を破綻させずに実装できること
- 単一スキル内で [ADR-ckr1p Claude Skills 実装難易度カテゴリと段階導入方針](ADR-ckr1p-skill-implementation-leveling.md) レベル 3/4 相当の複雑な処理を扱えるだけの柔軟性を確保すること
- Pure Agent の抽象度が過剰になった場合でも、Workflow や SubAgent への分割によって段階的に制御を取り戻せること
- MVP から拡張フェーズまで一貫した Phase 設計を取れること

### Considered Options

- **Option A: Workflow 型を標準採用**
  - 全体の制御フロー・スキル選択・実行を明示的に定義
  - Plan/Act/Reflect を制御フロー（条件分岐・ループ・並列化）で表現

- **Option B: Pure Agent 型を標準採用**
  - Supervisor/SubAgents による LLM 主導の動的制御
  - スキル選択・実行フローを LLM に任せる

- **Option C: ハイブリッド（層状）アプローチ**
  - 高レベル（Plan/Reflect）は Workflow で制御
  - 中レベル（Act）は Pure Agent で実装
  - 層ごとに最適なアプローチを選択

### Option Analysis

| 観点                       | Workflow 型                      | Pure Agent 型              | ハイブリッド（層状）     |
| -------------------------- | -------------------------------- | -------------------------- | ------------------------ |
| **コンテキスト可視化**     | 完全に可視                       | ブラックボックス化         | 部分的に可視             |
| **デバッグ・テスト容易性** | 容易                             | 困難                       | 中程度                   |
| **柔軟性・適応性**         | 低い                             | 高い                       | 中程度                   |
| **実装複雑度**             | 中程度                           | 中程度                     | 高い（層間契約管理必須） |
| **プロンプト改善の余地**   | Context Engineering で最適化可能 | LLM 判断を信頼する必要あり | 層別での最適化が必要     |
| **仕様変更への対応**       | 制御フロー修正が必要             | 新規 SubAgent 追加のみ     | 両者のハイブリッド対応   |
| **学習曲線**               | 中程度                           | 中程度                     | 高い                     |

## Rationale

本決定は、前節の Decision Drivers を満たすために、以下の観点から Option C（ハイブリッド / 層状アプローチ）を支持するものである。

まず、Progressive Disclosure を中核とする Context Engineering を破綻させないことが最優先である。Claude Skills / SKILL.md のレベル構造（メタデータ → 本文 → リソース）を適切なタイミングで提示するには、「どの情報をどの順序でプロンプトに載せるか」を実装側で明示的に制御できる必要がある。そのため、この制御は Workflow 層に集約し、Pure Agent は「どのスキルをいつ呼ぶか」という高レベルな意思決定に専念させる。

次に、[ADR-ckr1p Claude Skills 実装難易度カテゴリと段階導入方針](ADR-ckr1p-skill-implementation-leveling.md) で定義されたレベル 3/4 のような複雑なスキル実行では、依存関係の有無やエラー発生状況を見ながら実行経路を切り替える必要がある。これを Workflow だけで表現すると制御フローが肥大化しがちであり、変更にも弱くなる。このため、こうした「実行時に経路が変わりうる処理」は、抽象度の高い Pure Agent に任せる方が、柔軟性・保守性の両面で合理的である。ただし、Pure Agent が扱うコンテキストはスキル実行に必要な最小限の情報に絞り、詳細な指示文やリソースロードの制御は Workflow 側の責務とする。

さらに、Pure Agent の抽象度が高くなりすぎると、どの SKILL.md をいつロードするかが不透明になり、Context Engineering の可視性が失われるリスクがある。本 ADR では、その場合に Pure Agent をより小さな Workflow や SubAgent に分解することを、あらかじめリファクタリングの方向性として規定している。これにより、「最初に Pure Agent だけで作ってしまい、後から分割できない」という行き止まりを避けやすくなる。

最後に、MVP から拡張フェーズにかけての実装戦略との整合性も重要である。MVP ではコード量削減とスピードを優先し、まず Pure Agent ベースで動くものを構築する。その後、Observability の結果や運用上の知見に基づき、処理順序が安定してきた部分から順次 Workflow 化していく。この戦略は、ハイブリッド / 層状アプローチと自然に噛み合うため、本 ADR では Option C を採用している。

## Consequences

### Positive

- **柔軟性と可視性の両立**
  - 単一スキル内の複雑で可変的な処理フロー（レベル 3/4 相当）は Pure Agent に任せることで、LLM の自律性・適応性を活かせる。
  - 一方で、**Progressive Disclosure・Context Engineering は Workflow 層に集約**されるため、プロンプト改善やデバッグが行いやすい。

- **リファクタリング方針が明示されているため、設計上の行き止まりを避けやすい**
  - 「Pure Agent の抽象度が高すぎて制御不能になった場合は、抽象度を下げて Workflow へ落とす」というルールが明確であるため、「まず Pure Agent で作ってみる → 観測と評価の結果、必要に応じて Workflow に分解する」という反復サイクルを安全に回せる。

- **サーバサイド / マルチテナント環境での安定性に寄与**
  - Resource 制御や Observability は主に Workflow / Tool 実行側に寄せることで、**高レベルの Pure Agent が暴走しても、下位の Workflow レイヤで制限・監査しやすい構造**になる。

### Negative

- **設計・実装が「層状」になるため、理解コストが増える**
  - 開発者は、「この機能は Pure Agent 層で扱うべきか？」「どこまで Workflow に落とし込むべきか？」を常に意識する必要がある。
  - 不適切なレイヤリングを行うと、Pure Agent と Workflow の責務が曖昧になり、かえって複雑さが増すリスクがある。

- **短期的には、MVP 実装の計画と実際の構造がズレる可能性**
  - 当初は「ほぼ Workflow だけで実装する」計画でも、実際には「高レベル Pure Agent を先に入れた方が自然」なユースケースが出てくる可能性がある。
  - その場合、スケジュール上、Pure Agent 層の実装に工数を割く判断が必要になる。

### Neutral

- 将来的に、特定ユースケースでは **「ほぼ Pure Agent だけ」「ほぼ Workflow だけ」** といった構成も選択されうるが、それも上記ポリシーの中で「例外」として位置付けることで整合性を保てる。

## Implementation Notes

### パターン適用の基本ルール

1. **MVP ではまず Pure Agent 中心で実装する**
   - 単一スキルのエンドツーエンド実行は、原則として Pure Agent によるスキル実行から着手する。
   - [ADR-ckr1p Claude Skills 実装難易度カテゴリと段階導入方針](ADR-ckr1p-skill-implementation-leveling.md) のレベル 1/2（テキスト出力、マルチファイル生成）およびレベル 3/4（外部プロセス実行、ビルドパイプライン）も、初期実装では同様とする。
   - 実装後、トークン消費・レイテンシ・ログを観測し、処理順序が安定している部分を Workflow として切り出す候補にする。

2. **Pure Agent の責務を「動的な制御」に限定する**
   - 依存関係の確認、エラー発生時の代替手段の選択など、実行時の状況に応じてスキル実行パスを決定する。
   - コンテキストの詳細な組み立ては基本的に行わず、「どのスキルをどう組み合わせるか」という高レベルな制御に集中する。

3. **Workflow の責務として Context Engineering / Progressive Disclosure を集約する**
   - SKILL.md の frontmatter・本文・参照リソースなど、Progressive Disclosure の各レベルをどのタイミングで LLM に渡すかを Workflow 側で制御する。
   - 各ステップで使用したコンテキストをログとして残し、Observability から改善ポイントを抽出できるように実装する。

4. **リファクタリングのトリガを明示しておく**
   - Pure Agent のプロンプトやコンテキストが肥大化し、SKILL.md / リソースのロードタイミングが不透明になってきた場合は、Workflow や SubAgent への分割を検討する。
   - 具体的な判断例は、Examples セクションの「リファクタリングの判断基準例」を参照する。

### Phase 設計のガイド（MVP 〜 拡張）

- **Phase 1（MVP）**
  - 重点：単一スキルのエンドツーエンド実行、Progressive Disclosure・Observability の確立。
  - 対象：[ADR-ckr1p Claude Skills 実装難易度カテゴリと段階導入方針](ADR-ckr1p-skill-implementation-leveling.md) のレベル 1/2。
  - 方針：上記基本ルール 1〜3 に従い、Pure Agent を起点に実装し、安定した部分から Workflow 化を検討する。

- **Phase 2 以降**
  - 重点：同 ADR のレベル 3/4（外部プロセス実行、ビルドパイプライン等）の実装。
  - 方針：Phase 1 と同様に Pure Agent を起点としつつ、基本ルール 4 に基づき、抽象度が高すぎる部分を積極的に Workflow / SubAgent に分解する。
  - この段階で、本 ADR で定めたパターン適用方針をユースケースごとに再確認し、必要に応じて細部を更新する。

## Examples

### 複雑なスキル実行における Pure Agent + Workflow の構成例

```text
[Pure Agent: ビルドパイプライン実行制御]
  ↓ （実行時の状況を判断：依存チェック、エラー検出など）
[Workflow: 依存解決]  → npm install / pip install
  ↓
[Workflow: ビルド実行]  → webpack / tsc
  ↓（エラー発生時は Pure Agent が代替手段を選択）
[Workflow: テスト実行]  → jest / pytest
```

- Pure Agent は、実行時の状況（依存の有無、エラー発生時の代替手段など）に応じた動的な判断を担当
- 各 Workflow は、自身の SKILL.md / 参照リソースを Progressive Disclosure の方針でロードし、Context Engineering を行う

### リファクタリングの判断基準例

以下のいずれかを満たす場合、「Pure Agent の抽象度が高すぎる」と判断し、Workflow への分解を検討する：

- Pure Agent 内のプロンプトが肥大化し、SKILL.md / リソースのロードタイミングが不透明になっている
- Observability 上、どのステップでどのコンテキストが使われたかが追跡しづらい
- トークン消費やレイテンシのばらつきが大きく、改善の手がかりを得づらい

## Open Questions

- [ ] 複雑なスキル実行（レベル 3/4）において、Pure Agent の責務をどこまで広げてよいか（どの程度までなら Context Engineering を Pure Agent 側に寄せてよいか） → Next step: PoC 実装による検証と docs/tasks/T-xxxx-<task>/design.md での設計固め
- [ ] Pure Agent と Workflow の分割をどうレビュー・合意形成するか（設計レビューの観点・チェックリスト） → Method: 設計レビューガイドラインの策定
- [ ] 複数スキルのカタログ選択を行う Supervisor 層（高レベル Pure Agent）の設計方針 → Next step: Phase 2 以降の拡張設計で検討

---

## Template Usage

For detailed instructions on using this template, see [Template Usage Instructions](../templates/README.md#adr-templates-adrmd-and-adr-litemd) in the templates README.
