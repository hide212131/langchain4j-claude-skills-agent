# T-7k08g プロンプト・エージェント可視化タスク

## Metadata

- Type: Task
- Status: Complete
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
Phase 2 では可視化イベントを OTLP(LangFuse など) に送れるよう `--exporter`/`--otlp-endpoint`/`--otlp-headers` を受け付け、送信先切替を実装する。

## Scope

- In scope: SKILL パース結果の記録、LLM 送信プロンプトと応答の取得、AgenticScope コンテキスト/履歴の追跡、入出力パラメータとメトリクスの構造化ログ化、OTLP 連携の設計方針整理（LangFuse/Azure いずれも OTLP 送信先として想定）。
- Out of scope: 複数スキル連鎖や複雑分岐の実装そのもの（FR-2ff4z/FR-cccz4 に委譲）、本番環境へのデプロイ手順詳細（EKS/クラウド構築）、UI ダッシュボード実装。

## Success Metrics

- プロンプトと AgenticScope 状態を工程別に追跡できるログ/トレースを出力し、FR-hjz63 受け入れ基準を満たす。
- ローカル観測（LangFuse、OTLP 宛て）とクラウド観測（OTLP/AI Insights）へ同一スキーマで送出できる設計を確立する。
- エラー/マスキング要件を含む可視化の安全性を計画に盛り込み、NFR-30zem/NFR-mt1ve の観点を満たす。

## ローカル検証環境（前提）

LangFuse をローカルで立ち上げる場合は公式 docker-compose を利用する（開発者ローカルの前提手順。クラウド構築は別途）。Gradle から簡易起動できる `langfuseUp`/`langfuseDown`（docker compose ラッパー）を追加予定。

```bash
docker compose -f https://raw.githubusercontent.com/langfuse/langfuse/main/docker-compose.yml up -d
```

Gradle タスク（推奨）:

```bash
./gradlew :app:langfuseUp
./gradlew :app:langfuseUpForeground
./gradlew :app:langfuseDown
./gradlew :app:langfuseReset
./gradlew :app:langfuseLogs
```

### Self-host 初期化（LANGFUSE_INIT\_\*）

self-host の場合、最初は画面で「組織・プロジェクト・キー」を作る必要がありますが、代わりに `LANGFUSE_INIT_*` を設定して起動すると、起動時に自動作成できます（公式 docker-compose の機能）。

ポイント:

- `./gradlew :app:langfuseUp` はリポジトリ直下の `.env` を参照するため、`.env` に `LANGFUSE_INIT_*` を書いておけば OK です。
- 固定キー運用にしたい場合は、`LANGFUSE_INIT_PROJECT_PUBLIC_KEY`/`LANGFUSE_INIT_PROJECT_SECRET_KEY` と、`LANGFUSE_PUBLIC_KEY`/`LANGFUSE_SECRET_KEY` を同じ値にしてください（`langfuseReport`/`langfusePrompt` がその値で API を呼びます）。
- 初期化は「初回起動（または未作成状態）」前提です。すでに UI で作成済みの場合は、初期化が反映されないことがあります。
  - その場合は UI で手動作成するか、ローカル用途で問題ないならボリュームごと削除してやり直してください（データ消えます）。

例（`.env`）:

```bash
LANGFUSE_INIT_ORG_ID=local
LANGFUSE_INIT_ORG_NAME=Local
LANGFUSE_INIT_PROJECT_ID=demo
LANGFUSE_INIT_PROJECT_NAME=Demo
LANGFUSE_INIT_PROJECT_PUBLIC_KEY=pk-lf-demo
LANGFUSE_INIT_PROJECT_SECRET_KEY=sk-lf-demo
LANGFUSE_INIT_USER_EMAIL=local@example.com
LANGFUSE_INIT_USER_NAME=LocalUser
LANGFUSE_INIT_USER_PASSWORD=local-password
```

反映手順:

```bash
./gradlew :app:langfuseDown
./gradlew :app:langfuseUp
```

ボリューム削除してやり直す場合（注意: データ消えます）:

```bash
./gradlew :app:langfuseReset
./gradlew :app:langfuseUp
```

デバッグ（ブラウザ操作しながらログを見たい）:

ターミナルを 2 つ使います。

ターミナル 1（フォアグラウンドで起動。Ctrl+C で停止）:

```bash
./gradlew :app:langfuseUpForeground
```

ターミナル 2（ログ追跡。特定サービスに絞る場合は `-Pservice=...`）:

```bash
./gradlew :app:langfuseLogs
./gradlew :app:langfuseLogs -Pservice=langfuse-web
./gradlew :app:langfuseLogs -Pservice=langfuse-worker
```

トレース集計は `langfuseReport`（予定）で LangFuse API から直近トレースを取得し、gen_ai 指標（トークン数・レイテンシ・エラー率）を標準出力にまとめる。鍵が未設定の場合はスキップする。

プロンプト取得は `langfusePrompt`（予定）で可視化スキーマに合わせたプロンプト属性を取得する（旧仕様の固定パスではなく、設計で定義した VisibilityEvent `prompt` や `gen_ai.request.*` を持つ Span/Log を対象）。環境変数または Gradle プロパティで資格情報を指定する。

### LangFuse レポート/プロンプト取得（Gradle タスク）

前提（環境変数）:

- `LANGFUSE_HOST`
- `LANGFUSE_PUBLIC_KEY`
- `LANGFUSE_SECRET_KEY`

実行例:

```bash
./gradlew :app:langfuseReport -Plimit=20
./gradlew :app:langfuseReport -PtraceId=<traceId>
./gradlew :app:langfusePrompt -Plimit=5
./gradlew :app:langfusePrompt -PtraceId=<traceId>
```

## OpenAI 実行の動作確認（CLI）

`.env` に OpenAI と可視化の設定を入れた上で、CLI で `--llm-provider openai` を指定して実行する。

例:

```bash
app/build/install/skills/bin/skills --skill path/to/SKILL.md --goal "demo" --llm-provider openai --exporter otlp
```

OTLP 送信先は `OTEL_EXPORTER_OTLP_ENDPOINT`（および必要なら `OTEL_EXPORTER_OTLP_HEADERS`）で指定する。`.env.example` を参照。

LangFuse self-host に送る場合は、`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:3000/api/public/otel` を推奨（OTLP/HTTP）。
この場合 `LANGFUSE_PUBLIC_KEY`/`LANGFUSE_SECRET_KEY` が設定されていれば、`Authorization: Basic ...` ヘッダは自動付与される（`OTEL_EXPORTER_OTLP_HEADERS` を省略可）。

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](../../templates/README.md#task-template-taskmd) in the templates README.
