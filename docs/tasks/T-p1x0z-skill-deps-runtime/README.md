# T-p1x0z 複雑スキル依存バンドルタスク

## Metadata

- Type: Task
- Status: Draft
  <!-- Draft: Work in progress | Approved: Ready for implementation | Rejected: Not moving forward with this task -->

## Links

- Related Requirements:
  - [FR-cccz4 単一スキルの複雑手続き実行](../../requirements/FR-cccz4-single-skill-complex-execution.md)
- Related ADRs:
  - [ADR-38940 セキュリティ・リソース管理フレームワーク](../../adr/ADR-38940-security-resource-management.md)
  - [ADR-ehfcj スキル実行エンジン設計](../../adr/ADR-ehfcj-skill-execution-engine.md)
- Task Documents:
  - [Design](./design.md)
  - [Plan](./plan.md)

## Summary

FR-cccz4 に必要な複雑スキル（例: skills/pptx）の依存を、実行時インストールなしで扱うための「事前ビルド（依存バンドルイメージ）」を設計・実装する。SKILL.md の依存宣言をパースし、汎用プロファイルにマッピングしてベースイメージを選択・検証する。ランタイム統合は別タスクで実施する。
Java 実装は `io.github.hide212131.langchain4j.claude.skills.bundle` に配置する。

## Status

- Design: Draft
- Plan: Draft

## Outcomes

- SKILL.md 依存パーサとプロファイルマッピングの仕様・実装
- 事前ビルドされたベースイメージ（ローカル/Docker, 本番/ACADS）選択の仕組み
- CI 依存検証ジョブで未充足を検知し、実行前に拒否できること
