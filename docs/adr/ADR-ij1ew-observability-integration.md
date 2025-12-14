# ADR-ij1ew Observability 基盤の統合戦略

## Metadata

- Type: ADR
- Status: Approved
  <!-- Draft: Under discussion | Approved: Ready to be implemented | Rejected: Considered but not approved | Deprecated: No longer recommended | Superseded: Replaced by another ADR -->

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)
- Related ADRs:
  - [ADR-3 Context Engineering 実装方針](ADR-mpiub-context-engineering-strategy.md)
  - [ADR-5 AgenticScope の活用シナリオ](ADR-ae6nw-agenticscope-scenarios.md)
  - [ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md)
- Impacted Requirements:
  - [NFR-30zem Observability 統合](../requirements/NFR-30zem-observability-integration.md)
  - [NFR-mck7v 漸進的開発・評価サイクル支援](../requirements/NFR-mck7v-iterative-metrics-evaluation.md)
- Related Tasks:
  - [T-0mcn0 最小実行基盤タスク](../tasks/T-0mcn0-minimal-foundation/README.md)
  - [T-7k08g プロンプト・エージェント可視化タスク](../tasks/T-7k08g-prompt-visibility/README.md)

## Context

本プロジェクトでは、Claude Skills ベースのエージェント実行において、**プロンプト可視化・漸進的開発（Progressive Disclosure の評価・改善）** を支える Observability が必須となる。

一方で、以下の制約・方針がある：

1. **ビジネスロジックへのログ／テレメトリ混入を極力避けたい**
   - 各スキル実装・エージェント実装のクラス／メソッド内に `logger.info(...)` や計測コードを埋め込むと、ビジネスロジックの 1/2 以上がログ行で占められるリスクがある。
   - Observability は **cross-cutting concern** として、アプリケーション組み立て（composition root）やフレームワーク側のコールバックで扱うことを基本としたい。

2. **LangFuse / Azure Application Insights などのベンダー固有 API を直接書きたくない**
   - コード中に `TelemetryClient` や `Langfuse` SDK 呼び出しを散在させるのではなく、
   - **OpenTelemetry + OTLP** を標準 API／ワイヤプロトコルとし、ベンダー固有の差異は Exporter 設定や環境変数側で吸収する構成が望ましい。

3. **LangChain4j の Observability / Agentic API を最大限活用したい**
   - LangChain4j には
     - Chat / LLM レベルの Observability（`ChatModelListener` 等）
     - Agentic レベルの Observability（`beforeAgentInvocation` / `afterAgentInvocation` 等）
       が用意されており、
   - これらを活用することで、ビジネスロジック側に計測コードを入れずに、エージェント・LLM 呼び出し・ツール呼び出しを横断的に計測できる。

4. **Agentic AI（Workflow / Supervisor / SubAgent）を多用する前提**
   - ADR-q333d で定義した通り、Workflow 型と Pure Agent 型のハイブリッド構成を採用し、Phase 1 から単一スキルに対しても Pure Agent（Supervisor/SubAgent）を積極的に用いる。
   - よって Observability も **「Agent 単位」** を第一級の観測単位として扱う必要がある（ChatModel レベルのみでは不十分）。

5. **インフラ前提**
   - Java コマンドラインアプリケーションを前提（Spring Boot / Quarkus などのフレームワークは Phase 1 では利用しない）。
   - ローカル開発では LangFuse、将来的なクラウド本番では Azure Application Insights（Azure Monitor）を候補とする。

この状況下で、

- **ビジネスロジックに手を入れず（cross-cutting）、**
- **ベンダー非依存（OTLP）で、**
- **Agentic AI を第一級に観測しつつ、必要に応じて LLM レベルも掘れる**

Observability 統合戦略を決定する必要がある。

## Success Metrics

- メトリック 1：`ビジネスロジック（スキル実装・エージェント実装）クラス内に、ベンダー固有 Observability SDK への直接依存が存在しない`
- メトリック 2：`Observability のエントリポイントが LangChain4j の Agentic Observability / ChatModel Observability コールバックに集約されている`
- メトリック 3：`OTLP 経由で LangFuse（ローカル）／Azure Application Insights（本番）へ、同一 Span/Metric スキーマのデータが送信される`
- メトリック 4：`エージェント単位・LLM コール単位のトレース、トークン消費、レイテンシがダッシュボード上で可視化される`
- メトリック 5：`Observability 関連コードが、アプリケーション組み立て層（Agent ビルダー／モデルビルダー）とインフラ層（OTel 初期化）に局在する`

## Decision

<!-- State the decision clearly in active voice. Start with "We will..." or "We have decided to..." and describe the core rules, policies, or structures chosen. Include short examples if clarifying. -->

### Decision Drivers（オプション）

以下の要件に基づいて選択判断を行う：

- ビジネスロジックへのテレメトリ混入を避けること（cross-cutting concern）
- ベンダーロックインを回避すること（OTLP 標準化）
- Agentic AI を第一級の観測単位とすること（Agent 単位の span）
- LangChain4j の Observability API 活用を最大化すること
- CLI アプリケーション環境での実装容易性

### Considered Options

- **Option A: ベンダー別の Observability SDK を直接使用**
  - LangFuse SDK や Azure Application Insights `TelemetryClient` をビジネスコード内で直接呼び出す。
  - コンタクトポイントごとに `logPrompt(...)` `trackTrace(...)` などを埋め込む。

- **Option B: OpenTelemetry + OTLP のみ（LangChain4j 非依存）**
  - LLM 呼び出しやスキル実行箇所で、OpenTelemetry の `Tracer` / `Meter` を直接呼び出す。
  - LangChain4j の Observability / Agentic API には依存せず、すべて自前で計測ポイントを挿入。

- **Option C: LangChain4j Observability（Agentic + ChatModel）+ OpenTelemetry + OTLP**
  - **計測トリガは LangChain4j 側のコールバック（Agentic Observability / ChatModel Observability）に集約**。
  - コールバック内では **OpenTelemetry API のみ** を使用し、 OTLP Exporter の設定で LangFuse / Azure へ出力先を切り替える。
  - ビジネスロジック／スキル実装には計測コードを入れない。

### Option Analysis

| 観点                       | Option A: ベンダー SDK 直書き | Option B: OTel 直書き      | Option C: LangChain4j + OTel |
| -------------------------- | ----------------------------- | -------------------------- | ---------------------------- |
| ビジネスロジックからの分離 | 低い（呼び出しが散在）        | 中（OTel 呼び出しが散在）  | 高い（コールバック層に集約） |
| ベンダーロックイン         | 高い（SDK 依存）              | 低い（OTLP）               | 低い（OTLP）                 |
| Agentic AI との親和性      | 中（自前で相関付けが必要）    | 中（自前で相関付けが必要） | 高い（Agentic API で観測）   |
| 導入コスト                 | 中                            | 中〜高                     | 中                           |
| LangChain4j 機能活用度     | 低                            | 低                         | 高                           |
| CLI アプリへの適用容易性   | 中                            | 中                         | 中〜高                       |

### Chosen Option

**Option C: LangChain4j Observability（Agentic + ChatModel）+ OpenTelemetry + OTLP** を採用する。

より具体的には：

1. **観測ポイント（トリガ）は LangChain4j のコールバックに限定する**
   - Agentic AI の実行には Agentic Observability API（`beforeAgentInvocation` / `afterAgentInvocation` 等）を利用し、**エージェント単位**での開始／終了を計測する。
   - LLM 呼び出しレベルの詳細（トークン数、モデルパラメータなど）が必要な場合は、ChatModel Observability（`ChatModelListener` 等）を併用して **LLM コール単位**の計測を行う。
   - これらのコールバックでのみ計測処理を記述し、スキル実装／ビジネスロジックには手を入れない。

2. **テレメトリの発行は OpenTelemetry API 経由に統一し、OTLP Exporter で LangFuse / Azure へ切り替える**
   - コールバック内からは `Tracer` / `Meter` 等の OpenTelemetry API のみを呼び出し、ベンダー固有の SDK には直接依存しない。
   - OTLP Exporter の設定（エンドポイント URL、ヘッダ、接続文字列など）を変更することで、LangFuse / Azure Application Insights などのバックエンドを切り替える。

3. **AgenticScope を用いて Span 間の関連付け・コンテキスト共有を行う**
   - AgenticScope に「エージェント実行 Span ハンドル」や「直近の LLM 呼び出し情報」を保存し、サブエージェント／ツール実行との親子関係を構築する（ADR-ae6nw との整合）。
   - これにより、Workflow / Supervisor / SubAgent の階層構造を持ったトレースツリーを構築し、LangFuse や Azure 上での可視化を容易にする。

以上により、**cross-cutting concern としての Observability** を実現しつつ、ベンダー非依存・Agentic AI フレンドリな監視基盤を構築する。

## Rationale

1. **ビジネスロジックからの分離（cross-cutting concern）**
   - 計測コードがビジネスロジック内に散在すると、SKILL 実装の可読性が低下し、Progressive Disclosure や Context Engineering の検証に集中しづらくなる。
   - LangChain4j が提供する Agentic / ChatModel の Observability コールバックに計測を集約することで、**「どこで観測しているか」が明確になり、スキル実装は純粋なロジックに専念できる**。

2. **ベンダーロックインの回避**
   - Observability の導入初期フェーズでは LangFuse を前提とするが、将来的に Azure Application Insights や他ツールへ移行する可能性がある。
   - OpenTelemetry + OTLP を標準インターフェースとし、**Exporter の差し替えだけで LangFuse / Azure 双方に対応**できる構成は、長期的な柔軟性・保守性の観点で有利。

3. **Agentic AI を第一級で観測できる**
   - 本プロジェクトは ADR-q333d に従い、Workflow / Supervisor/SubAgent を組み合わせた Agentic パターンを多用する。
   - そのため、「LLM 呼び出し毎」だけでなく、「どのエージェントがどの入力で何を出力したか」「どの SubAgent が何回呼ばれたか」を観測できることが重要。
   - Agentic Observability API を使えば、**エージェント／サブエージェント単位の span** を簡潔に構築できる。

4. **LangChain4j 機能の最大活用**
   - 既に LangChain4j には Observability / AgenticScope / Agentic パターンなど、多数の機能が存在する。
   - これらを活用すれば、OpenTelemetry の生 API を直接各所に書かずとも、**LangChain4j の拡張ポイントに集中して計測コードを書くだけで済む**。
   - 将来的に LangChain4j 側で Observability 機能が強化された場合も、コールバック層のみ修正すればよい。

## Consequences

### Positive

- ビジネスロジック（スキル・エージェント）から Observability を切り離し、実装の見通しがよくなる。
- OTLP を介して LangFuse / Azure Application Insights の双方に対応できるため、環境ごとのスイッチが容易。
- AgenticScope と組み合わせることで、Agentic AI の階層構造（Supervisor → SubAgent → Tool）をトレースとして再現できる。

### Negative

- LangChain4j の Observability API と OpenTelemetry の両方を理解する必要があり、導入時の学習コストは一定程度発生する。
- CLI アプリであるため、フレームワーク（Spring Boot / Quarkus）の自動計測機能は使わず、OpenTelemetry SDK の初期化・エクスポータ設定を自前で記述する必要がある。

### Neutral

- 将来的に LangChain4j 側で OTLP 直接対応の Observability 機構が追加された場合、本 ADR の実装は簡略化される可能性がある（が、基本方針＝cross-cutting + OTLP は変わらない）。

## Implementation Notes

### 1. OpenTelemetry SDK / OTLP Exporter の初期化

CLI アプリケーション起動時に、OpenTelemetry SDK を初期化する。ここでは **環境変数または設定ファイルでバックエンドを選択** できるようにする。

```java
public final class TelemetryBootstrap {

    public static OpenTelemetrySdk initOpenTelemetry() {
        String backend = System.getenv().getOrDefault("OBS_BACKEND", "console");

        SdkTracerProvider tracerProvider;

        switch (backend.toLowerCase()) {
            case "langfuse" -> {
                // LangFuse Cloud の OTLP endpoint 例
                OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(System.getenv("LANGFUSE_OTLP_ENDPOINT"))
                    .addHeader("Authorization", System.getenv("LANGFUSE_OTLP_AUTH"))
                    .build();

                tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                    .build();
            }
            case "azure" -> {
                // Azure Monitor OpenTelemetry Exporter を利用する構成を想定
                // （Azure 側のサンプルに従い、接続文字列から exporter を構成）
                tracerProvider = AzureMonitorTracerProviderFactory.fromEnv();
            }
            default -> {
                // デフォルト: ログ出力のみ
                SpanExporter logging = LoggingSpanExporter.create();
                tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(logging))
                    .build();
            }
        }

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();

        return sdk;
    }
}
```

- `AzureMonitorTracerProviderFactory` は Azure Monitor OpenTelemetry Exporter の設定ロジックをラップするファクトリ（インフラ層）として実装する。アプリ本体からは OpenTelemetry の API のみを利用する。

### 2. Agentic Observability 連携（エージェント単位）

AgenticServices でエージェントを組み立てる際に、`beforeAgentInvocation` / `afterAgentInvocation` を指定し、その中で OpenTelemetry の `Tracer` を利用して Span を生成・終了する。

```java
public final class AgenticTelemetry {

    private final Tracer tracer;

    public AgenticTelemetry(Tracer tracer) {
        this.tracer = tracer;
    }

    public <T> T instrumentAgenticService(T agenticService) {
        // AgenticServices.agentBuilder(...) をラップして、Observability コールバックを注入する想定。
        // インターフェースは LangChain4j の API に合わせて調整する。
        return AgenticServices.agentBuilder(agenticService)
            .beforeAgentInvocation(req -> {
                String agentName = req.agentName();
                Span span = tracer.spanBuilder("agent:" + agentName)
                    .setAttribute("agent.name", agentName)
                    .setAttribute("agent.inputs.preview", truncate(req.inputs().toString(), 512))
                    .startSpan();

                // AgenticScope に Span を保存して、子ステップからも参照できるようにする
                req.agenticScope().writeState("otel.span.agent." + agentName, span);
            })
            .afterAgentInvocation(res -> {
                String agentName = res.agentName();
                Span span = res.agenticScope()
                    .readState("otel.span.agent." + agentName, null);

                if (span != null) {
                    span.setAttribute("agent.output.preview",
                                      truncate(String.valueOf(res.output()), 512));
                    span.end();
                }
            })
            .build();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
```

- 上記は概念的なコードであり、実際には LangChain4j の Agentic API のシグネチャに合わせて実装する。
- ポイントは、
  - **Agentic Observability のコールバックだけで Span を生成／終了している**
  - **Span ハンドルを AgenticScope に保存し、後続処理と関連付け可能にしている**
    ことである。

### 3. ChatModel Observability 連携（LLM コール単位）

より詳細なトークン数・モデル名などを記録したい場合、ChatModelListener を併用して LLM コール単位の Span を追加する。

```java
public class OTelChatModelListener implements ChatModelListener {

    private final Tracer tracer;

    public OTelChatModelListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        Span span = tracer.spanBuilder("llm.call")
            .setAttribute("llm.model", ctx.getModelName())
            .setAttribute("llm.provider", ctx.getProvider())
            .setAttribute("llm.prompt.preview", truncate(ctx.getPrompt(), 512))
            .startSpan();

        ctx.getExtensions().put("otel.span.llm", span);
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        Span span = (Span) ctx.getExtensions().get("otel.span.llm");
        if (span != null) {
            span.setAttribute("llm.tokens.input", ctx.getPromptTokens());
            span.setAttribute("llm.tokens.output", ctx.getCompletionTokens());
            span.end();
        }
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        Span span = (Span) ctx.getExtensions().get("otel.span.llm");
        if (span != null) {
            span.recordException(ctx.getError());
            span.end();
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
```

- Agentic 側で開始した Span と LLM 側の Span は、OpenTelemetry の現在コンテキストにより自動的に親子関係が構築されることを想定（`span.makeCurrent()` の利用など）。
- これにより、LangFuse / Azure 上で「エージェント → LLM コール → ツール実行」までのトレースツリーが確認できる。

### 4. CLI アプリの組み立て例

```java
public class Main {

    public static void main(String[] args) {

        // 1. OpenTelemetry の初期化
        OpenTelemetrySdk otel = TelemetryBootstrap.initOpenTelemetry();
        Tracer tracer = otel.getTracer("langchain4j-cli");

        // 2. ChatModel の構築（ChatModelListener で LLM コール観測）
        ChatLanguageModel baseModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .addListener(new OTelChatModelListener(tracer))
            .build();

        // 3. Agentic テレメトリの設定
        AgenticTelemetry agenticTelemetry = new AgenticTelemetry(tracer);

        // 4. ビジネスロジック（スキル／エージェント）を生成し、テレメトリでラップ
        CreativeWriter rawWriter = AgenticServices.create(CreativeWriter.class, baseModel);
        CreativeWriter writer = agenticTelemetry.instrumentAgenticService(rawWriter);

        // 5. 実行（ビジネスロジック側は Observability 非依存）
        String story = writer.write("LangChain4j と Observability の設計");
        System.out.println(story);
    }
}
```

- Main でやっていることは、
  - OpenTelemetry 初期化
  - LangChain4j モデル／エージェント構築時に Observability 用コールバックを差し込む
    のみであり、`CreativeWriter` インターフェースや SKILL 実装には Observability コードが登場しない。

## Platform Considerations

- Java コマンドラインアプリケーションのため、OpenTelemetry SDK の初期化・シャットダウンを適切に行う（アプリ終了時に `tracerProvider.shutdown()` など）。
- ローカル開発では LangFuse（Docker コンテナ or LangFuse Cloud）を OTLP で利用し、プロンプト・応答内容の可視化に重点を置く。
- 本番では Azure Monitor / Application Insights を Exporter 経由で利用し、他のアプリケーションログ・メトリクスと統合したダッシュボード構成を採用する。

## Security & Privacy

- プロンプトや応答本文を Span 属性／イベントとして送信する場合、PII や機密情報の扱いに注意する。
  - 本文をそのまま送らず、ハッシュ化や一部トリミング／マスキングを行うポリシーを検討する。

- API キー（OpenAI, LangFuse, Azure Application Insights など）は環境変数またはシークレットマネージャで管理し、コードベースにハードコードしない。

## Monitoring & Logging

- Observability データの主役はトレース／メトリクスとし、ログは最小限（初期化ログ、致命的エラー）に留める。
- LangFuse 側ではプロンプト履歴・モデル比較、Azure 側では依存関係マップ・アラート（エラー率・レイテンシ）などを活用し、漸進的開発サイクル（NFR-mck7v）の定量評価を行う。

## Open Questions

- [ ] LangChain4j Agentic Observability API / ChatModel Observability API のバージョン変化にどう追随するか
- [ ] 本番環境での OTLP Exporter のリトライ・バッファ戦略（ネットワーク障害時の挙動）
- [ ] トレーサビリティとプライバシのバランス（どこまでプロンプト／応答を可視化するかのガイドライン策定）

## External References（オプション）

<!-- External standards, specifications, articles, or documentation only -->

- [OpenTelemetry Documentation](https://opentelemetry.io/docs/) - OpenTelemetry の標準仕様とベストプラクティス
- [OpenTelemetry Protocol (OTLP)](https://opentelemetry.io/docs/specs/otlp/) - OTLP ワイヤプロトコル仕様
- [LangFuse Documentation](https://docs.langfuse.com/) - LangFuse の使用ガイドおよび OTLP インテグレーション
- [Azure Monitor OpenTelemetry Exporter](https://learn.microsoft.com/en-us/azure/azure-monitor/app/opentelemetry-enable) - Azure Application Insights との連携方法
