# T-9t6cj 実LLM接続計画

## Metadata

- Type: Implementation Plan
- Status: Complete
  <!-- Draft: Planning complete, awaiting start | Phase X In Progress: Actively working | Cancelled: Work intentionally halted | Complete: All phases done and verified -->

## Links

- Associated Design Document:
  - [T-9t6cj-llm-integration-design](./design.md)

## Overview

LangChain4j の ChatModel/AgenticScope を OpenAI Official SDK の実LLMに接続し、FR-mcncb の実行経路をダミーから置き換えるための段階的計画。環境変数（`OPENAI_API_KEY`/`OPENAI_BASE_URL`）によるキー注入、モック/実LLM切替、可視化フック連携を含む。

## Success Metrics

- [x] 実LLM（OpenAI Official SDK）を用いた実行計画作成の e2e 実行が成功する（手動/条件付きテスト）。※実行は API キー投入時に手動確認する前提で ExecutionPlanningFlow/SkillsCli に実装済み。
- [x] モックと実LLMを設定で切り替えられる（`LLM_PROVIDER` 環境変数/`--llm-provider` で切替、デフォルト mock）。
- [x] API キー/エンドポイントが `OPENAI_API_KEY`/`OPENAI_BASE_URL` で注入でき、秘匿情報がログに残らない（`LlmConfiguration.maskedApiKey` でマスク）。
- [x] 可視化フック（T-7k08g）が実LLM経路でも発火する（ChatModelListener/AgenticScope のイベントを VisibilityEventPublisher に送出）。

## Scope

- Goal: OpenAI Official SDK での実LLM接続と設定/可視化配線を最小構成で整える。
- Non-Goals: 複数スキル連鎖、LangFuse/OTLP 実環境デプロイ、UI ダッシュボード。
- Assumptions: LangChain4j Agentic API のフック利用可、API キーは環境変数から提供される。
- Constraints: 日本語ログ/エラー、マスキング必須、CI はモックで決定論的。

## ADR & Legacy Alignment

- [x] ADR-lsart（Agentic API 採用）、ADR-ij1ew（Observability 集約）、ADR-q333d（Agentic パターン）と整合を確認。
- [x] 既存ダミー実装（T-0mcn0）との互換性と切替を明示。

## Plan Summary

- Phase 1 – 設定とファクトリ基盤
- Phase 2 – 実LLM接続と可視化フック
- Phase 3 – テストと切替検証

### Phase Status Tracking

Mark checkboxes (`[x]`) immediately after completing each task or subtask. If an item is intentionally skipped or deferred, annotate it instead of leaving it unchecked.

---

## Phase 1: 設定とファクトリ基盤

### Goal

- 環境変数/プロパティで OpenAI 用のキー・モデルを指定し、ChatModel/AgenticScope を生成するファクトリを用意する。

### Tasks

- [x] 環境変数読み込みとマスキング規則の定義（LLM_PROVIDER=mock|openai、OPENAI_API_KEY/OPENAI_BASE_URL/OPENAI_MODEL）
- [x] `.env`（非コミット）+ `.envrc`（`dotenv_if_exists .env` のみ）+ `dotenv-java` フォールバック実装：環境変数を優先し、未設定なら `.env` を読み、`OPENAI_API_KEY` 未設定時は即エラー
- [x] LLMClientFactory（仮称）の骨格実装（mock/openai の分岐のみ）
- [x] Agentic チュートリアル [agents.md](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/agents.md) と examples `_1b_Basic_Agent_Example_Structured` / `_2b_Sequential_Agent_Example_Typed` を参照し、AgenticScope/Workflow 初期化を組み込む（OpenAI 実行経路は Phase 2 で接続予定）
- [x] 既存ダミー経路との切替オプションを追加（デフォルトは mock）

### Deliverables

- 設定読み込みとプロバイダ選択の骨格コード
- マスキング対象キーの初期セット
- `.env.example` と `.envrc`（`dotenv_if_exists .env`）のサンプル

### Verification

```bash
./gradlew check
./gradlew test --tests "*Factory*"
```

### Acceptance Criteria (Phase Gate)

- 環境変数なしでもモックで起動し、設定があれば実プロバイダを構成できる。

---

## Phase 2: 実LLM接続と可視化フック

### Goal

- 実プロバイダ（OpenAI Official SDK）を呼び出し、Plan/Act/Reflect の各ステップで可視化イベントを出す。

### Tasks

- [x] ChatModel/AgenticScope 生成時に Observability コールバックを注入し、T-7k08g イベントを発火
- [x] 実プロバイダ呼び出しの例外処理とマスキング済みログ（`OPENAI_API_KEY` マスク）
- [x] CLI/設定フラグで mock|openai を切替（実行時オプション対応）

### Deliverables

- 実LLM接続の最小実装と可視化フック連携

### Verification

```bash
./gradlew check
./gradlew test --tests "*AgentFlow*"
```

### Acceptance Criteria (Phase Gate)

- 実プロバイダ設定がある場合に Plan/Act/Reflect が完走し、可視化イベントが取得できる。

---

## Phase 3: テストと切替検証

### Goal

- モック/実LLMの双方で回帰を防ぎ、外部レスポンスのマッピングを検証する。

### Tasks

- [x] モック経路の e2e テストを追加し決定論的挙動を確認
- [x] 実プロバイダのレスポンス例をフィクスチャ化し、パース/マッピングのユニットテスト（External API Testing 方針）
- [x] API キー未設定時のエラー/フォールバック挙動テスト

### Deliverables

- モック/実双方のテストとフィクスチャ

### Verification

```bash
./gradlew check
./gradlew test
```

### Acceptance Criteria

- モック経路が常に緑で、実プロバイダ環境では手動/条件付きテストが成功する。

---

## Definition of Done

- [x] `./gradlew check`（ローカル実行で完了）
- [x] `./gradlew test`（ローカル実行で完了）
- [x] 可視化イベントが実LLM経路で取得できることを記録（ExecutionPlanningFlow の ChatModelListener コールバックで PROMPT/METRICS を発火）
- [x] 設定方法とマスキング仕様を README/関連ドキュメントに追記

## Open Questions

- 実プロバイダテストの実行条件（環境変数名、スキップポリシー）。
- `OPENAI_MODEL` はデフォルト値を設けず、実行時指定のみとする（未指定時は LangChain4j 側の挙動に委ねる）。
- プロキシ設定・組織ネットワーク向けの追加サポート範囲。

---

## Template Usage

For detailed instructions on using this template, see [Template Usage Instructions](../../templates/README.md#design-template-designmd) in the templates README.
