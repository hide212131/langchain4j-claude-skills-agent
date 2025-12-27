# FR-cccz4 単一スキルの複雑手続き実行

## Metadata

- Type: Functional Requirement
- Status: Approved
  <!-- Draft: Under discussion | Approved: Ready for implementation | Rejected: Decision made not to pursue this requirement -->

## Links

- Prerequisite Requirements:
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../requirements/FR-hjz63-prompt-visibility-framework.md)
- Dependent Requirements:
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
  - [FR-uu07e Progressive Disclosure 実装](../requirements/FR-uu07e-progressive-disclosure.md)
- Related Tasks:
  - [T-p1x0z 複雑スキル依存バンドル・実行分離](../tasks/T-p1x0z-skill-deps-runtime/README.md)
  - [T-8z0qk 複雑スキルランタイム統合](../tasks/T-8z0qk-runtime-execution/README.md)

## Requirement Statement

> Focus the requirement on the problem to solve and the desired outcome, remaining independent of any specific implementation approach.

条件分岐・参照リソース・スクリプト実行を含む複雑な SKILL.md を、単一スキルとして実行し、バイナリ生成物まで出力できること。

## Rationale

簡易スキルだけでは実運用の複雑性をカバーできず、スクリプト実行や外部リソース参照を伴う高度な手順書を扱う基盤が必要なため。

## User Story (if applicable)

As a Java エージェント利用者, I want 条件分岐や外部リソースを含むスキルを安全に実行したい, so that 高度な生成手順を自動化できる。

## Acceptance Criteria

- [ ] 条件分岐や参照リソースを含む SKILL.md（例: Anthropics `skills/pptx`）をパースし、フロントマター・本文・参照ファイル（`html2pptx.md`、`ooxml.md` など）を必要時のみロードできること。
- [ ] LLM が SKILL.md 内の分岐条件に従って実行経路を選択し、必要なスクリプト・リソースを段階的に取得できること。
- [ ] Python/Bash 等のスクリプトをエージェント経由で実行し、標準出力・エラー・終了コードを取得できること。
- [ ] `skills/pptx` の「テンプレートなしで新規作成」ワークフロー（`html2pptx` → `html2pptx.js` → `scripts/thumbnail.py`）をエンドツーエンドで実行し、pptx 本体とサムネイル画像が生成されることを確認すること。
- [ ] `skills/pptx` の「既存 PPTX 編集」ワークフロー（`ooxml/scripts/unpack.py` → 編集 → `validate.py` → `pack.py`）を実行し、バリデーション成功まで到達すること。
- [ ] スクリプト実行やリソース取得の全ステップが FR-hjz63 の可視化フレームワークに追跡されること。

## Technical Details (if applicable)

### Functional Requirement Details

- LangChain4j Code Execution Engines を利用して外部スクリプトの実行を制御する。
- スクリプト参照やリソース取得は Progressive Disclosure（FR-uu07e）に従い、必要時のみロードする。

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

| Risk                                   | Impact | Likelihood | Mitigation                                                          | Validation                               |
| -------------------------------------- | ------ | ---------- | ------------------------------------------------------------------- | ---------------------------------------- |
| スクリプト実行によるセキュリティリスク | High   | Medium     | NFR-kc6k1, NFR-3gjla に準拠したサンドボックス化・クォータ管理を適用 | 侵入テストとリソース上限制御テストで確認 |
| バイナリ出力の整合性不足               | Medium | Medium     | テストデータでハッシュ検証やスナップショット比較を実施              | E2E テストで成果物を検証                 |

## Implementation Notes

- AN-f545x の FR-DRAFT-2 を正式化。実行エンジンとセキュリティ制約は ADR-ehfcj と ADR-38940 に準拠する。
- Progressive Disclosure（FR-uu07e）を前提に、不要なリソースはロードしない。

## External References

- N/A – No external references

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](../templates/README.md#individual-requirement-template-requirementsmd) in the templates README.
