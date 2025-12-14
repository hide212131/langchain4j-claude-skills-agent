# T-7k08g プロンプト・エージェント可視化デザイン

## Metadata

- Type: Design
- Status: Draft
  <!-- Draft: Work in progress | Approved: Ready for implementation | Rejected: Not moving forward with this design -->

## Links

<!-- Internal project artifacts only. Replace or remove bullets as appropriate. -->

- Associated Plan Document:
  - [T-7k08g-prompt-visibility-plan](./plan.md)

## Overview

FR-hjz63 に基づき、SKILL.md パースから AgenticScope の Plan/Act/Reflect 実行、生成物出力までのプロンプト・内部状態・メトリクスを時系列かつ構造化で記録する設計をまとめる。LangChain4j Agentic API への計装ポイントと出力フォーマットを定義し、LangFuse/OTLP などの外部観測先へ連携可能な形にする。

## Success Metrics

- [ ] SKILL パース入力/結果、生成された POJO を全て構造化記録できる。
- [ ] LLM 入出力（システム/ユーザー/コンテキストプロンプト、応答）が時系列で取得できる。
- [ ] AgenticScope コンテキスト・メモリ・履歴・入出力パラメータ・メトリクスを可視化し、FR-hjz63 の受け入れ基準を満たす。

## Background and Current State

- Context: Claude Skills エージェントの最小実行経路（FR-mcncb）に可視化を付与し、後続の複雑スキル・連鎖実行や Observability 統合（NFR-30zem）の基盤とする。
- Current behavior: 可視化の統一的な計装レイヤーが未整備。LangChain4j の AgenticScope ログはデフォルト出力のみで、SKILL パース結果や LLM プロンプトは散在。
- Pain points: プロンプト/状態の欠落ログ、収集項目の一貫性不足、外部観測先とのスキーマ不一致。
- Constraints: 日本語メッセージ、決定論的なテストスキルを使った e2e 検証、機密情報マスキング。
- Related ADRs: \[/docs/adr/ADR-q333d-agentic-pattern-selection.md], \[/docs/adr/ADR-ij1ew-observability-integration.md]

## Proposed Design

### High-Level Architecture

```
SKILL.md -> Parser -> SkillModel
                     |                +--> LangFuse Export
                     v                |
Agentic Workflow (Plan/Act/Reflect) --+--> OTLP Export (traces/logs/metrics)
                     |                |
                     +--> Visibility Logger (structured events)
```

### Components

- VisibilityLogger（新設ユーティリティ、具体名後述）: SKILL パース結果、LLM プロンプト、AgenticScope 状態、入出力パラメータ、メトリクスを構造化イベントとして生成。マスキングフィルタを備える。
- Parser Hook: SKILL.md 読み込み時に YAML frontmatter/Markdown 本文、JSON Schema 検証結果をイベント化。
- Agentic Hooks: Plan/Act/Reflect の各ステップでプロンプト/応答と決定を記録。エラーハンドリング（NFR-mt1ve）用の例外情報/リトライ情報も併記。
- Exporters: OTLP（トレース/ログ/メトリクス）、LangFuse（ローカル比較用）。共通イベントスキーマから変換。
- Minimal Execution Stub: ダミー LLM 応答と最小パース/実行スタブでイベント発火を先に実装し、テストファーストでスキーマとフックの契約を固める。

### Data Flow

- 入力: SKILL.md + 実行パラメータ。
- パース: YAML frontmatter と本文を POJO 化し、スキーマ検証結果をイベントに記録。
- 実行: AgenticScope の Plan/Act/Reflect それぞれでプロンプト/応答/決定/メトリクスをイベント化。
- 出力: Visibility Logger が構造化イベントをキューイングし、同期/非同期に OTLP と LangFuse へ送出。

### Storage Layout and Paths (if applicable)

- 設定: `resources/` 配下に観測設定（サンプリング、マスキングルール、エクスポーター切替）を配置予定。
- 生成物: ローカルデバッグ用の JSON ログ（機密はマスク済み）を `build/logs/visibility/` に保存（開発時のみ）。

### API/Interface Design (if applicable)

Usage

```bash
java -jar agent.jar --skill path/to/SKILL.md --visibility-level debug
```

- パラメータ例: `--visibility-level (off|errors|basic|debug)`, `--exporter (none|otlp|langfuse)`, `--masking-rules path`.
- Agent 内部 API: `VisibilityLogger.recordPrompt`, `recordAgentState`, `recordMetrics`（仮）を通じて統一スキーマで記録。

Implementation Notes

- 名前に "Manager"/汎用 "Util" を避け、責務ごとに命名（例: `VisibilityEvents`, `PromptTraceExporter`）。
- try-with-resources でエクスポーターのリソースを管理し、リークを防止。

### Data Models and Types

- `VisibilityEvent`: イベント種別（parse/prompt/agent-state/metrics/error）、タイムスタンプ、skillId、runId、phase、payload（構造化オブジェクト）。
- `PromptPayload`: system/user/context プロンプト、モデル応答、トークン使用量。
- `AgentStatePayload`: AgenticScope メモリ、決定内容、入出力パラメータ。
- `MetricsPayload`: レイテンシ、トークン数、エラー率、再試行回数。

### Error Handling

- パース/実行失敗時は例外を捕捉し、イベントに `errorCode` と日本語メッセージを付与して記録。
- リトライやフォールバック経路もイベント化し、NFR-mt1ve の検証に利用。

### Security Considerations

- プロンプト・パラメータ中の機密をマスキング（正規表現/キー指定）。マスク結果のみ外部送信。
- 環境変数やシークレットはイベントに含めない。設定で項目除外を定義。

### Performance Considerations

- デフォルトはバッファリング＋非同期送信でオーバーヘッドを低減。レベル別に収集項目を制御。
- サンプリング率を環境別に設定し、過剰トレースを防止。

### Platform Considerations

#### Unix

- 一時ログ出力パスのパーミッション確認。OTLP エンドポイントを環境変数で指定。

#### Windows

- パス区切りを考慮し、設定で上書え可能にする。

#### Filesystem

- 長パス/ケース感度非依存の構成。ログファイル名に日付と runId を含め衝突回避。

## Alternatives Considered

1. Alternative A
   - Pros: LangChain4j 既存ロギングのみを拡張し簡潔。
   - Cons: SKILL パース結果やメトリクスとのスキーマ統一が困難、外部エクスポートの整合性が落ちる。
2. Alternative B
   - Pros: 独自イベントバスを設けて高柔軟。
   - Cons: 実装コストと複雑性が増し、最小要件に対して過剰。

Decision Rationale

- 最小限の Visibility Logger と共通イベントスキーマを中心にし、LangChain4j 既存フックを活用するアプローチがコスト/可視性のバランスに優れるため採用。

## Migration and Compatibility

- 既存ログ出力は残しつつ、新スキーマに段階移行。フラグで旧形式にフォールバック可能にする。
- 後続の FR-mcncb/FR-cccz4/FR-2ff4z でも共通のイベントスキーマを再利用し、後方互換を保つ。

## Testing Strategy

### Unit Tests

- VisibilityLogger のイベント生成・マスキング単体テストを追加。
- SKILL パース成功/失敗ケースのイベント内容を検証。
- ダミー LLM 応答と最小パース/実行スタブを用い、Plan/Act/Reflect で期待イベントが出る赤テストを先に書く。

### Integration Tests

- テスト用 SKILL.md を用いた Plan/Act/Reflect e2e 実行でプロンプト・状態・メトリクスの記録を確認。
- LangFuse/OTLP 送信をモック/ローカルエンドポイントで検証。

### External API Parsing (if applicable)

- LangFuse/OTLP への送信レスポンス例を fixture 化し、JSON パースとフィールドマッピングをテスト。

### Performance & Benchmarks (if applicable)

- 可視化レベル別のオーバーヘッドを簡易ベンチで測定し、目標値（例: 1 リクエストあたり追加レイテンシ < 20ms）を確認。

## Documentation Impact

- 観測設定と出力例を README か専用ドキュメントに追加。
- エラーメッセージとマスキング仕様を記載。

## External References (optional)

<!-- External standards, specifications, articles, or documentation -->

- N/A

## Open Questions

- [ ] LangFuse/OTLP への同時送信をどの段階で行うか（同期 vs 非同期バッファ）。
- [ ] マスキングルールの設定形式（YAML/JSON）とデフォルト項目。
- [ ] LangChain4j AgenticScope のどのフックが最小で十分か（Plan/Act/Reflect 以外の補助フック有無）。

<!-- Complex investigations should spin out into their own ADR or analysis document -->

## Appendix

### Diagrams

```
[Skill Parser] -> [Visibility Logger] -> [Exporter]
        \-> [AgenticScope Hooks] ->/
```

### Examples

```bash
# ローカルで可視化レベル debug、LangFuse 送信なし
java -jar agent.jar --skill examples/hello/SKILL.md --visibility-level debug --exporter none
```

### Glossary

- Visibility Event: 可視化対象の構造化イベント。
- Exporter: LangFuse/OTLP など外部観測先への送信コンポーネント。

---

## Template Usage

For detailed instructions on using this template, see [Template Usage Instructions](../../templates/README.md#design-template-designmd) in the templates README.
