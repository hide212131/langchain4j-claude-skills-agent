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
   │  ├─ SkillResolver (SKILL.md / frontmatter)
   │  ├─ ExecutionPlanner (分岐・手順決定)
   │  ├─ ResourceLoader (Progressive Disclosure)
   │  ├─ ToolInvoker (Code Execution Engine 連携)
   │  └─ TraceEmitter (可視化/観測性)
```

### Components

- SkillExecutionAgent: スキル実行の司令塔。入力を受け取り、実行計画と実行順序を管理する
- SkillResolver: SKILL.md と参照リソースの所在を解決する
- ExecutionPlanner: 条件分岐と手順を評価し、実行ステップを構築する
- ResourceLoader: Progressive Disclosure ルールに従い、必要時のみリソースを取得する
- ToolInvoker: T-8z0qk の CodeExecutionEnvironment を呼び出し、スクリプト/コマンド実行を行う
- TraceEmitter: FR-hjz63/NFR-30zem の要求に従いイベントを発行する

### Data Flow

- 1. リクエストを受け取る
- 2. SkillResolver が SKILL.md を読み込み、エントリポイントを決定
- 3. ExecutionPlanner が条件分岐を評価し、ステップ列を生成
- 4. ResourceLoader が必要リソースのみ取得
- 5. ToolInvoker がコード実行エンジン（T-8z0qk）に実行依頼し、結果を収集
- 6. TraceEmitter が各ステップを可視化・観測性へ送信

### Storage Layout and Paths (if applicable)

- N/A – 実装段階で具体化

### API/Interface Design (if applicable)

Usage

```bash
SkillExecutionAgent.execute(skillId, input, context)
```

- skillId: 実行対象のスキル識別子
- input: ユーザー入力/指示
- context: 実行制約（リソース、セキュリティ、観測）

Implementation Notes

- 実行エンジンは ADR-ehfcj の API を利用する
- コード実行は T-8z0qk の CodeExecutionEnvironment を利用する
- CodeExecutionEnvironment の API 仕様は T-8z0qk とすり合わせながら段階的に確定する
- 可視化イベントは ADR-ij1ew のフォーマットに従う

### Data Models and Types

- SkillExecutionPlan: ステップ列と分岐条件
- ExecutionStep: 実行タイプ（リソース取得/スクリプト/プロンプト）とメタ情報
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
