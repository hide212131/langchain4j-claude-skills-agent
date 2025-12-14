# T-9t6cj 実LLM接続タスク

## Metadata

- Type: Task
- Status: In Progress
  <!-- Draft: Under discussion | In Progress: Actively working | Complete: Code complete | Cancelled: Work intentionally halted -->

## Links

- Related Analyses:
  - [AN-f545x-claude-skills-agent](../../analysis/AN-f545x-claude-skills-agent.md)
- Related Requirements:
  - [FR-mcncb 単一スキルの簡易実行](../../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../../requirements/FR-hjz63-prompt-visibility-framework.md)
  - [NFR-30zem Observability 統合](../../requirements/NFR-30zem-observability-integration.md)
- Related ADRs:
  - [ADR-lsart LangChain4j Agentic AI 最新機能の検証と適用](../../adr/ADR-lsart-langchain4j-agentic-verification.md)
  - [ADR-ij1ew Observability 統合](../../adr/ADR-ij1ew-observability-integration.md)
  - [ADR-q333d Agentic パターン選定](../../adr/ADR-q333d-agentic-pattern-selection.md)
- Associated Design Document:
  - [T-9t6cj-llm-integration-design](./design.md)
- Associated Plan Document:
  - [T-9t6cj-llm-integration-plan](./plan.md)

## Summary

LangChain4j の ChatModel/AgenticScope を実LLM（OpenAI Official SDK 経由）に接続し、SKILL.md の Plan/Act/Reflect 実行をダミーでなく動かす最小実装を構築する。環境変数 `OPENAI_API_KEY`/`OPENAI_BASE_URL` による設定注入、テスト時のモック/スキップ切替、および可視化フック（T-7k08g）との連携を前提にする。

## Scope

- In scope: ChatModel/AgenticScope への OpenAI Official SDK 接続、環境変数/設定読み込み、決定論的テスト用モック切替、可視化イベント発火点との配線。
- Out of scope: 複数スキル連鎖や複雑分岐の新機能（FR-2ff4z/FR-cccz4）、LangFuse/OTLP 本番環境へのデプロイ手順、UI ダッシュボード。

## Success Metrics

- 実LLMを呼び出す Plan/Act/Reflect 経路が動作し、FR-mcncb の受け入れ基準に沿ったテキスト成果物が得られる。
- API キー/エンドポイントが `OPENAI_API_KEY`/`OPENAI_BASE_URL` から注入でき、秘匿情報をログに残さない。
- モック/実LLMを設定で切り替え、CI ではモック経路で決定論的にテスト可能。
- 可視化フック（T-7k08g）が実LLM経路でもイベントを取得できる。

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](../../templates/README.md#task-template-taskmd) in the templates README.
