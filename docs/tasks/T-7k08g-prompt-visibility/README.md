# T-7k08g プロンプト・エージェント可視化タスク

## Metadata

- Type: Task
- Status: Draft
  <!-- Draft: Under discussion | In Progress: Actively working | Complete: Code complete | Cancelled: Work intentionally halted -->

## Links

- Related Analyses:
  - [AN-f545x-claude-skills-agent](../../analysis/AN-f545x-claude-skills-agent.md)
- Related Requirements:
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../../requirements/FR-hjz63-prompt-visibility-framework.md)
  - [NFR-30zem Observability 統合](../../requirements/NFR-30zem-observability-integration.md)
- Related ADRs:
  - [ADR-q333d Agentic パターン選定](../../adr/ADR-q333d-agentic-pattern-selection.md)
  - [ADR-ij1ew Observability 統合](../../adr/ADR-ij1ew-observability-integration.md)
- Associated Design Document:
  - [T-7k08g-prompt-visibility-design](./design.md)
- Associated Plan Document:
  - [T-7k08g-prompt-visibility-plan](./plan.md)

## Summary

SKILL.md パースからエージェントの Plan/Act/Reflect 実行、生成物出力までに発生するプロンプト・内部状態・メトリクスを可視化する仕組みを設計・実装する。

## Scope

- In scope: SKILL パース結果の記録、LLM 送信プロンプトと応答の取得、AgenticScope コンテキスト/履歴の追跡、入出力パラメータとメトリクスの構造化ログ化、LangFuse/OTLP 連携の設計方針整理。
- Out of scope: 複数スキル連鎖や複雑分岐の実装そのもの（FR-2ff4z/FR-cccz4 に委譲）、本番環境へのデプロイ手順詳細、UI ダッシュボード実装。

## Success Metrics

- プロンプトと AgenticScope 状態を工程別に追跡できるログ/トレースを出力し、FR-hjz63 受け入れ基準を満たす。
- ローカル観測（LangFuse）とクラウド観測（OTLP/AI Insights）へ同一スキーマで送出できる設計を確立する。
- エラー/マスキング要件を含む可視化の安全性を計画に盛り込み、NFR-30zem/NFR-mt1ve の観点を満たす。

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](../../templates/README.md#task-template-taskmd) in the templates README.
