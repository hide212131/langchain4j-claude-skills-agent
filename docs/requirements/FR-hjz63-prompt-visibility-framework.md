# FR-hjz63 プロンプト・エージェント可視化フレームワーク

## Metadata

- Type: Functional Requirement
- Status: Approved
  <!-- Draft: Under discussion | Approved: Ready for implementation | Rejected: Decision made not to pursue this requirement -->

## Links

<!-- Internal project artifacts only. Replace or remove bullets as appropriate. -->

- Prerequisite Requirements:
  - N/A – 事前条件となる要件なし
- Dependent Requirements:
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
  - [FR-uu07e Progressive Disclosure 実装](../requirements/FR-uu07e-progressive-disclosure.md)
  - [NFR-30zem Observability 統合](../requirements/NFR-30zem-observability-integration.md)
- Related Tasks:
  - N/A – タスクは要件承認後に作成

## Requirement Statement

> Focus the requirement on the problem to solve and the desired outcome, remaining independent of any specific implementation approach.

SKILL.md パースからエージェントの Plan/Act/Reflect 実行、生成物出力までの各ステップにおけるプロンプト内容と内部状態を、開発者が追跡・分析できる可視化フレームワークを提供すること。

## Rationale

LLM に送信されるプロンプトや AgenticScope の状態を可視化しないと、スキル実行の正確性・効率性を検証できず、他要件（スキル実行や Progressive Disclosure）の品質保証が困難になるため。

## User Story (if applicable)

As a Java エージェント開発者, I want プロンプトとエージェント内部状態のログ・ダッシュボードを得る, so that スキル実行の挙動を検証し改善できる。

## Acceptance Criteria

- [ ] SKILL.md パースの入力ファイル、解析ステップ、生成されたモデル（POJO）を全て記録できること。
- [ ] LLM に送信されるプロンプト（システム/ユーザー/コンテキスト）を完全に取得し、時系列で確認できること。
- [ ] AgenticScope 内のコンテキスト、メモリ、実行履歴を可視化する手段を提供すること。
- [ ] Plan/Act/Reflect の各ステップでの LLM 応答とエージェント決定を追跡できること。
- [ ] スキル実行時の入力・出力パラメータが記録され、生成物と紐付けて参照できること。
- [ ] トークン消費、レイテンシ、エラー率などの主要メトリクスを収集し、他要件の検証に利用できること。

## Technical Details (if applicable)

### Functional Requirement Details

- 可視化対象は SKILL.md パース結果、プロンプト、AgenticScope 状態、Plan/Act/Reflect ステップ、スキル入出力、メトリクスを含む。
- ログ/トレースの出力形式は他 Observability 手段（LangFuse、OTLP）と連携可能な構造化形式を想定する。

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
| ログ出力の機密情報露出 | High | Medium | マスキング・フィルタリングを実装し、収集範囲を最小化 | セキュリティレビューとテストデータで検証 |
| 収集項目過多による性能劣化 | Medium | Medium | 収集レベルを設定可能にし、開発/本番で制御 | 負荷テストでオーバーヘッドを計測 |

## Implementation Notes

- AN-f545x で提案された「プロンプト・エージェント状態の可視化フレームワーク」を正式化した要件。Observability 統合（NFR-30zem, NFR-mck7v）と組み合わせ、ダッシュボード/ログ基盤へ送信する。
- LangChain4j の AgenticScope と連携し、Plan/Act/Reflect のステップを区別して記録する設計を優先する。

## External References

<!-- Only external resources. Internal documents go in Links section -->

- N/A – No external references

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](README.md#individual-requirement-template-requirementsmd) in the templates README.
