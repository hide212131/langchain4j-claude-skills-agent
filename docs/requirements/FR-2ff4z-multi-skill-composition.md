# FR-2ff4z 複数スキル連鎖実行

## Metadata

- Type: Functional Requirement
- Status: Draft
  <!-- Draft: Under discussion | Approved: Ready for implementation | Rejected: Decision made not to pursue this requirement -->

## Links

- Prerequisite Requirements:
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../requirements/FR-hjz63-prompt-visibility-framework.md)
- Dependent Requirements:
  - [FR-uu07e Progressive Disclosure 実装](../requirements/FR-uu07e-progressive-disclosure.md)
- Related Tasks:
  - N/A – タスクは要件承認後に作成

## Requirement Statement

> Focus the requirement on the problem to solve and the desired outcome, remaining independent of any specific implementation approach.

複数の SKILL.md から適切なスキルを検索・選択し、スキル同士を連鎖させて最終生成物を得るエージェント実行フローを提供すること。

## Rationale

単一スキルでは解決できない複合タスクを扱うためには、スキルストア管理とスキル連携の基盤が必要となるため。

## User Story (if applicable)

As a Java エージェント利用者, I want 目的に合うスキルを自動選択し連鎖実行できる, so that 複合的な生成タスクを自律的に完了できる。

## Acceptance Criteria

- [ ] スキルストアを管理し、複数の SKILL.md を検索・参照できること。
- [ ] タスク要件からスキルを自動マッチングする仕組みを備えること。
- [ ] スキル出力を次のスキル入力として渡す連鎖実行が可能であること。
- [ ] エージェントがスキル選択・パラメータ生成・再実行判定を行えること。
- [ ] 複数スキル実行フロー全体が FR-hjz63 の可視化で監視できること。

## Technical Details (if applicable)

### Functional Requirement Details

- Supervisor/SubAgents もしくは Workflow によりスキルチェーンを制御し、AgenticScope でコンテキスト共有する。
- スキル選択ロジックはプランニング/反省サイクル（Plan/Act/Reflect）を前提とする。

### Non-Functional Requirement Details

`N/A – 機能要件の詳細に含める`

## Platform Considerations

### Unix

`N/A – Platform agnostic`

### Windows

`N/A – Platform agnostic`

### Cross-Platform

`N/A – Platform agnostic`

## Risks & Mitigation

| Risk | Impact | Likelihood | Mitigation | Validation |
| --- | --- | --- | --- | --- |
| スキル選択の誤りによる品質低下 | Medium | Medium | スキルメタデータの明確化と選択ロジックの評価指標を定義 | 可視化ログと評価メトリクスで選択結果を検証 |
| コンテキスト肥大によるトークン超過 | Medium | Medium | FR-uu07e による Progressive Disclosure を適用 | トークン使用量の計測と比較テスト |

## Implementation Notes

- AN-f545x の FR-DRAFT-3 を正式化。スキル実行エンジン設計は ADR-ehfcj、パターン選択は ADR-q333d/ADR-ae6nw を参照。
- トークン効率化のため Progressive Disclosure（FR-uu07e）を必須依存とする。

## External References

- N/A – No external references

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](README.md#individual-requirement-template-requirementsmd) in the templates README.
