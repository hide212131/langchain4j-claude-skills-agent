# T-63g7b スキル実行エージェント設計

## Metadata

- Type: Task
- Status: Draft
  <!-- Draft: Under discussion | In Progress: Actively working | Complete: Code complete | Cancelled: Work intentionally halted -->

## Links

- Related Analyses:
  - N/A – 既存要件から直接タスク化
- Related Requirements:
  - [FR-cccz4 単一スキルの複雑手続き実行](../../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-uu07e Progressive Disclosure 実装](../../requirements/FR-uu07e-progressive-disclosure.md)
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../../requirements/FR-hjz63-prompt-visibility-framework.md)
  - [NFR-3gjla セキュリティとリソースガバナンス](../../requirements/NFR-3gjla-security-resource-governance.md)
  - [NFR-kc6k1 コンテキストエンジニアリング最適化](../../requirements/NFR-kc6k1-context-engineering-optimization.md)
  - [NFR-30zem 観測性統合](../../requirements/NFR-30zem-observability-integration.md)
  - [NFR-mt1ve エラーハンドリングと回復性](../../requirements/NFR-mt1ve-error-handling-resilience.md)
- Related ADRs:
  - [ADR-ehfcj スキル実行エンジン](../../adr/ADR-ehfcj-skill-execution-engine.md)
  - [ADR-38940 セキュリティとリソース管理](../../adr/ADR-38940-security-resource-management.md)
  - [ADR-ij1ew 観測性統合](../../adr/ADR-ij1ew-observability-integration.md)
- Associated Design Document:
  - [T-63g7b-skill-execution-agent-design](./design.md)
- Associated Plan Document:
  - [T-63g7b-skill-execution-agent-plan](./plan.md)

## Summary

FR-cccz4 の達成に必要な「スキル実行エージェント」の責務と設計境界を整理し、実行エンジンと分離された制御・可視化・リソース取得の設計を定義する。

## Scope

- In scope: スキル実行エージェントの責務定義、実行エンジンとの境界設計、Progressive Disclosure 連携、可視化・観測性の接続方針、テスト方針
- Out of scope: 実行エンジン本体の実装、具体的なスキル（例: pptx）固有ロジックの実装

## Supporting Artifacts

- [pptxスキルテスト計画](./pptx-skill-test-plan.md)

## Success Metrics

- スキル実行エージェントの責務・入出力・依存関係が設計書で明確化されている
- 実行エンジン/可視化/リソース取得の接続点が日本語で説明され、タスク計画に落とし込まれている

---

## Template Usage

For detailed instructions and key principles, see [Template Usage Instructions](../../templates/README.md#task-template-taskmd) in the templates README.
