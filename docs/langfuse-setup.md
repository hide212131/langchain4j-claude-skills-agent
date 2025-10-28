# LangFuse Observability Setup Guide

本ガイドでは、LangChain4j-Claude-Skills-Agent に OpenTelemetry (OTLP) を使用した LangFuse 連携を設定する方法を説明します。

## 概要

LangFuse は LLM アプリケーションの observability プラットフォームであり、OTLP (OpenTelemetry Protocol) を通じてトレースデータを受け取ることができます。このプロジェクトは OpenTelemetry を統合しており、ローカル端末で動作する LangFuse にトレースデータを送信することができます。

## キャプチャされる情報

このプロジェクトは以下の詳細な observability データをキャプチャします：

### LLM 呼び出しレベル
- **プロンプト内容**: 送信された完全なメッセージ内容
- **レスポンス内容**: LLM からの完全なレスポンス
- **メッセージ構造**: すべてのメッセージの役割（user/system/assistant）とコンテンツ
- **トークン使用量**: 入力/出力/合計トークン数
- **実行時間**: 各 LLM 呼び出しの所要時間
- **エラー情報**: 失敗した場合の例外とエラーメッセージ

### ワークフローレベル（Plan→Act→Reflect）
- **Plan ステージ**: 
  - 目標（goal）
  - 実行モード（dry-run / live）
  - 選択されたスキル ID
  - スキル数
  - 試行回数
- **Act ステージ**:
  - 実行されたスキル ID
  - 呼び出されたスキル
  - 生成された成果物（artifact）
  - 試行回数
- **Reflect ステージ**:
  - 評価サマリー
  - リトライアドバイス
  - 試行回数

### SkillRuntime 内部処理レベル
- **スキル実行（SkillExecution）**:
  - スキル ID、名前、説明
  - 成果物の有無とパス
  - 参照ファイル数
  - ツール呼び出し回数
  - 呼び出された子スキル
  - 検証結果（期待される出力が満たされているか）
- **プロンプト構築（SkillPromptBuild）**:
  - プロンプト長
  - 期待される出力
- **オーケストレータ実行（SkillOrchestrator）**:
  - レスポンス長
  - ツール呼び出し回数

これにより、**SkillRuntime での効率的なコンテキストエンジニアリングを詳細に分析**できます。

## 前提条件

### 1. LangFuse のセットアップ

ローカル端末で LangFuse を起動します。Docker Compose を使用する場合:

```bash
# LangFuse の docker-compose.yml をダウンロード
curl -o docker-compose.yml https://raw.githubusercontent.com/langfuse/langfuse/main/docker-compose.yml

# LangFuse を起動
docker-compose up -d
```

デフォルトでは LangFuse は以下のポートで起動します:
- Web UI: http://localhost:3000
- OTLP gRPC エンドポイント: http://localhost:4317 (このプロジェクトで使用)

### 2. 環境変数の設定

LangFuse との連携を有効にするため、以下の環境変数を設定します:

```bash
# LangFuse OTLP エンドポイント (必須)
# 注: gRPC エンドポイントを使用 (HTTP/JSON の場合は 4318)
export LANGFUSE_OTLP_ENDPOINT="http://localhost:4317"

# サービス名 (任意、デフォルトは "langchain4j-claude-skills-agent")
export LANGFUSE_SERVICE_NAME="my-skills-agent"

# OpenAI API キー (必須)
export OPENAI_API_KEY="your-openai-api-key"
```

**注意**: `LANGFUSE_OTLP_ENDPOINT` が設定されていない場合、OpenTelemetry は無効化され、トレースデータは送信されません。

### 3. direnv を使用する場合

`.envrc` ファイルに環境変数を追加:

```bash
export LANGFUSE_OTLP_ENDPOINT="http://localhost:4317"
export LANGFUSE_SERVICE_NAME="my-skills-agent"
export OPENAI_API_KEY="your-openai-api-key"
```

そして:

```bash
direnv allow
```

## 使用方法

### 基本的な実行

環境変数を設定した後、通常通り CLI を実行します:

```bash
./gradlew run --args="run --goal 'ブランド準拠で5枚のスライドを作る' --skills-dir skills"
```

または、スクリプトを使用:

```bash
./sk run --goal "ブランド準拠で5枚のスライドを作る" --skills-dir skills
```

OpenTelemetry が有効な場合、起動時に以下のメッセージが表示されます:

```
Info: OpenTelemetry enabled, exporting to http://localhost:4317
```

### LangFuse Web UI での確認

1. ブラウザで http://localhost:3000 を開く
2. トレースデータが自動的に表示されます
3. 各 LLM 呼び出しの詳細（トークン使用量、レスポンス時間など）を確認できます

## トレースデータの内容

以下の情報が LangFuse に送信されます:

### LLM 呼び出しトレース
- **llm.messages**: 全メッセージの役割とコンテンツ
- **llm.prompt**: プロンプトテキスト（単一メッセージの場合）
- **llm.response**: LLM からの完全なレスポンステキスト
- **message_count**: メッセージ数
- **response_length**: レスポンステキストの長さ
- **token_usage.input/output/total**: トークン使用量
- **実行時間**: 各呼び出しの所要時間
- **エラー情報**: 失敗した場合の例外

### ワークフロートレース
- **workflow.plan**: 
  - `goal`: 目標
  - `mode`: 実行モード（dry-run/live）
  - `attempt`: 試行番号
  - `skill_count`: 選択されたスキル数
  - `skill_ids`: スキル ID のリスト
- **workflow.act**:
  - `skill_ids`: 実行予定のスキル
  - `invoked_skills`: 実際に実行されたスキル
  - `has_artifact`: 成果物の有無
  - `artifact`: 成果物のパス
- **workflow.reflect**:
  - `summary`: 評価サマリー
  - `retry_advice`: リトライが必要かどうかのアドバイス
  - `attempt/max_attempts`: 試行回数と最大試行回数

### SkillRuntime トレース
- **workflow.skillexecution**:
  - `skill_id/name/description`: スキル情報
  - `has_artifact/artifact_path`: 成果物情報
  - `referenced_files`: 参照ファイル数
  - `tool_invocation_count`: ツール呼び出し回数
  - `invoked_skills`: 呼び出された子スキル
  - `validation_satisfied`: 検証結果
  - `missing_outputs`: 不足している出力
- **workflow.skillpromptbuild**:
  - `prompt_length`: プロンプト長
  - `expected_outputs`: 期待される出力
- **workflow.skillorchestrator**:
  - `response_length`: レスポンス長
  - `tool_invocations`: ツール呼び出し回数

これにより、プロンプトがどのように生成されたか、AgentScope の内容、処理の流れ、さらに **SkillRuntime の内部処理**を完全に可視化できます。

## トラブルシューティング

### トレースデータが表示されない

1. `LANGFUSE_OTLP_ENDPOINT` が正しく設定されているか確認
2. LangFuse が起動しているか確認: `docker-compose ps`
3. ポート 4317 がアクセス可能か確認
4. ファイアウォール設定を確認

### 接続エラー

LangFuse が起動していない、または別のポートで動作している場合、接続エラーが発生する可能性があります。エンドポイントを確認してください。

### OpenTelemetry を無効化する

OpenTelemetry を一時的に無効化する場合は、環境変数をアンセット:

```bash
unset LANGFUSE_OTLP_ENDPOINT
```

または、変数を空にします:

```bash
export LANGFUSE_OTLP_ENDPOINT=""
```

## 参考資料

- [LangChain4j Observability Tutorial](https://docs.langchain4j.dev/tutorials/observability/)
- [LangFuse Documentation](https://langfuse.com/docs)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)

## アーキテクチャ

このプロジェクトでは以下のコンポーネントで OpenTelemetry を実装しています:

1. **ObservabilityConfig**: OpenTelemetry の設定を管理
2. **ObservableChatModel**: ChatModel のラッパーで、各 LLM 呼び出しをトレース
3. **LangChain4jLlmClient**: OpenTelemetry が有効な場合、ObservableChatModel でラップされた ChatModel を使用

これにより、既存のコードを変更することなく、observability 機能を追加できます。
