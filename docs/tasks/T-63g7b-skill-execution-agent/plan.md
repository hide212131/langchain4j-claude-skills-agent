# T-63g7b スキル実行エージェント計画

## Metadata

- Type: Implementation Plan
- Status: Phase 4 In Progress
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
- Phase 2 – 実行計画作成
- Phase 3 – スキル実行とリトライ制御
- Phase 4 – 可視化連携とテスト強化

### Phase Status Tracking

Mark checkboxes (`[x]`) immediately after completing each task or subtask. If an item is intentionally skipped or deferred, annotate it (e.g., strike-through with a brief note) instead of leaving it unchecked.

---

## 全Phase共通の疎通確認事項

#### PPTX-001 確認手順（実行計画作成〜LangFuse確認）

```bash
./gradlew :app:installDist
set -a; source .env; set +a; app/build/install/skills/bin/skills run --skill=build/skills/anthropics/skills/pptx/SKILL.md --goal "PPTX-001 以下のファイルのテキスト抽出してください。" --input-file app/src/test/resources/skills/pptx/langchain4j_presentation.pptx --llm-provider=openai
set -a; source .env; set +a; ./gradlew langfuseReport -Plimit=5
set -a; source .env; set +a; ./gradlew langfusePrompt -PtraceId="<traceId>"
```

- 実行結果: `./build/langchain4j_presentation.md` を取得済み（`--output-dir ./build` 指定でダウンロード確認）

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

- [x] **設計境界の確定**
  - [x] 公開 API（入力/出力/制約）の確定
  - [x] 実行エンジンとの責務分離をコードで表現
- [x] **基盤構造の配置**
  - [x] パッケージ配置を docs/architecture.md に準拠して決定
  - [x] 主要インターフェース/データ構造を追加

### Deliverables

- スキル実行エージェントの基盤 API
- ExecutionTaskList/ExecutionTask/ExecutionResult のデータ構造

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

## Phase 2: 実行計画作成

### Phase 2 Goal

- ExecutionPlanningAgent による実行計画作成を確立する

### Phase 2 Inputs

- Dependencies:
  - Phase 1: 実行計画モデル
  - T-8z0qk: コード実行エンジンの呼び出しインターフェース
- Source Code to Modify:
  - /src/... – 実行計画生成と情報収集

### Phase 2 Tasks

- [x] **実行計画の生成**
  - [x] ExecutionPlanningAgent によるゴール/スキル解析と分岐選択
  - [x] LocalResourceTool の tool calling によるローカル参照の取得
  - [x] ExecutionEnvironmentTool によるリモート環境確認（ファイル存在確認など）
  - [x] ExecutionTaskList/ExecutionTask（ステータス込み）の生成

### Phase 2 Deliverables

- 実行計画生成ロジック
- ExecutionTaskList/ExecutionTask の生成

### Phase 2 Verification

```bash
./gradlew check
./gradlew test --tests "<suite or package>"
# Optional: broader runs
./gradlew test
```

### Phase 2 Acceptance Criteria

- 実行計画が要件どおりに構築され、タスクリストが生成される
- PPTX-001 の実行計画が作成される

### Phase 2 Rollback/Fallback

- 既存の簡易実行経路にフォールバック

---

## Phase 3: スキル実行とリトライ制御

### Phase 3 Goal

- PlanExecutorAgent によるスキル実行とリトライ制御を確立する

### Phase 3 Tasks

- [x] **スキル実行**
  - [x] 実行計画開始前に入力ファイルがあれば CodeExecutionEnvironment.uploadFile() を呼ぶ
  - [x] PlanExecutorAgent によるタスクリスト実行
  - [x] ExecutionEnvironmentTool によるコマンド実行
  - [x] ステータス更新（未実施/実行中/異常終了/完了）
  - [x] スキル実行終了後、出力がファイルなら出力フォルダにダウンロードする
  - [x] スキル実行終了後、出力が標準出力なら CLI の標準出力に出力する
- [x] **リトライ制御**
  - [x] 異常終了時のエラー状況付与とリトライ
  - [x] リトライ失敗時のスキル異常終了/計画修正の分岐
- [x] **入力ファイル属性の追加**
  - [x] CLI の goal と同じレイヤで inputFilePath を受け取る
- [x] **出力フォルダ属性の追加**
  - [x] CLI の goal と同じレイヤで outputDirectoryPath を受け取る

### Phase 3 Deliverables

- スキル実行ロジック
- inputFilePath と事前アップロードの取り扱い
- outputDirectoryPath と出力成果物の持ち帰り方針
- リトライ制御の実装方針

### Phase 3 Verification

```bash
./gradlew check
./gradlew test --tests "<suite or package>"
# Optional: broader runs
./gradlew test
```

### Phase 3 Acceptance Criteria

- ステータス更新とリトライ制御が期待どおりに動作する

### Phase 3 Rollback/Fallback

- 既存の簡易実行経路にフォールバック

---

## Phase 4: 可視化連携とテスト強化

### Phase 4 Goal

- 観測性イベントとテストの網羅性を高める

### Phase 4 Tasks

- [x] Test utilities
  - [x] 可視化イベント検証のヘルパー
  - [x] 代表的なスキル入力のフィクスチャ
- [x] Scenarios
  - [x] Happy path
  - [x] Error handling
  - [x] Edge cases
- [ ] pptxスキルテスト計画のケース対応
  - [x] PPTX-001 テキスト抽出のみ（markitdown経由）
  - [ ] PPTX-002 ノート・コメント要求（XML unpackへ分岐）
  - [ ] PPTX-003 スクリプトのパス揺れ耐性（findで解決）
  - [ ] PPTX-004 新規作成（テンプレなし、HTML→PPTX）
  - [ ] PPTX-005 新規作成＋サムネイル検証（thumbnail.py）
  - [ ] PPTX-006 既存pptx編集（最小変更）＋validate＋pack
  - [ ] PPTX-007 validate失敗時の停止と再実行
  - [ ] PPTX-008 テーマ/フォント/色の抽出（theme1.xmlの参照）
  - [ ] PPTX-009 テンプレ利用の新規作成（抽出＋棚卸し）
  - [ ] PPTX-010 テンプレ利用の新規作成（複製→差し替え→検証）
- [x] pptxスキルテスト計画（`docs/tasks/T-63g7b-skill-execution-agent/pptx-skill-test-plan.md`）を基準にE2E観点を整理
- [x] Concurrency & cleanup
  - [x] Boundary conditions
  - [x] Resource cleanup

### Phase 4 Deliverables

- 新規実装の包括的テスト
- 既知の制約とフォローアップの記録

### Phase 4 Verification

```bash
./gradlew check
# Full test suite (consider runtime)
./gradlew test
```

### Phase 4 Acceptance Criteria

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
