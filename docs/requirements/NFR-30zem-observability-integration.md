# NFR-30zem Observability 統合

## Metadata

- Type: Non-Functional Requirement
- Status: Draft
  <!-- Draft: Under discussion | Approved: Ready for implementation | Rejected: Decision made not to pursue this requirement -->

## Links

- Prerequisite Requirements:
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../requirements/FR-hjz63-prompt-visibility-framework.md)
- Dependent Requirements:
  - [NFR-mck7v 漸進的開発・評価サイクル支援](../requirements/NFR-mck7v-iterative-metrics-evaluation.md)
- Related Tasks:
  - N/A – タスクは要件承認後に作成

## Requirement Statement

> Focus the requirement on the problem to solve and the desired outcome, remaining independent of any specific implementation approach.

ローカル開発では LangFuse、クラウド本番では Azure Application Insights を中心とした Observability 基盤を統合し、OTLP による標準化を行うこと。

## Rationale

開発と本番で同一の観測指標を用い、プロンプト可視化・メトリクス収集・トレーシングを統一することで継続的改善を可能にするため。

## User Story (if applicable)

The system shall LangFuse と Azure Application Insights を使った観測を提供する, to ensure 開発速度と本番監視の両立が可能になる。

## Acceptance Criteria

- [ ] OTLP（OpenTelemetry）を用いてトレース/メトリクス/ログを送信できること。
- [ ] ローカル環境で LangFuse にプロンプト履歴とモデル比較を送信できること。
- [ ] クラウド本番で Azure Application Insights にカスタムイベント・メトリクス・ログを送信できること。
- [ ] 環境別の設定切替を行い、共通の観測スキーマで両環境のデータを比較できること。

## Technical Details (if applicable)

### Non-Functional Requirement Details

- Development Quality/Operations: LangFuse と Azure を切り替える構成管理を実装。
- Compatibility: OTLP スキーマを共通化し、FR-hjz63 の可視化対象を両環境に送出する。

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
| 環境差異による計測ギャップ | Medium | Medium | 送信スキーマとメトリクス名を統一し、変換レイヤーを実装 | 両環境で同一トレースを比較検証 |
| 観測コストの増加 | Medium | Medium | サンプリングやログレベルを環境別に設定 | コストモニタリングで調整 |

## Implementation Notes

- AN-f545x の NFR-DRAFT-5 を正式化。ADR-ij1ew の統合戦略と ADR-ae6nw の AgenticScope 利用シナリオに従う。
- FR-hjz63 の収集項目を OTLP へ送出し、LangFuse/AI Insights で同一指標を確認できるようにする。

## External References

- N/A – No external references

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](README.md#individual-requirement-template-requirementsmd) in the templates README.
