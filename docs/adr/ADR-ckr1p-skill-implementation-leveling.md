# ADR-ckr1p Claude Skills 実装難易度カテゴリと段階導入方針

## Metadata

- Type: ADR
- Status: Approved

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)
  - [Claude Skills Repository – Implementation Difficulty Analysis](../reference/external-materials/Claude%20Skills%20Repository%20%E2%80%93%20Implementation%20Difficulty%20Analysis.md)
- Related ADRs:
  - [ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md)
  - [ADR-mpiub Context Engineering 実装方針](ADR-mpiub-context-engineering-strategy.md)
  - [ADR-ehfcj スキル実行エンジン設計](ADR-ehfcj-skill-execution-engine.md)
  - [ADR-38940 セキュリティ・リソース管理](ADR-38940-security-resource-management.md)
- Impacted Requirements:
  - FR-DRAFT-1（単一スキル実行）
  - FR-DRAFT-2（複雑な手続きによるスキル実行）
  - FR-DRAFT-3（複数スキル組み合わせ実行）
  - FR-DRAFT-4（Progressive Disclosure 実装）
  - NFR-DRAFT-3（セキュリティ・リソース管理）
  - NFR-DRAFT-5（Observability 基盤の統合）

## Context

- Anthropic Skills リポジトリ調査（参考資料上記）で、スキルが **レベル 1～4** に段階化され、必要な実行能力が大きく異なることが判明。
  - レベル 1：テキスト出力のみ（外部実行なし）
  - レベル 2：コード/ファイル生成（実行なし、マルチファイル出力あり）
  - レベル 3：Python/Node/画像処理などローカル実行を伴うバイナリ生成
  - レベル 4：ビルドパイプライン・Playwright など複数ツール連携を伴う高度な実行
- 現状の LangChain4j 実装では、セキュアな外部プロセス実行やヘビーな依存解決の準備が不十分。レベル 3/4 を一気に有効化するとリソース枯渇・セキュリティリスクが高い。
- FR-DRAFT-2（複雑手続き）と FR-DRAFT-3（複数スキル）が扱う範囲が広く、実行環境の負荷と安全性を段階的に整える必要がある。
- 既存 ADR 群は実行エンジンやセキュリティ方針を定義しているが、どのスキルカテゴリをいつ解禁するかのロードマップは未定義。

## Success Metrics

- メトリック 1：Phase 1/2（レベル 1～2）が依存追加なしで安定稼働し、テキスト・マルチファイル出力の E2E テスト成功率が 95% 以上。
- メトリック 2：Phase 3 以降で外部プロセス実行をサンドボックス内に閉じ、CPU/メモリのクォータ超過による失敗率が 1% 未満。
- メトリック 3：Observability（NFR-DRAFT-5）により、各レベルでのトークン消費・実行時間・エラー率がダッシュボードで可視化される。

## Decision

Claude Skills の実装難易度分類（レベル 1～4）を公式な導入フェーズとし、**フェーズごとに有効化する機能・依存・許可コマンドを制御する**。

### Decision Drivers

- リスク低減：外部実行を伴うレベル 3/4 を後段に送り、段階的に安全装備を追加する
- トレーサビリティ：各フェーズの対象スキルと要件（FR-DRAFT-1～4、NFR-DRAFT-3/5）を明示し、進捗を可視化する
- 実装効率：LangChain4j 側のコア改修を小さく保ち、依存導入とサンドボックス強化を順次進める

### Considered Options

- Option A: 全レベル同時対応（ビッグバン）
- Option B: レベル 1→4 の段階導入（選択）
- Option C: レベル 1/2 に限定し、以降は別プロジェクトで扱う

### Option Analysis

- Option A — Pros: 一度の設計で完結 / Cons: 依存爆増・安全性未整備で高リスク
- Option B — Pros: リスクと依存を段階管理、フェーズごとに検証可能 / Cons: レベル 4 完了まで時間がかかる
- Option C — Pros: 早期に安全な最小機能を提供 / Cons: FR-DRAFT-2/3 の達成が後ろ倒し、エンドユーザー価値が限定

## Rationale

- 参考資料の難易度分類が、必要なランタイム機能（ファイル I/O、外部プロセス、ブラウザ自動化）と直結しており、フェーズ設計の軸として妥当。
- セキュリティ・リソース管理（NFR-DRAFT-3）を満たすには、外部プロセス・パッケージ導入をサンドボックスとクォータ付きで漸進導入するのが現実的。
- Observability（NFR-DRAFT-5）をフェーズごとに敷設することで、FR-DRAFT-2/3 のトークンコスト・安定性を定量評価できる。

## Consequences

### Positive

- フェーズ完了ごとに要件達成度を測定でき、失敗時の切り戻しが容易。
- レベル 1/2 で早期に価値を提供しつつ、レベル 3/4 ではサンドボックス強化と依存整備を計画的に進められる。
- スキルカテゴリごとに許可コマンドや依存を分離し、攻撃面積を縮小。

### Negative

- レベル 3/4 対応のリードタイムが伸び、全スキル網羅まで時間がかかる。
- フェーズ間で機能トグルや依存セットを維持する運用コストが発生。

### Neutral

- フェーズ分割により FR-DRAFT-2/3 のスコープを再分解する可能性がある（必要なら requirements で正式化）。

## Implementation Notes

- **Phase 1（レベル 1）**：SKILL.md パース、テキスト生成のみ。外部プロセス禁止。ログとメトリクスのみ導入。
- **Phase 2（レベル 2）**：マルチファイル出力と差分管理を追加。書き込みパスを限定し、実行は依然禁止。
- **Phase 3（レベル 3）**：Python/Node 等の子プロセス実行をサンドボックス（CPU/メモリ/実行時間クォータ、許可コマンドリスト）下で解禁。バイナリ生成の整合性チェックを追加。
- **Phase 4（レベル 4）**：ビルドパイプライン・Playwright 等の多段実行を、隔離された実行環境（例：一時ワークスペース、依存キャッシュ）で実行。ネットワーク・npm install などはホワイトリスト制御。
- 各フェーズで Observability を拡張（トークン消費、外部プロセス結果、リソース使用量、エラー分類）し、次フェーズ移行の判断指標とする。
- 新規タスク作成時は対象スキルのレベルを明記し、レベルに応じた依存セットと検証ステップ（ユニットテスト＋実行ログ確認）をタスク README に追記する。

## Platform Considerations

- レベル 3/4 で導入する依存（Python、Node、ImageMagick、Playwright、LibreOffice など）はオプション扱いとし、存在チェック＋機能フラグで分岐する。
- Unix 前提で実装するが、外部コマンドパスや一時ディレクトリは設定可能にする。

## Security & Privacy

- 子プロセス実行は許可コマンドリストとリソースクォータで制御し、標準入出力をキャプチャして監査ログに残す。
- レベル 4 のビルド・ブラウザ自動化はネットワーク遮断（デフォルト）を基本とし、必要な場合のみ明示的に許可。
- 生成物のパスとサイズを検証し、意図しないファイル上書きや大量出力を検知する。

## Monitoring & Logging

- AgenticScope / Observability パイプラインに、スキルレベル・実行経路・外部プロセスのリソース使用量を計測するイベントを追加。
- レベル 3/4 では外部依存のバージョンと実行結果ログをメトリクスに送信し、失敗の主要因を可視化。

## Open Questions

- [ ] レベル 3/4 のサンドボックスをどの技術（OS 制限 / コンテナ / seccomp）で実装するか → セキュリティ要件（NFR-DRAFT-3）を requirements で正式化の上、別 ADR で決定
- [ ] レベル 4 の npm install / Playwright ブラウザ取得をどのタイミングで行うか（キャッシュ戦略と CI 負荷） → ビルドパイプライン設計タスクで詰める
- [ ] FR-DRAFT-2/3 の細分化が必要か（例：レベル 3.5 相当の段階を設けるか） → requirements フェーズで検討

## External References

- [Claude Skills Repository – Implementation Difficulty Analysis](https://chatgpt.com/s/dr_692320c95ce481919573372b141099f2)
