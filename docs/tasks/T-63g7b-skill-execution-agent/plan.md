# T-63g7b スキル実行エージェント計画

## Metadata

- Type: Implementation Plan
- Status: Draft
  <!-- Draft: Planning complete, awaiting start | Phase X In Progress: Actively working | Cancelled: Work intentionally halted before completion | Complete: All phases done and verified -->

## Links

- Associated Design Document:
  - [T-63g7b-skill-execution-agent-design](./design.md)

## Overview

スキル実行エージェントの責務分離、実行計画生成、Progressive Disclosure、可視化連携を実装可能な形に落とし込む。

## Success Metrics

- [ ] スキル実行エージェントの API と責務がコードとドキュメントで一致する
- [ ] FR-cccz4 の実行経路に必要な可視化イベントが発行される
- [ ] ステップ実行時のリソース取得が遅延ロードになっている
- [ ] All existing tests pass; no regressions in エージェント実行基盤

## Scope

- Goal: スキル実行エージェントの実装と、実行エンジン/可視化/リソース取得の連携を成立させる
- Non-Goals: スキル本体の高度化、個別スキルの内容改善
- Assumptions: 実行エンジン API は ADR-ehfcj で定義済み
- Assumptions: コード実行エンジンは T-8z0qk で提供される
- Constraints: NFR-3gjla と NFR-mt1ve の制約に従う

## ADR & Legacy Alignment

- [ ] 最新 ADR がリンクされていることを確認する（ADR-ehfcj, ADR-38940, ADR-ij1ew）
- [ ] 既存コードとの乖離があれば移行タスクに落とす

## Plan Summary

- Phase 1 – 基盤インターフェースと責務分離
- Phase 2 – 実行ステップ制御とリソース取得
- Phase 3 – 可視化連携とテスト強化

### Phase Status Tracking

Mark checkboxes (`[x]`) immediately after completing each task or subtask. If an item is intentionally skipped or deferred, annotate it (e.g., strike-through with a brief note) instead of leaving it unchecked.

---

## Phase 1: 基盤インターフェースと責務分離

### Goal

- スキル実行エージェントのコア API と責務境界を実装できる形で定義する

### Inputs

- Documentation:
  - /docs/requirements/FR-cccz4-single-skill-complex-execution.md – 要件確認
  - /docs/adr/ADR-ehfcj-skill-execution-engine.md – 実行エンジン連携
- Source Code to Modify:
  - /src/... – 実装場所の選定
- Dependencies:
  - Internal: src/... – エージェント基盤
  - Internal: T-8z0qk の CodeExecutionEnvironment – コード実行基盤
  - External: N/A – 既存依存のみ

### Tasks

- [ ] **設計境界の確定**
  - [ ] 公開 API（入力/出力/制約）の確定
  - [ ] 実行エンジンとの責務分離をコードで表現
- [ ] **基盤構造の配置**
  - [ ] パッケージ配置を docs/architecture.md に準拠して決定
  - [ ] 主要インターフェース/データ構造を追加

### Deliverables

- スキル実行エージェントの基盤 API
- 実行計画/結果のデータ構造

### Verification

```bash
# Build and checks
./gradlew check
# Focused unit tests (module-specific)
./gradlew test --tests "<suite or package>"
```

### Acceptance Criteria (Phase Gate)

- 主要 API と責務境界が設計と一致し、レビュー可能な状態

### Rollback/Fallback

- 既存 API を維持し、実行エージェントの導入を保留する

---

## Phase 2: 実行ステップ制御とリソース取得

### Phase 2 Goal

- 実行計画の生成と Progressive Disclosure を統合する

### Phase 2 Inputs

- Dependencies:
  - Phase 1: 実行計画モデル
  - T-8z0qk: コード実行エンジンの呼び出しインターフェース
- Source Code to Modify:
  - /src/... – 実行計画生成とリソース取得

### Phase 2 Tasks

- [ ] **実行計画の生成**
  - [ ] SKILL.md 解析と分岐選択
  - [ ] 実行ステップ列の生成
- [ ] **リソース取得**
  - [ ] 必要時のみ参照ファイルをロード
  - [ ] 取得結果を可視化イベントに反映

### Phase 2 Deliverables

- 実行計画生成ロジック
- 遅延ロードによるリソース取得

### Phase 2 Verification

```bash
./gradlew check
./gradlew test --tests "<suite or package>"
# Optional: broader runs
./gradlew test
```

### Phase 2 Acceptance Criteria

- 分岐と遅延ロードが正しく動作し、可視化に反映される

### Phase 2 Rollback/Fallback

- 既存の簡易実行経路にフォールバック

---

## Phase 3: 可視化連携とテスト強化

### Phase 3 Goal

- 観測性イベントとテストの網羅性を高める

### Phase 3 Tasks

- [ ] Test utilities
  - [ ] 可視化イベント検証のヘルパー
  - [ ] 代表的なスキル入力のフィクスチャ
- [ ] Scenarios
  - [ ] Happy path
  - [ ] Error handling
  - [ ] Edge cases
- [ ] pptxスキルテスト計画のケース対応
  - [ ] PPTX-001 テキスト抽出のみ（markitdown経由）
  - [ ] PPTX-002 ノート・コメント要求（XML unpackへ分岐）
  - [ ] PPTX-003 スクリプトのパス揺れ耐性（findで解決）
  - [ ] PPTX-004 新規作成（テンプレなし、HTML→PPTX）
  - [ ] PPTX-005 新規作成＋サムネイル検証（thumbnail.py）
  - [ ] PPTX-006 既存pptx編集（最小変更）＋validate＋pack
  - [ ] PPTX-007 validate失敗時の停止と再実行
  - [ ] PPTX-008 テーマ/フォント/色の抽出（theme1.xmlの参照）
  - [ ] PPTX-009 テンプレ利用の新規作成（抽出＋棚卸し）
  - [ ] PPTX-010 テンプレ利用の新規作成（複製→差し替え→検証）
- [ ] pptxスキルテスト計画（`docs/tasks/T-63g7b-skill-execution-agent/pptx-skill-test-plan.md`）を基準にE2E観点を整理
- [ ] Concurrency & cleanup
  - [ ] Boundary conditions
  - [ ] Resource cleanup

### Phase 3 Deliverables

- 新規実装の包括的テスト
- 既知の制約とフォローアップの記録

### Phase 3 Verification

```bash
./gradlew check
# Full test suite (consider runtime)
./gradlew test
```

### Phase 3 Acceptance Criteria

- 主要経路のテストが通り、可視化イベントが欠落しない

---

## Definition of Done

- [ ] `./gradlew check` (or module equivalent)
- [ ] `./gradlew test`
- [ ] Integration/perf/bench (as applicable): `N/A – 実装時に決定`
- [ ] `docs/reference.md` updated; user docs updated if user-facing
- [ ] ADRs added/updated for design decisions
- [ ] Error messages actionable and in Japanese
- [ ] Platform verification completed (if platform-touching)
- [ ] Avoid vague naming (no "manager"/"util"); avoid unchecked reflection shortcuts

## Open Questions

- [ ] 既存のスキル実行 API との互換レベルをどこまで維持するか
- [ ] 実行ステップの可視化粒度をどう統一するか
- [ ] Windows の実行制約の検証範囲

<!-- Complex investigations should spin out into their own ADR or analysis document -->
