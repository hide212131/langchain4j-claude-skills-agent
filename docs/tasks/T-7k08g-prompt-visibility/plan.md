# T-7k08g プロンプト可視化実装計画

## Metadata

- Type: Implementation Plan
- Status: Draft
  <!-- Draft: Planning complete, awaiting start | Phase X In Progress: Actively working | Cancelled: Work intentionally halted before completion | Complete: All phases done and verified -->

## Links

<!-- Internal project artifacts only. Replace or remove bullets as appropriate. -->

- Associated Design Document:
  - [T-7k08g-prompt-visibility-design](./design.md)

## Overview

FR-hjz63 の受け入れ基準を満たす可視化基盤を実装するための段階的計画。SKILL.md パースから AgenticScope 実行、出力までを可視化し、OTLP への連携準備を整える（LangFuse/Azure いずれも OTLP 送信先として想定）。

## Success Metrics

- [ ] SKILL パース/実行/出力の各段階で構造化イベントが記録される。
- [ ] LLM プロンプトと AgenticScope 状態が時系列で追跡できる。
- [ ] メトリクス/ログの送信先切替（none|otlp）が設定で制御できる。
- [ ] 既存テストに回帰なし。

## Scope

- Goal: 可視化イベント生成とエクスポートを最小構成で成立させ、FR-hjz63 の受け入れ基準を実現する。
- Non-Goals: 複数スキル連鎖の新規機能、UI ダッシュボード作成、クラウドデプロイ手順の詳細化。
- Assumptions: LangChain4j Agentic API のフックが利用可能、観測設定はプロパティ/環境変数で注入可能。
- Constraints: 日本語メッセージ、マスキング必須、性能オーバーヘッドを最小化。

## ADR & Legacy Alignment

- [ ] 最新 ADR（ADR-q333d, ADR-ij1ew）と整合を確認し、設計で参照する。
- [ ] 既存ログ出力との重複/競合がある場合はフェーズ内で整理タスクを追加。

## Plan Summary

- Phase 1 – 可視化イベント基盤とパース計装
- Phase 2 – AgenticScope フックとエクスポーター（OTLP 連携）
- Phase 3 – テストとハードニング

### Phase Status Tracking

Mark checkboxes (`[x]`) immediately after completing each task or subtask. If an item is intentionally skipped or deferred, annotate it (e.g., strike-through with a brief note) instead of leaving it unchecked.

---

## Phase 1: 可視化イベント基盤

### Goal

- SKILL パース～POJO 生成までのイベントを記録し、共通スキーマを確立する。

### Inputs

- Documentation:
  - `docs/requirements/FR-hjz63-prompt-visibility-framework.md` – 受け入れ基準
  - `docs/tasks/T-hjz63-prompt-visibility/design.md` – 設計方針
- Source Code to Modify:
  - `src/main/java/...` – SKILL パーサ関連（イベント発火）
  - `src/main/java/...` – 共通イベントモデル/ロガー追加
- Dependencies:
  - Internal: LangChain4j パーサ/AgenticScope 実装
  - External: Jackson/OTLP 型（シリアライズ用）

### Tasks

- [ ] **イベントスキーマ定義**
  - [ ] VisibilityEvent/PromptPayload/AgentStatePayload/MetricsPayload を定義
  - [ ] マスキングルールと共通メタデータ（skillId/runId/phase）を決定
- [ ] **SKILL パース計装**
  - [ ] YAML frontmatter/Markdown 本文のパース結果をイベント化
  - [ ] JSON Schema 検証結果と例外をイベント化
- [ ] **最小実行スタブと赤テスト**
  - [ ] ダミー LLM 応答とパース/実行スタブを用意し、イベントが出る赤テストを追加
  - [ ] Plan/Act/Reflect で期待イベントのフィールドをアサート

### Deliverables

- 共通イベントモデルと SKILL パース時のイベント出力
- マスキングルールの初期セット

### Verification

```bash
./gradlew check
./gradlew test --tests "*Parser*"
```

### Acceptance Criteria (Phase Gate)

- SKILL パース成功/失敗のイベントが構造化で取得できること。
- マスキング済みログが生成されること。

### Rollback/Fallback

- パース計装をフラグで無効化し、既存挙動に戻せるようにする。

---

## Phase 2: AgenticScope フックとエクスポート

### Phase 2 Goal

- Plan/Act/Reflect の各ステップでプロンプト/状態/メトリクスを記録し、OTLP への送信経路を整備する（LangFuse は OTLP 送信先として優先検証、Azure Application Insights は後続フェーズ）。

### Phase 2 Inputs

- Dependencies:
  - Phase 1: イベントスキーマとパース計装
  - 設計: エクスポーター切替ポリシー
- Source Code to Modify:
  - `src/main/java/...` – AgenticScope フック実装
  - `src/main/java/...` – OTLP Exporter 実装

### Phase 2 Tasks

- [ ] **Agentic フック**
  - [ ] Plan/Act/Reflect でプロンプト/応答/決定をイベント化
  - [ ] 入出力パラメータとメトリクス（トークン/レイテンシ）を付与
- [ ] **エクスポーター**
  - [ ] OTLP の接続設定を CLI/環境変数で切替（`--exporter none|otlp`、`OTEL_EXPORTER_OTLP_ENDPOINT`、`OTEL_EXPORTER_OTLP_HEADERS` など）
  - [ ] OTLP は OpenTelemetry Java SDK を用い、ビジネスコードは OpenTelemetry API のみを呼ぶ形に整理（Exporter で宛先切替）
  - [ ] Span/Log に gen_ai セマンティック属性をマッピング（`gen_ai.request.*`/`gen_ai.response.*`/`gen_ai.usage.*` など）し、Plan/Act/Reflect を Span として表現
  - [ ] OTLP 出力の基本実装と設定切替（none|otlp）
  - [ ] エラー/リトライのロギング（NFR-mt1ve 連携）
  - [ ] OTLP のモック送信でフィールドマッピングを検証（LangFuse/Azure どちらでも同一スキーマ）
  - [ ] ローカル検証向けに LangFuse docker-compose 起動を簡略化する Gradle タスク（例: `langfuseUp`/`langfuseDown`）を追加し、README に手順を記載（インフラ構築は範囲外であることを明示）
  - [ ] LangFuse トレースを取得する Gradle タスク（例: `langfuseReport`）を追加し、直近トレースの gen_ai 指標（トークン数/レイテンシ/エラー率）を標準出力に集計（キー未設定時はスキップ）
  - [ ] プロンプト取得 Gradle タスク（例: `langfusePrompt`）を実装し、VisibilityEvent 種別 `prompt` や `gen_ai.request.*` を持つ Span/Log からプロンプト情報を抽出する。固定パスに依存せず、資格情報は環境変数/Gradle プロパティ両対応

### Phase 2 Deliverables

- AgenticScope 計装とエクスポート設定
- OTLP 送信の最小実装（LangFuse 宛ての検証は OTLP エンドポイントで実施）
- OTLP (OpenTelemetry) 宛て送信の実装と gen_ai 属性マッピング例
- ローカル LangFuse 起動手順（Gradle タスク経由の簡易起動と docker-compose 1 行）を README に記載
- LangFuse トレース集計用 Gradle タスクと出力例（トークン数・p95 レイテンシ・エラー率）
- LangFuse プロンプト取得タスク（`langfusePrompt`）の利用手順と資格情報指定方法（現行スキーマの属性に基づく）

### Phase 2 Verification

```bash
./gradlew check
./gradlew test --tests "*Agentic*"
```

### Phase 2 Acceptance Criteria

- Plan/Act/Reflect のイベントが記録され、送信先切替が動作する。
- エラーハンドリング情報がイベントに含まれる。

### Phase 2 Rollback/Fallback

- エクスポート機能を無効化し、ローカル JSON ログのみの出力に戻す。

---

## Phase 3: Testing & Integration

### Phase 3 Goal

- e2e シナリオと安定性テストで可視化機能を検証し、回帰を防止する。

### Phase 3 Tasks

- [ ] テスト用 SKILL.md を用いた e2e テスト追加（Plan/Act/Reflect のイベント確認）
- [ ] OTLP モック送信テストでフィールドマッピングを検証
- [ ] エラー/リトライ/フォールバックのイベント記録テスト（NFR-mt1ve）
- [ ] 性能オーバーヘッドの簡易測定と調整

### Phase 3 Deliverables

- 自動テストと観測サンプルログ/トレース
- 既知の制約と今後の改善メモ

### Phase 3 Verification

```bash
./gradlew check
./gradlew test
```

### Phase 3 Acceptance Criteria

- FR-hjz63/NFR-30zem/NFR-mt1ve の可視化関連受け入れ基準を満たすテストが緑である。
- 主要パスの性能オーバーヘッドが許容範囲である。

---

## Definition of Done

- [ ] `./gradlew check`
- [ ] `./gradlew test`
- [ ] 観測サンプル（OTLP 相当）を取得し、ドキュメントに反映
- [ ] 新規エラーメッセージが日本語で、マスキング仕様に従う
- [ ] 関連ドキュメント/README を更新し、タスクリンクを traceability に反映

## Open Questions

- [ ] Exporter のバッファ戦略（サイズ/タイムアウト）のデフォルト値
- [ ] マスキング対象キーの標準セット（例: apiKey, token, email）
- [ ] Plan/Act/Reflect 以外の補助フェーズ（評価/後処理）の可視化範囲

<!-- Complex investigations should spin out into their own ADR or analysis document -->
