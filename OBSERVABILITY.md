# OpenTelemetry / LangFuse 統合

## 概要

このプロジェクトは OpenTelemetry Protocol (OTLP) を通じて LangFuse への observability 統合をサポートしています。

## クイックスタート

### 1. LangFuse をローカルで起動

```bash
docker-compose up -d
```

LangFuse は以下で利用可能:
- Web UI: http://localhost:3000
- OTLP gRPC エンドポイント: http://localhost:4317

### 2. 環境変数を設定

```bash
export LANGFUSE_OTLP_ENDPOINT="http://localhost:4317"  # gRPC endpoint
export OPENAI_API_KEY="your-api-key"
```

### 3. アプリケーションを実行

```bash
./gradlew run --args="run --goal 'テスト実行' --skills-dir skills"
```

または:

```bash
./sk run --goal "テスト実行" --skills-dir skills
```

### 4. トレースを確認

http://localhost:3000 で LangFuse Web UI を開き、トレースデータを確認できます。

## 環境変数

| 変数名 | 必須 | デフォルト値 | 説明 |
|--------|------|-------------|------|
| `LANGFUSE_OTLP_ENDPOINT` | いいえ | なし | OTLP gRPC エンドポイント URL (例: http://localhost:4317)。未設定の場合、observability は無効 |
| `LANGFUSE_SERVICE_NAME` | いいえ | `langchain4j-claude-skills-agent` | トレースに使用するサービス名 |
| `OPENAI_API_KEY` | はい | なし | OpenAI API キー |

## 送信されるメトリクス

- メッセージ数
- レスポンステキスト長
- トークン使用量 (入力/出力/合計)
- 実行時間
- エラー情報

## 詳細ドキュメント

詳細は [docs/langfuse-setup.md](docs/langfuse-setup.md) を参照してください。
