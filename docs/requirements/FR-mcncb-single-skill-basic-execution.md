# FR-mcncb 単一スキルの簡易実行

## Metadata

- Type: Functional Requirement
- Status: Approved
  <!-- Draft: Under discussion | Approved: Ready for implementation | Rejected: Decision made not to pursue this requirement -->

## Links

- Prerequisite Requirements:
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../requirements/FR-hjz63-prompt-visibility-framework.md)
- Dependent Requirements:
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
  - [FR-uu07e Progressive Disclosure 実装](../requirements/FR-uu07e-progressive-disclosure.md)
- Related Tasks:
  - [T-0mcn0 最小実行基盤タスク](../tasks/T-0mcn0-minimal-foundation/README.md)
  - [T-7k08g プロンプト・エージェント可視化タスク](../tasks/T-7k08g-prompt-visibility/README.md)

## Requirement Statement

> Focus the requirement on the problem to solve and the desired outcome, remaining independent of any specific implementation approach.

単一の SKILL.md（分岐や参照リソースを含まない簡易手続き）をパースし、Java エージェントが LLＭ を用いてテキスト生成物を出力できる end-to-end 実行経路を提供すること。

## Rationale

Claude Skills 仕様に基づく最小限の実行パスを確立しなければ、後続の複雑スキル実行や複数スキル連鎖の品質を検証できないため。

## User Story (if applicable)

As a LangChain4j ユーザー, I want SKILL.md で定義した簡易スキルを実行してテキスト成果物を得る, so that Java 環境で Claude Skills の基本動作を確認できる。

## Acceptance Criteria

- [ ] YAML frontmatter + 簡易 Markdown 本文を JSON Schema/POJO にパースできること。
- [ ] スキル実行インターフェースが入力パラメータを受け取り、LLM へのプロンプトを生成できること。
- [ ] 実行結果としてテキスト生成物を出力できること。
- [ ] パース～実行～出力のフルサイクルが FR-hjz63 の可視化で追跡可能であること。
- [ ] テスト用スキルで end-to-end の成功パターンを確認できること。

## Technical Details (if applicable)

### Functional Requirement Details

- LangChain4j Agentic API（Workflow/Pure Agent いずれか）で単一スキルの Plan/Act/Reflect フローを構成する。
- SKILL.md の schema バリデーション、POJO 変換、プロンプト生成を含む。

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

| Risk                           | Impact | Likelihood | Mitigation                                                   | Validation                             |
| ------------------------------ | ------ | ---------- | ------------------------------------------------------------ | -------------------------------------- |
| スキル定義のバリデーション漏れ | Medium | Medium     | JSON Schema と型安全な POJO を併用し、必須項目を検証         | パース単体テストで例外を確認           |
| LLM 依存による結果不安定       | Medium | Medium     | システムプロンプト固定化とテストスキルで決定論的な入力を用意 | LLM への同条件リクエストで安定性を測定 |

## Implementation Notes

- AN-f545x の FR-DRAFT-1 を正式化。実行エンジン設計は ADR-ehfcj に従う。
- 可視化要件（FR-hjz63）を満たすログ出力を同時に整備し、後続の複雑スキル検証を容易にする。

## External References

- N/A – No external references

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](../templates/README.md#individual-requirement-template-requirementsmd) in the templates README.
