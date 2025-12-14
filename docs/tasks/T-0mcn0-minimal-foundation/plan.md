# T-0mcn0 最小実行基盤計画

## Metadata

- Type: Implementation Plan
- Status: Draft
  <!-- Draft: Planning complete, awaiting start | Phase X In Progress: Actively working | Cancelled: Work intentionally halted before completion | Complete: All phases done and verified -->

## Links

<!-- Internal project artifacts only. Replace or remove bullets as appropriate. -->

- Associated Design Document:
  - [T-0mcn0-minimal-foundation-design](./design.md)

## Overview

SKILL.md パース、ダミー LLM を用いた最小 Plan/Act/Reflect スタブ、Gradle ビルド環境、可視化/エラー処理のプレースホルダを構築し、後続タスクの検証基盤を整える。

## Success Metrics

- [ ] サンプル SKILL.md を使った e2e 実行で固定テキストが得られる。
- [ ] Plan/Act/Reflect 各ステップで可視化プレースホルダログが出力される。
- [ ] 例外時に日本語ログとリトライ枠組みが動作する。
- [ ] `./gradlew check/test/build` が成功。

## Scope

- Goal: 最小実行パスとビルド環境を用意し、可視化/エラー処理の足場を作る。
- Non-Goals: 本番 LLM 連携、LangFuse/OTLP 実送信、複数スキルや複雑分岐の実装。
- Assumptions: LangChain4j 依存を導入可能、テスト用 SKILL.md を作成可能。
- Constraints: 日本語ログ、決定論的テスト、ミニマル構成。

## ADR & Legacy Alignment

- [ ] ADR-ehfcj/ADR-q333d/ADR-ae6nw/ADR-ij1ew の方針と整合を確認。
- [ ] 既存ログとの重複があれば整理。

## Plan Summary

- Phase 1 – パーサとダミー LLM フローの骨格
- Phase 2 – 可視化プレースホルダとエラー枠組み
- Phase 3 – テストとビルド配線

### Phase Status Tracking

Mark checkboxes (`[x]`) immediately after completing each task or subtask. If an item is intentionally skipped or deferred, annotate it (e.g., strike-through with a brief note) instead of leaving it unchecked.

---

## Phase 1: パーサとダミー LLM フロー

### Goal

- SKILL.md を POJO 化し、固定応答の Plan/Act/Reflect スタブを動かす。

### Inputs

- Documentation:
  - `docs/requirements/FR-mcncb-single-skill-basic-execution.md`
  - `docs/tasks/T-0mcn0-minimal-foundation/design.md`
- Source Code to Modify:
  - `src/main/java/...` パーサ
  - `src/main/java/...` ダミー Agent フロー
- Dependencies:
  - LangChain4j

### Tasks

- [ ] **パーサ**
  - [ ] YAML frontmatter + 本文を読み取り POJO 化（必須項目のみ）
  - [ ] エラー時の日本語メッセージ
- [ ] **ダミー LLM フロー**
  - [ ] 固定レスポンス LLM モック
  - [ ] Plan/Act/Reflect スタブで入出力を通す

### Deliverables

- SkillDocument POJO とパーサ
- ダミー Plan/Act/Reflect 実行フロー

### Verification

```bash
./gradlew check
./gradlew test --tests "*Parser*"
```

### Acceptance Criteria (Phase Gate)

- サンプル SKILL.md でスタブ実行が完走し、固定テキストを生成する。

### Rollback/Fallback

- フラグでダミー実行を無効化し、既存（なし）の状態に戻す。

---

## Phase 2: 可視化プレースホルダとエラー枠組み

### Phase 2 Goal

- Plan/Act/Reflect で可視化イベントをログ出力し、例外捕捉＋1 回リトライ枠組みを組み込む。

### Phase 2 Inputs

- Dependencies:
  - Phase 1 のパーサとダミー実行フロー
- Source Code to Modify:
  - `src/main/java/...` 可視化プレースホルダ
  - `src/main/java/...` エラー処理/リトライ枠組み

### Phase 2 Tasks

- [ ] **可視化プレースホルダ**
  - [ ] Phase/skillId/runId/step/message/inputSummary/outputSummary/error を含むログ出力（input/output はマスク前提）
  - [ ] レベル制御（basic/off）
- [ ] **エラー枠組み**
  - [ ] try-catch と 1 回リトライ（固定ディレイなし）
  - [ ] 日本語ログと終了コード管理（info/warn/error の最小構成）

### Phase 2 Deliverables

- 可視化ログ出力のプレースホルダ
- リトライ付きエラーハンドリング枠組み

### Phase 2 Verification

```bash
./gradlew check
./gradlew test --tests "*Flow*"
```

### Phase 2 Acceptance Criteria

- 可視化ログが Plan/Act/Reflect で出力され、例外時にリトライログが出る。

### Phase 2 Rollback/Fallback

- ログ/リトライをフラグで無効化。

---

## Phase 3: テストとビルド配線

### Phase 3 Goal

- e2e テストと Gradle 配線を完了させ、ビルド/実行方法をドキュメント化する。

### Phase 3 Tasks

- [ ] テスト用 SKILL.md を `src/test/resources/skills/` に追加
- [ ] e2e テストで Plan/Act/Reflect と可視化ログを検証
- [ ] CLI/Run タスクの引数処理を整備

### Phase 3 Deliverables

- e2e テストと実行手順
- ビルド成功状態

### Phase 3 Verification

```bash
./gradlew check
./gradlew test
./gradlew build
```

### Phase 3 Acceptance Criteria

- 主要テストが緑で、ビルドが成功する。

---

## Definition of Done

- [ ] `./gradlew check`
- [ ] `./gradlew test`
- [ ] `./gradlew build`
- [ ] 実行手順を README に追記
- [ ] 日本語ログとリトライ枠組みが動作

## Open Questions

- [ ] Workflow 型と Pure Agent 型のどちらを最小実装とするか。
- [ ] ログレベルと終了コードのデフォルト値。
- [ ] 可視化プレースホルダのフィールド拡張タイミング（T-7k08g との境界）。

<!-- Complex investigations should spin out into their own ADR or analysis document -->
