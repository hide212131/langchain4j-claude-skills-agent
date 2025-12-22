# T-0mcn0 最小実行基盤デザイン

## Metadata

- Type: Design
- Status: Approved
  <!-- Draft: Work in progress | Approved: Ready for implementation | Rejected: Not moving forward with this design -->

## Links

<!-- Internal project artifacts only. Replace or remove bullets as appropriate. -->

- Associated Plan Document:
  - [T-0mcn0-minimal-foundation-plan](./plan.md)

## Overview

FR-mcncb の最小成立と後続可視化/Observability を支えるため、SKILL.md パース、ダミー LLM を用いた Plan/Act/Reflect スタブ、Gradle ビルド環境、プレースホルダ可視化/エラーハンドリングを一括で用意する。

## Success Metrics

- [x] SKILL.md を POJO 化し、固定応答の Agentic フローが完走する。
- [x] Plan/Act/Reflect 各ステップで可視化プレースホルダイベントを出力する。
- [x] Gradle ビルド・テストが成功し、依存が揃う。

## Background and Current State

- Context: FR-mcncb の実行系が未整備で、FR-hjz63/NFR-mt1ve 実装前に動作する土台が必要。
- Current behavior: ビルド設定やランナー、パーサ/LLM モックが存在しない。
- Pain points: 可視化/エラー処理を試せる実行源がなく、以降のタスクが着手できない。
- Constraints: 日本語ログ、最小構成で決定論的テストが可能であること。
- Related ADRs: \[/docs/adr/ADR-ehfcj-skill-execution-engine.md], \[/docs/adr/ADR-q333d-agentic-pattern-selection.md], \[/docs/adr/ADR-ae6nw-agenticscope-scenarios.md], \[/docs/adr/ADR-ij1ew-observability-integration.md]

## Proposed Design

### High-Level Architecture

```
SKILL.md -> Parser -> SkillModel (POJO)
                     -> Dummy LLM -> Plan/Act/Reflect Stub -> Result Text
                         |-> Visibility Placeholder Logs (phase tagged)
```

### Components

- SkillParser (最小): YAML frontmatter + 本文を読み取り POJO 化。必須項目のみ。
- DummyAgentFlow: ADR-q333d を参照し、**Workflow 型**の簡易実装で Plan/Act/Reflect を順実行（明示制御で最小化）。LLM は固定文字列を返すダミー。
- VisibilityPlaceholder: Phase/skillId/runId を含む簡易ログ出力。将来の T-7k08g で拡張。
- ErrorGuard: try-catch と 1 回のリトライ枠組み、例外時の日本語ログ。

#### 可視化プレースホルダのフィールド

- phase（parse/plan/act/reflect/error）
- skillId
- runId
- step（plan.prompt/act.call/reflect.eval など）
- message（日本語の簡潔な説明）
- inputSummary（任意・マスク済み）
- outputSummary（任意・マスク済み）
- error（任意・コード/メッセージ、日本語）

### Data Flow

- SKILL.md 読み込み → パース → ダミー LLM で Plan/Act/Reflect → テキスト結果 → ログ/可視化プレースホルダ出力。

### Storage Layout and Paths (if applicable)

- `src/main/java/...` にパーサ/フロー/可視化プレースホルダ。
- テスト用 SKILL.md を `src/test/resources/skills/` に配置。

### API/Interface Design (if applicable)

Usage

```bash
./gradlew run --args="--skill src/test/resources/skills/hello/SKILL.md"
```

- オプション例: `--skill <path>`, `--visibility-level basic`.
- Java API: `runSkill(Path skillPath, VisibilityLevel level)`（仮）。

Implementation Notes

- リソースは try-with-resources で管理。
- 「Manager」など曖昧な命名は避け、責務を明確化。

### Data Models and Types

- `SkillDocument`（id/title/description/body）。
- `VisibilityEvent`（phase/skillId/runId/step/message/inputSummary/outputSummary/error）※プレースホルダ。

### Error Handling

- パース/実行失敗時に日本語メッセージでログ。1 回のリトライ枠組みを用意し、致命時は終了コードを返す。

### Security Considerations

- ローカルファイルのみ読み取り（外部呼び出しなし）。シークレットを含まないテストデータを使用。

### Performance Considerations

- 最小構成でオーバーヘッドは軽微。可視化出力はレベルで抑制可能にする。

### Platform Considerations

#### Unix

- デフォルトパスは POSIX 想定。パーミッション依存なし。

#### Windows

- パス区切りを `Path` API に委ねる。

#### Filesystem

- ケース感度非依存。テスト用ファイルを短パスに配置。

## Alternatives Considered

1. 先に可視化フレームワークを実装し、実行系を後で足す
   - Cons: イベントソースがなく検証できない。
2. 本番 LLM 実装から着手する
   - Cons: テストの決定論性がなく、最小機能に過剰。

Decision Rationale

- ダミー実行 + プレースホルダ可視化を先に用意することで、後続タスクの検証基盤を早期に確保できるため。

## Migration and Compatibility

- 後続タスクで LLM や可視化を差し替えても、同 API とイベントスキーマの枠組みを維持。

## Testing Strategy

### Unit Tests

- パーサの成功/失敗ケース。
- DummyAgentFlow が固定レスポンスを返し、Plan/Act/Reflect が順に呼ばれること。
- VisibilityPlaceholder が phase 付きで出力すること。

### Integration Tests

- テスト用 SKILL.md で e2e 実行し、結果文字列とログ出力を検証。

### External API Parsing (if applicable)

- 外部 API 呼び出しなし。

### Performance & Benchmarks (if applicable)

- 最小構成のため計測不要（手動確認のみ）。

## Documentation Impact

- README か専用ドキュメントに実行方法と制約を追記（本タスク内で必要最低限）。

## External References (optional)

- N/A

## Open Questions

- [x] Workflow 型と Pure Agent 型のどちらを最小実装とするか。→ Workflow 型を採用（明示制御でステップ順序を固定しやすく、最小構成に向くため）。
- [x] リトライの条件とログレベルのデフォルト設定。→ 1 回リトライのみ、固定ディレイなし。ログレベルは info（通常）/warn（リトライ時）/error（失敗確定）。
- [x] 可視化プレースホルダのイベントフィールドをどこまで含めるか。→ 上記フィールドセット（phase/skillId/runId/step/message/inputSummary/outputSummary/error）で実装。

---

## Template Usage

For detailed instructions on using this template, see [Template Usage Instructions](../../templates/README.md#design-template-designmd) in the templates README.
