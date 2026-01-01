# T-9t6cj 実LLM接続デザイン

## Metadata

- Type: Design
- Status: Approved
  <!-- Draft: Work in progress | Approved: Ready for implementation | Rejected: Not moving forward with this design -->

## Links

- Associated Plan Document:
  - [T-9t6cj-llm-integration-plan](./plan.md)

## Overview

FR-mcncb を達成するため、LangChain4j の Agentic API を OpenAI Official SDK の実LLMに接続する最小構成をまとめる。API キー/エンドポイントを環境変数 `OPENAI_API_KEY`/`OPENAI_BASE_URL` から注入し、SKILL.md パース → Plan/Act/Reflect → 生成物出力までをダミーではなく実モデルで実行可能にする。可視化フック（T-7k08g）と Observability 方針（ADR-ij1ew）に整合する形で計装ポイントを配置する。

- LangChain4j バージョン: v1.10.0（本タスクおよび以降の実装の前提）

## Success Metrics

- [x] ChatModel/AgenticScope が実LLMにリクエストを送信し、実行計画作成の応答を受け取れる（ExecutionPlanningFlow/SkillsCli に実装済み。実行は API キー投入時の手動確認で担保）。
- [x] API キー/エンドポイントが `OPENAI_API_KEY`/`OPENAI_BASE_URL` から注入され、ログに秘匿値を残さない（`LlmConfiguration.maskedApiKey` でマスク）。
- [x] モック/実LLMの切替が設定で可能で、CI はモックで決定論的に通る（デフォルト mock、`LLM_PROVIDER`/`--llm-provider` で切替）。
- [x] 可視化フック（T-7k08g）経由でプロンプト・メトリクスが取得できる（ChatModelListener/AgenticScope コールバックで VisibilityEvent を発火）。

## Background and Current State

- 現状は T-0mcn0 のダミー LLM で Plan/Act/Reflect を通すのみ。実プロバイダ接続とキー注入は未実装。
- FR-hjz63 可視化と NFR-30zem Observability では LangChain4j コールバック活用とマスキングが求められる。
- ADR-lsart で LangChain4j Agentic API 採用が決定済み。ADR-ij1ew で Observability をコールバック層に集約する方針。

## Proposed Design

### High-Level Architecture

```
OPENAI_API_KEY/OPENAI_BASE_URL -> LLMClientFactory -> ChatModel/AgenticScope
                                        |                 |
                                        |         Visibility hooks (T-7k08g)
                                        v
                                  Mock or OpenAI
```

### Components

- **LLMClientFactory（仮称）**: 環境変数 `OPENAI_API_KEY`/`OPENAI_BASE_URL` を読み取り、LangChain4j ChatModel/AgenticScope（OpenAI Official SDK）を組み立てる。プロバイダ分岐は mock/openai のみ。責務を限定し、"Manager"/汎用"Util"命名を避ける。
- **Configuration**: `OPENAI_API_KEY`（実プロバイダ時必須）、`OPENAI_BASE_URL`（任意）、`OPENAI_MODEL`（任意）。モック時はキー不要。マスキング対象キーを明示。
- **Visibility Hooks**: ChatModel Observability / Agentic Observability コールバックに T-7k08g のイベント生成を差し込む。ログにはマスク済み値のみを出力。
- **Mock Strategy**: プロバイダ=mock では固定レスポンスを返し、CI で決定論的挙動を担保。実プロバイダ接続はローカル/手元検証向けに有効化。
- **AgenticScope 初期化**: LangChain4j Agentic チュートリアル（[agents.md](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/agents.md)）および examples（[\_1b_Basic_Agent_Example_Structured](https://github.com/langchain4j/langchain4j-examples/blob/main/agentic-tutorial/src/main/java/_1_basic_agent/_1b_Basic_Agent_Example_Structured.java)、[\_2b_Sequential_Agent_Example_Typed](https://github.com/langchain4j/langchain4j-examples/blob/main/agentic-tutorial/src/main/java/_2_sequential_workflow/_2b_Sequential_Agent_Example_Typed.java)）の API を参照し、AgenticScope/Workflow の組み立てパターンに準拠させる。

### Configuration Handling

- `.env` にのみ値を書き、`.envrc` は `dotenv_if_exists .env` で読むだけの薄い入口にする（direnv 無しでも `.env` で完結）。`.env` はコミットせず、`.env.example` を配布。
- Java 側は `System.getenv()` を優先し、未設定時のみ `dotenv-java` で `.env` を読むフォールバックを設計。環境変数がある場合は `.env` を再読込しない。

### Data Flow

1. CLI/設定がプロバイダ（mock/openai）とキーを指定。
2. LLMClientFactory が ChatModel/AgenticScope を生成し、Plan/Act/Reflect フローへ注入（OpenAI Official SDK）。
3. 実行中のプロンプト/応答/メトリクスを Visibility Hooks が T-7k08g スキーマでイベント化。
4. モック時は固定レスポンスでテストを実施、実プロバイダ時は外部 API 呼び出し。

### Storage Layout and Paths

- 設定値は環境変数 `OPENAI_API_KEY`/`OPENAI_BASE_URL` を優先し、必要に応じて `resources/` のプロパティでデフォルトモデル/ベースURLを提供（キーは含めない）。
- テスト用フィクスチャ（モック応答例）は `src/test/resources/llm/` に配置予定。

### API/Interface Design

- 例: `LLMClientFactory.create(options)` が `ChatLanguageModel` と `AgenticScope` 構成を返す（チュートリアル [agents.md](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/agents.md) および examples `_1b_Basic_Agent_Example_Structured` / `_2b_Sequential_Agent_Example_Typed` を準拠参照）。
- 設定スイッチ: `LLM_PROVIDER=mock|openai`（デフォルト mock）、`OPENAI_BASE_URL`, `OPENAI_API_KEY`, `OPENAI_MODEL`。
- モック/実を切り替える設定は実行時引数でも指定できるようにする（例: `--llm-provider=mock`）。

### Error Handling

- API キー未設定時は OpenAI 実プロバイダを無効化し、明示的な日本語エラーを返す（実行を止めるか自動でモックにフォールバック）。
- 接続/レート制限/タイムアウトは LangChain4j 側の例外を捕捉し、マスク済みメッセージを可視化イベントに添付。

### Security Considerations

- API キーは環境変数からのみ取得し、ログやイベントにはマスク後の値を使用。
- デフォルトはモックプロバイダとし、`OPENAI_API_KEY` がない場合は外部送信を行わない。

### Performance Considerations

- 可視化のサンプリング/レベル設定を T-7k08g のポリシーと共有。
- 実プロバイダ接続時は同期呼び出しのオーバーヘッドを計測し、必要に応じてバッチ設定を検討。

### Platform Considerations

- Unix/Windows いずれも環境変数ベースで設定。HTTP プロキシ設定が必要な場合は追加で検討（範囲外）。

## Alternatives Considered

1. **アプリ側で直接プロバイダ SDK を呼ぶ**
   - Pros: 実装が単純
   - Cons: LangChain4j AgenticScope/Observability から外れるため、FR-hjz63/NFR-30zem の整合性が崩れる
2. **全てモックのままにし、実接続を後回し**
   - Pros: 安全・決定論的
   - Cons: FR-mcncb の「実LLM経路での確認」を満たさず、以降の要求（FR-cccz4 等）に進めない

## Testing Strategy

- ユニット: LLMClientFactory の設定解決、プロバイダ切替、マスキングの単体テスト。
- 統合: モックプロバイダでの Plan/Act/Reflect e2e テスト。実プロバイダは環境変数がある場合のみ実行する条件付きテストにし、プロンプト/応答が可視化イベントに載ることを確認。
- フィクスチャ: 実プロバイダレスポンス例を文字列で保持し、パース/マッピング検証（External API Testing 方針に従う）。

## Documentation Impact

- 新しい設定項目と利用例を README か専用設定ドキュメントに追記予定。
- 可視化フック連携例を T-7k08g 側とリンク。

## Open Questions

- `OPENAI_MODEL` はデフォルト値を設けず、実行時指定のみとする（未指定なら LangChain4j 側の挙動に委ねる）。
- API キー未設定時の挙動は「即エラー」（自動モック切替は行わない）。

---

## Template Usage

For detailed instructions on using this template, see [Template Usage Instructions](../../templates/README.md#design-template-designmd) in the templates README.
