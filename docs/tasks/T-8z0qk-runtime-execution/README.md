# T-8z0qk 複雑スキルランタイム統合タスク

## Metadata

- Type: Task
- Status: Draft
  <!-- Draft: Work in progress | Approved: Ready for implementation | Rejected: Not moving forward with this task -->

## Links

- Related Requirements:
  - [FR-cccz4 単一スキルの複雑手続き実行](../../requirements/FR-cccz4-single-skill-complex-execution.md)
- Related ADRs:
  - [ADR-ehfcj スキル実行エンジン設計](../../adr/ADR-ehfcj-skill-execution-engine.md)
  - [ADR-38940 セキュリティ・リソース管理フレームワーク](../../adr/ADR-38940-security-resource-management.md)
- Related Tasks:
  - [T-p1x0z 複雑スキル依存バンドル](../T-p1x0z-skill-deps-runtime/README.md)

## Summary

事前にバンドルされた依存イメージ（T-p1x0zで準備）を用い、SKILL.md 依存解決後にサンドボックスを起動して複雑スキル（例: skills/pptx）のランタイム実行を行う。実行時インストール禁止・外部ネットワーク遮断下で FR-cccz4 の acceptance を満たす実行フローを実装する。

## Status

- Design: Draft
- Plan: Draft

## Outcomes

- 依存解決済みのベースイメージを選択し、Docker/ACADS サンドボックスで実行できるランタイムフロー
- 依存未充足時に実行前でエラーを返すガード
- FR-cccz4 の pptx ワークフロー（新規作成・既存編集）の実行・トレースが動作すること
