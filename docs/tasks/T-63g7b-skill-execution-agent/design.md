# T-63g7b スキル実行エージェント設計

## Metadata

- Type: Design
- Status: Draft
  <!-- Draft: Work in progress | Approved: Ready for implementation | Rejected: Not moving forward with this design -->

## Links

- Associated Plan Document:
  - [T-63g7b-skill-execution-agent-plan](./plan.md)

## Overview

複雑な SKILL.md を段階的に実行する「スキル実行エージェント」の責務を定義し、実行エンジンと分離された制御・可視化・リソース取得の設計を定める。

## Success Metrics

- [ ] エージェントの責務・境界・入出力が一貫した設計として記述される
- [ ] Progressive Disclosure と可視化フレームワークの接続点が明示される
- [ ] 実装時の検証観点（テスト/観測性/セキュリティ）が計画に反映される

## Background and Current State

- Context: FR-cccz4 の達成には「実行エンジン」だけでなく、SKILL.md を解釈して段階的に行動を組み立てる制御層が必要
- Current behavior: T-8z0qk で実行エンジン中心のタスクが進行中で、スキル実行の制御設計が不足
- Pain points: 実行経路選択、リソースの遅延取得、可視化イベントの発行が設計に含まれていない
- Constraints: 既存 ADR の方針に従い、セキュリティと観測性の要求を満たす
- Related ADRs: \[/docs/adr/ADR-ehfcj-skill-execution-engine.md, /docs/adr/ADR-38940-security-resource-management.md, /docs/adr/ADR-ij1ew-observability-integration.md]

## Proposed Design

### High-Level Architecture

```text
User Request
   │
   ▼
SkillExecutionAgent
   │  ├─ ExecutionPlanningAgent (分岐・手順決定)
   │  │  └─ LocalResourceTool (SKILL.md / frontmatter / ローカル参照)
   │  ├─ ExecutionEnvironmentTool (Code Execution Engine 連携)
   │  ├─ PlanExecutorAgent (実行計画の実行と評価)
   │  └─ TraceEmitter (可視化/観測性)
```

### Components

- SkillExecutionAgent: スキル実行の司令塔。入力を受け取り、実行計画と実行順序を管理する
- ExecutionPlanningAgent: 条件分岐と手順を評価し、必要な情報収集を行ったうえで実行ステップ（タスクリスト）を構築する
- LocalResourceTool: SKILL.md とローカル参照リソースを読み込み、エントリポイントと参照先を解決する
- ExecutionEnvironmentTool: T-8z0qk の CodeExecutionEnvironment を呼び出し、スクリプト/コマンド実行とリモート環境確認を行う
- PlanExecutorAgent: タスクリストの実行、実行結果の評価、リトライ判断を担う
- TraceEmitter: FR-hjz63/NFR-30zem の要求に従いイベントを発行する

### 実行計画作成

実行計画作成は、ExecutionPlanningAgent が与えられたゴールを満たすためのタスクリストを構築するフェーズである。
「推論する」はエージェントによるLLMの推論を示す。

1. ExecutionPlanningAgent が実行計画作成に必要な前提情報として、ゴールとSKILL.md を読み、計画作成へむけ取りうるアクションを推論する。
   - ゴールは自然言語の文、添付がある場合はファイルパスとして扱う
2. ExecutionPlanningAgent がアクションを実行し、計画作成に必要な情報を収集する
   - ExecutionPlanningAgent が LocalResourceTool を tool calling し、ローカルの追加ドキュメントやその他のリソースを読む。（例: `scripts/` `references/` `assets/` `./` 上のファイルなど）
   - ExecutionEnvironmentTool を用いてリモートのコード実行環境を確認する（ファイルの存在確認など）
3. ExecutionPlanningAgent が収集結果をコンテキストに追加し、推論して、タスクリストの項目を組み立てる
   - ローカル/リモートの区別、入力情報、処理内容、出力情報を明示する
   - タスクリスト生成後、PlanExecutorAgent が実行を開始し、ExecutionEnvironmentTool がコード実行エンジン（T-8z0qk）へ実行依頼を行い、TraceEmitter が各ステップを可視化・観測性へ送信する

タスクリストの各タスクは以下の構造で記述する。

- ステータス: 未実施/実行中/異常終了/完了
- 入力情報: 自然言語の文、またはファイルパス
- 処理内容:
  - コマンド実行が必要な場合は具体的なコマンド（パスはフルパス）
- 出力情報:
  - 処理結果が標準出力かファイルか
  - ファイル出力の場合はファイルパス

### スキル実行

PlanExecutorAgent が実行計画で作成したタスクリストに従い、タスクを順次実行する。

1. 実行計画の開始前に入力ファイルの有無を確認し、存在する場合は CodeExecutionEnvironment.uploadFile() を明示的に呼ぶ
2. PlanExecutorAgent が各タスクの command 有無に従い実行手段を選択する
   - command あり: ExecutionEnvironmentTool でコマンドを実行する
   - command なし: LLM への出力情報依頼を実行する
3. PlanExecutorAgent がタスクのステータスを未実施から実行中へ更新する
4. PlanExecutorAgent が実行結果（標準出力/生成ファイルなど）をコンテキストに追加する
5. PlanExecutorAgent が成否に応じてステータスを完了/異常終了へ更新する
6. PlanExecutorAgent が異常終了の場合は、原因分析のためにエラー状況を付与して一定回数リトライする
7. PlanExecutorAgent がリトライに失敗した場合は、スキル全体を異常終了するか、実行計画自体を修正して再試行する
8. TraceEmitter により各タスクの開始/終了/失敗を可視化イベントとして送信する
9. スキル実行終了後、最終出力結果を以下の仕様で取り扱う
   - 出力がファイルの場合: 出力フォルダへダウンロードする
   - 出力が標準出力の場合: CLI の標準出力へ出力する

### Storage Layout and Paths (if applicable)

- N/A – 実装段階で具体化

### API/Interface Design (if applicable)

Usage

```bash
SkillExecutionAgent.execute(skillId, input, inputFilePath, outputDirectoryPath, context)
```

- skillId: 実行対象のスキル識別子
- input: ユーザー入力/指示
- inputFilePath: CLI の goal と同じレイヤで指定する入力ファイルのパス（任意）
- outputDirectoryPath: CLI の goal と同じレイヤで指定する出力フォルダのパス（任意、実行環境で生成されたファイル名をそのまま持ち帰るためフォルダ指定とする）
- context: 実行制約（リソース、セキュリティ、観測）

Implementation Notes

- 実行エンジンは ADR-ehfcj の API を利用する
- コード実行は T-8z0qk の CodeExecutionEnvironment を利用する
- CodeExecutionEnvironment の API 仕様は T-8z0qk とすり合わせながら段階的に確定する
- 可視化イベントは ADR-ij1ew のフォーマットに従う
- "Agent" が付くコンポーネントは LangChain4j の `dev.langchain4j.agentic` API を用いて実装する
- "Tool" が付くコンポーネントは Agent から呼ばれる `@Tool` アノテーション付きのツールとして実装する

### Data Models and Types

- SkillExecutionPlan: ステップ列と分岐条件
- SkillExecutionRequest: goal と同じレイヤで inputFilePath と outputDirectoryPath を保持する実行入力
- ExecutionTaskList: 実行計画作成フェーズで生成されるタスク一覧
- ExecutionTask: 入力情報/処理内容/出力情報/ステータスのセット（計画単位）
- ExecutionResult: 標準出力、標準エラー、終了コード、生成物

### Error Handling

- 失敗箇所と対処方法が分かる日本語のエラーメッセージを返す
- 例外は実行ステップ単位で捕捉し、可視化イベントに失敗理由を含める
- エラー分類は NFR-mt1ve に合わせる

### Security Considerations

- 実行権限・リソース制限・出力検査は ADR-38940 に従う
- スキル参照ファイルは許可パスのみに限定する

### Performance Considerations

- リソース取得は遅延ロードを基本とし、不要な I/O を避ける
- 実行ステップは逐次実行を基本とし、並列化は必要性を検証してから導入

### Platform Considerations

#### Unix

- シェル実行は POSIX 互換を前提にする

#### Windows

- 実行エンジンの対応範囲に合わせてパスとシェル差分を吸収する

#### Filesystem

- パス正規化とケース感度に注意し、ルート外アクセスを禁止する

## Alternatives Considered

1. 実行エンジンにスキル実行を統合
   - Pros: 実装箇所が少ない
   - Cons: 責務が肥大化し、可視化・制御の拡張が難しい
2. 既存エージェント層に薄いラッパーを追加
   - Pros: 変更範囲が限定的
   - Cons: 分岐・リソース取得の拡張余地が不足

Decision Rationale

- 実行エンジンと制御層を分離し、拡張可能なエージェント設計を採用する

## Migration and Compatibility

- Backward/forward compatibility: 既存の簡易スキル実行は同一 API に統合しつつ互換を維持する
- Rollout plan: フラグで段階的に切り替える
- Deprecation plan: 既存経路の非推奨化は段階的に実施

## Testing Strategy

### Unit Tests

- 実行計画の構築ロジック、分岐選択、リソース取得判定をテストする

### Integration Tests

- 擬似スキルを用いて実行ステップの連携（取得→実行→可視化）を検証する

### External API Parsing (if applicable)

- N/A – 外部 API パースは対象外

### Performance & Benchmarks (if applicable)

- N/A – 目標値は実装段階で設定

## Documentation Impact

- タスク README と設計/計画の整合性を維持する
- 実装時にユーザー向けドキュメントへの影響を確認する

## External References (optional)

- N/A – 外部参照なし

## Open Questions

- [ ] 実行計画のデータ構造を既存のものに合わせるか新設するか
- [ ] 可視化イベントの粒度（ステップ/サブステップ）をどこまで細分化するか
- [ ] Windows 実行時の制約をどのレベルまで保証するか

## Appendix

### Diagrams

```text
SkillExecutionAgent -> ExecutionPlanner -> ToolInvoker
        │                       │              │
        ▼                       ▼              ▼
  ResourceLoader          TraceEmitter     Execution Engine
```

### Examples

```bash
# 擬似フロー
SkillExecutionAgent.execute("skills/pptx", input, context)
```

### Glossary

- スキル実行エージェント: SKILL.md を解釈して実行計画を生成する制御層

---

## Template Usage

For detailed instructions on using this template, see [Template Usage Instructions](../../templates/README.md#design-template-designmd) in the templates README.
