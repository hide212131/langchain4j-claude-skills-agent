# ADR-ehfcj スキル実行エンジン設計

## Metadata

- Type: ADR
- Status: Draft

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)
- Related ADRs:
  - [ADR-1 Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md)
  - [ADR-3 Context Engineering 実装方針](ADR-mpiub-context-engineering-strategy.md)
- Impacted Requirements:
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
- Related Tasks:
  - [T-0mcn0 最小実行基盤タスク](../tasks/T-0mcn0-minimal-foundation/README.md)

## Context

スキル実行エンジンは、LangChain4j Workflow（ADR-1 で選定）内で、以下の責務を担当：

1. **スキル選択ロジック**：複数スキルの中から、タスク要件に最適なスキルを選択
2. **スキル実行パラメータ生成**：LLM が判定したスキル実行パラメータを取得
3. **スキル実行（単一・連鎖）**：
   - 単一スキル実行（テキスト生成）
   - 複雑な手続き（条件分岐、参照リソース、スクリプト実行）
   - 複数スキル連鎖実行（スキル A の出力 → スキル B の入力）
4. **エラーハンドリング・リトライ**：スキル実行失敗時の自動リトライ・フォールバック
5. **実行履歴・ロギング**：AgenticScope への実行履歴記録、Observability への通知

**制約・仮定**：

- 同期実行（非同期は Phase 2 以降）
- 実行時間：LLM API タイムアウト内（通常 30 秒以内）
- 複数スキル環境：スキルストア（スキル定義集約管理）との連携

## Success Metrics

- メトリック 1：`単一スキル実行が成功し、テキスト生成物が正常に出力される`
- メトリック 2：`複数スキル連鎖実行（A → B）が正常に動作し、前のステップの出力が次のステップの入力として使用される`
- メトリック 3：`スキル実行エラー（外部 API 呼び出し失敗、タイムアウトなど）が自動検出・ログされ、自動リトライが機能する`
- メトリック 4：`複数スキル環境（5～10 スキル）での検索・マッチング・実行が正常に動作する`

## Decision

### Considered Options

- **Option A: オペレーション宣言型 + 共通ツールチェイン実行枠（採用）**
  - SKILL.md メタデータで「許可オペレーション（ファイル I/O、プロセス、ツールチェイン）」と必要ツールを宣言し、ランタイムが検証・計画・実行・成果物管理を共通枠で行う。
- **Option B: 完全アドホック（LLM が逐次コマンド生成・実行）**
  - 事前宣言や制約なしで、LLM が自由にコマンドを流す。

### Option Analysis

| 観点                                   | A: 宣言型+ツールチェイン（採用）        | B: アドホック                      |
| -------------------------------------- | --------------------------------------- | ---------------------------------- |
| スケール（多数スキル）                 | 高い（共通枠＋設定で再利用）            | 低い（挙動が安定せず再利用不可）   |
| 外部ツール・ビルド・ブラウザ対応       | 高い（共通ポリシーで実行・監視）        | 低い（安全・再現性が確保できない） |
| 成果物（複数ファイル/ログ/サムネイル） | 高い（成果物カタログを標準提供）        | 低い                               |
| セキュリティ/リソース制御              | 高い（許可コマンド検証と上限制御）      | 非常に低い                         |
| 運用・観測性                           | 高い（cmd/exit code/stdout/stderr統一） | 低い                               |

## Rationale

**Option A: オペレーション宣言型 + 共通ツールチェイン実行枠**を採用する。理由：

1. **スケールと再利用性**：1000スキルを想定しても、オペレーション宣言と共通コンポーネントの組み合わせで実装爆発を防げる。
2. **堅牢性と安全性**：全実行をツールチェイン枠（1ステップ含む）に載せ、許可コマンド検証・リソース上限・ログ採取を標準化することで、外部プロセス/ビルド/ブラウザを安全に扱える。
3. **成果物の一貫性**：複数ファイル・ログ・スクリーンショット等を成果物カタログで返却し、下流の検証/配布を容易にする。
4. **宣言と実装の分離**：SKILL.md メタデータで必要オペレーション/ツールを明示し、ランタイムが実行計画を構築することで、リポジトリ差分や未知スキルにも対応できる。

## Consequences

### Positive

- スキル追加は「SKILL.md でのメタデータ宣言（許可オペレーション・依存ツール・入力仕様）」を行うだけで完結し、エンジン側コード改変を不要にして個別実装の爆発を防ぐ。
- 実行ポリシー（許可コマンド、リソース上限、観測性）が全スキルで統一され、外部プロセスを伴うスキルでも安全性が向上する。
- 成果物カタログにより、複合成果物（ファイル群・ログ・サムネイル）の扱いが標準化される。

### Negative

- ツールチェイン枠の設計・実装コストが初期にかかる。
- SKILL.md 側に必要オペレーション/依存ツールの記述を追加する運用コストが発生する。

### Neutral

- スキル固有の高度な処理は、共通枠にカスタムステップを挿入する形で対応可能（完全禁止ではないが、最小化する方針）。

## Implementation Notes

- **ロード/検証**：SKILL.md から「許可オペレーション種別（例: `fs-temp` 作業ディレクトリ内読み書き、`fs-project` ワークスペース書き込み、`process-single` 許可ツール単発実行、`process-chain` 複数ステップ実行、`browser-automation` Playwright 等）」と「必要ツール（python-pptx, ffmpeg, playwright, libreoffice 等）」「入力スキーマ/必須パラメータ」を読み込み、実行前に検証する。
- **補助ドキュメント読取**：SKILL.md 以外の手順/参考Markdown（例: html2pptx.md, ooxml.md, README, examples/\*.md 等）を必読し、参照パスとハッシュをトレースに記録する。
- **コード生成の明示**：実行前に必要な生成物（スライド HTML、呼び出し用 JS/TS、コマンド列）をエージェントが作成し、それらも成果物カタログに含める。
- **依存ツール検証**：宣言されたツール（Playwright ブラウザ同梱、sharp、pptxgenjs、markitdown、Pillow/LibreOffice/Poppler など）の存在確認と不足時のエラー/フォールバック方針を実装する。
- **実行計画**：宣言に基づき、1ステップでも必ずツールチェイン枠に載せる。各ステップにタイムアウト/CPU/メモリ/ディスク上限、環境変数、ワーキングディレクトリを設定する。
- **ツールチェイン枠（必須）**：
  - 許可コマンド検証と依存ツール存在チェック
  - 作業ディレクトリの準備・クリーンアップ
  - プロセス実行の監視（タイムアウト、上限超過で強制終了）
  - 観測性の統一: `cmd`, `exit code`, `stdout`, `stderr`, 実行時間を収集
  - 成果物カタログ化: 生成ファイル/ログ/サムネイル等のパス・種別・サイズ・メタ情報を記録し、必要に応じてバンドル（ZIP等）する
  - 失敗時の扱い: どのステップで落ちたかを報告し、部分成果物の保持/廃棄ポリシーを適用
- **成果物モデル**：`ArtifactCatalog`（仮）として、複数成果物を一覧で返す。各エントリは `type`（file/log/screenshot/thumbnail/script/html/command）、`path`、`mime`、`size`、`metadata` を持つ。
- **セキュリティ/リソース**：許可リスト型のコマンド/バイナリ、パス制約、ネットワーク利用の可否、リソース上限をオペレーション単位で適用。
- **観測性**：実行ログと成果物サマリを Observability に送信し、スキル連鎖時にはチェーン全体のトレースを構築。
- **カスタム拡張点**：特殊スキル向けに、ツールチェインの前後や中間に挿入できるフック（例: brand-guidelines 用のフォント検証）を用意するが、基本は共通枠で完結させる。
- **具体例（document-skills/pptx の新規生成）**：
  1. SKILL.md と `html2pptx.md` を全文読了し、参照パス/ハッシュをトレースに記録。
  2. 依存チェック：Playwright（ブラウザ同梱）、sharp、pptxgenjs、Node、Python+thumbnail.py 依存（Pillow 等）を確認。不足時はエラー。
  3. コード生成：各スライドの HTML（720×405pt、placeholder付き、画像は事前PNG化）と `scripts/html2pptx.js` を呼び出すラッパJSを生成。生成物は成果物カタログに含める。
  4. ツールチェイン実行（fs-temp + process-chain + browser-automation）：ラッパJSを Node で実行して `output.pptx` を生成し、続けて `python scripts/thumbnail.py output.pptx` でサムネイル作成。cmd/exit/stdout/stderr/elapsed を記録。
  5. 成果物カタログ：`output.pptx`、サムネPNG、生成HTML/JS、実行ログを登録。失敗時はどのステップで落ちたかと部分成果物の扱いを記録。

## Platform Considerations

- **マルチスレッド対応**：ツールチェイン実行時の作業ディレクトリと成果物カタログはリクエスト単位で分離し、スレッドセーフに扱う。
- **リソース制限**：タイムアウト/CPU/メモリ/ディスクをステップ単位とチェーン全体に適用し、外部プロセス暴走を防ぐ（ADR-6 参照）。

## Security & Privacy

- **許可コマンド/ツールの宣言と検証**：SKILL.md の宣言に基づき実行前にチェック。未宣言ツールは拒否。
- **パス制約とクリーンアップ**：作業ディレクトリ外への書き込み禁止、実行後のクリーンアップを徹底。
- **結果マスキング**：ログ出力時に PII を含む可能性がある場合はマスキングを適用。

## Monitoring & Logging

- 各ステップの `cmd/exit code/stdout/stderr/elapsed` を記録し、チェーン全体のトレースを構築。
- 成果物カタログのサマリ（件数/サイズ/パス）をログ化し、ダウンストリーム（検証/配布）に渡す。

## Open Questions

- [ ] ネットワーク利用やブラウザ自動化が必要なスキルへの制約レベル（許可/禁止/限定的許可）の具体化
- [ ] 成果物カタログの永続化期間と保存先（ローカルのみか、オブジェクトストレージにアップロードするか）
- [ ] ツールチェイン宣言のスキーマ詳細（SKILL.md での書式）と後方互換ポリシー

## External References

- [Anthropics Skills Repository](https://github.com/anthropics/skills)
- [LangChain4j Workflow Documentation](https://docs.langchain4j.dev/tutorials/agents/)
