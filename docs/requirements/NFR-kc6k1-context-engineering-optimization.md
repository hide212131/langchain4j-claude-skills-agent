# NFR-kc6k1 Context Engineering 最適化

## Metadata

- Type: Non-Functional Requirement
- Status: Draft
  <!-- Draft: Under discussion | Approved: Ready for implementation | Rejected: Decision made not to pursue this requirement -->

## Links

- Prerequisite Requirements:
  - [FR-uu07e Progressive Disclosure 実装](../requirements/FR-uu07e-progressive-disclosure.md)
- Dependent Requirements:
  - N/A – 後続要件なし
- Related Tasks:
  - N/A – タスクは要件承認後に作成

## Requirement Statement

> Focus the requirement on the problem to solve and the desired outcome, remaining independent of any specific implementation approach.

エージェントが限られたトークン予算内で最適な性能を出せるよう、Progressive Disclosure などのコンテキスト最適化を適用し、効率と精度を測定・改善できること。

## Rationale

複数スキル運用ではコンテキスト肥大がボトルネックになるため、段階的ロードや圧縮を通じてトークン効率を確保し、パフォーマンスを測定する必要がある。

## User Story (if applicable)

The system shall コンテキスト最適化を適用し効率を計測できる, to ensure トークンコストと精度のバランスを維持する。

## Acceptance Criteria

- [ ] Progressive Disclosure の適用有無でトークン消費を比較でき、削減率を記録できること。
- [ ] レイテンシと精度への影響をメトリクスとして収集できること。
- [ ] 複数アーキテクチャパターン（Workflow/Pure Agent）のトークン消費と性能を比較できる計測があること。

## Technical Details (if applicable)

### Non-Functional Requirement Details

- トークン消費: Progressive Disclosure 適用時に 30～50% 削減を目標とする。
- 性能: 計測対象のステップとメトリクスを FR-hjz63/NFR-30zem に送出する。

## Platform Considerations

### Unix

`N/A – Platform agnostic`

### Windows

`N/A – Platform agnostic`

### Cross-Platform

`N/A – Platform agnostic`

## Risks & Mitigation

| Risk                         | Impact | Likelihood | Mitigation                         | Validation                     |
| ---------------------------- | ------ | ---------- | ---------------------------------- | ------------------------------ |
| 測定不足による最適化効果不明 | Medium | Medium     | 計測ポイントと指標をテンプレート化 | 観測データの定期レビューを実施 |

## Implementation Notes

- AN-f545x の NFR-DRAFT-1 を正式化。Progressive Disclosure（FR-uu07e）と Observability（FR-hjz63, NFR-30zem）を前提とする。
- ADR-mpiub の方針に従い、段階的ロードを評価指標として計測する。

## External References

- N/A – No external references

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](../templates/README.md#individual-requirement-template-requirementsmd) in the templates README.
