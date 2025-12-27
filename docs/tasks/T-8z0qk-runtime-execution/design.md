# T-8z0qk ランタイム統合デザイン

## Metadata

- Type: Design
- Status: Draft

## Links

- Associated Plan Document:
  - [T-8z0qk-runtime-execution-plan](./plan.md)
- Related Requirements:
  - [FR-cccz4 単一スキルの複雑手続き実行](../../requirements/FR-cccz4-single-skill-complex-execution.md)
- Related ADRs:
  - [ADR-ehfcj スキル実行エンジン設計](../../adr/ADR-ehfcj-skill-execution-engine.md)
  - [ADR-38940 セキュリティ・リソース管理フレームワーク](../../adr/ADR-38940-security-resource-management.md)
- Related Tasks:
  - [T-p1x0z 複雑スキル依存バンドル・実行分離](../T-p1x0z-skill-deps-runtime/README.md)

## Overview

依存バンドル済みイメージを前提に、SKILL.md 依存解決後のランタイム実行フローを実装する。サンドボックス（Docker/ACADS）で外部ネットワーク遮断・実行時インストール禁止を守りつつ、FR-cccz4 の pptx ワークフロー（新規作成/既存編集）を実行・トレース可能にする。

## Success Metrics

- [ ] 依存解決済みイメージを受け取り、実行前チェックが通った場合のみサンドボックス起動する。
- [ ] 実行時に外部ネットワークを使用せず、`pip/npm install` を行わない。
- [ ] skills/pptx の新規作成/既存編集ワークフローが E2E で動作し、FR-hjz63 の可視化トレースに全ステップが記録される。

## Design Details

- 依存解決入力: T-p1x0z で決定したプロファイル名/イメージタグ。
- 実行フロー: SKILL.md 読込 → 依存解決結果チェック → サンドボックス起動（Docker/ACADS）→ `/workspace` で pptx ワークフロー実行 → 成果物/ログをカタログ化。
- ガード: 依存未充足・イメージ不整合時は即エラー。実行中の `pip/npm install` 呼び出しは禁止。
- トレース: 各ステップの cmd/exit/stdout/stderr/elapsed と成果物を FR-hjz63 に準拠して記録。

## Open Questions

- ACADS へのイメージ配布/タグ解決のインターフェース詳細。
- 失敗時の中間成果物保持ポリシーをどこまで残すか。
