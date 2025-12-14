# T-0mcn0 最小実行基盤タスク

## Metadata

- Type: Task
- Status: Draft
  <!-- Draft: Under discussion | In Progress: Actively working | Complete: Code complete | Cancelled: Work intentionally halted -->

## Links

- Related Analyses:
  - [AN-f545x-claude-skills-agent](../../analysis/AN-f545x-claude-skills-agent.md)
- Related Requirements:
  - [FR-mcncb 単一スキルの簡易実行](../../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../../requirements/FR-hjz63-prompt-visibility-framework.md)
  - [NFR-mt1ve エラーハンドリングと堅牢性](../../requirements/NFR-mt1ve-error-handling-resilience.md)
- Related ADRs:
  - [ADR-ehfcj スキル実行エンジン設計](../../adr/ADR-ehfcj-skill-execution-engine.md)
  - [ADR-q333d Agentic パターン選定](../../adr/ADR-q333d-agentic-pattern-selection.md)
  - [ADR-ae6nw AgenticScope 活用シナリオ](../../adr/ADR-ae6nw-agenticscope-scenarios.md)
  - [ADR-ij1ew Observability 統合](../../adr/ADR-ij1ew-observability-integration.md)
- Associated Design Document:
  - [T-0mcn0-minimal-foundation-design](./design.md)
- Associated Plan Document:
  - [T-0mcn0-minimal-foundation-plan](./plan.md)

## Summary

最初の実装ステップとして、SKILL.md の最小パース、ダミー LLM 応答での Plan/Act/Reflect スタブ、Gradle ビルド環境、最低限の可視化・エラーハンドリングの足場を用意し、以降のタスクの土台を整える。

## Scope

- In scope: SKILL.md パーサの骨格、ダミー LLM での最小 Agentic フロー、Gradle ビルド/依存追加、基本ログ出力（可視化プレースホルダ）、例外捕捉と単回リトライの枠組み。
- Out of scope: 実際の LLM 呼び出し、LangFuse/OTLP 実送信、高度な可視化・メトリクス収集、複数スキル連鎖や複雑手続き。

## Success Metrics

- 単一 SKILL.md を読み込み、固定応答で Plan/Act/Reflect スタブが完走する。
- 可視化イベント（プレースホルダ）が各ステップで出力される。
- `./gradlew check/test/build` が通るビルド環境が整う。
- 例外捕捉とリトライ枠組みが存在し、ログが日本語で出力される。

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](../../templates/README.md#task-template-taskmd) in the templates README.
