# テストで利用するLangfuseプロンプト取得ツール

Langfuseのトレースから `workflow.act` 配下の `llm.chat` プロンプトを取得するための Gradle タスクを追加しました。プロンプトの改善アイデアを検証したいときに、実際に生成されたプロンプトを素早く確認できます。

## 事前準備

- `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` / `LANGFUSE_BASE_URL` を環境変数、または `.envrc` などで設定しておきます。
- もしくは Gradle 実行時に `-PpublicKey=...` `-PsecretKey=...` `-PbaseUrl=...` を指定します。

## 使い方

最も最近のトレースからプロンプトを確認する場合（デフォルトはインデックス 0）:

```bash
./gradlew :app:langfusePrompt
```

実行時に `Info: using latest traceId=...` が表示されれば、最新トレースが自動で選択されています。

特定のトレースIDを明示して確認する場合（デフォルトはインデックス 0）:

```bash
./gradlew :app:langfusePrompt -PtraceId=d4776f504b601f61cb5d1d352a5bfc7e
```

特定のインデックスを指定する場合:

```bash
./gradlew :app:langfusePrompt -PtraceId=d4776f504b601f61cb5d1d352a5bfc7e -Pindex=2
```

`workflow.act` 配下のすべての `llm.chat` プロンプトをダンプする場合:

```bash
./gradlew :app:langfusePrompt -PtraceId=d4776f504b601f61cb5d1d352a5bfc7e -Pall=true
```

Gradle プロパティで資格情報を上書きする例:

```bash
./gradlew :app:langfusePrompt \
  -PtraceId=d4776f504b601f61cb5d1d352a5bfc7e \
  -PbaseUrl=http://localhost:3000 \
  -PpublicKey=pk-lf-example \
  -PsecretKey=sk-lf-example
```

ツール実行後は標準出力にプロンプトが表示されます。テキストエディタに貼り付けて差分比較したり、改善案を検討する際のベースラインとして活用してください。
