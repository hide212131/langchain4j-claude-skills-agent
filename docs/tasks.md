# 実装タスク集（tasks.md）— langchain4j-claude-skills-agent

本書は **t_wada 風TDD（Red → Green → Refactor）** で進めるための作業台帳です。  
優先度は **P0（MVP 必須） / P1（MVP 完走後すぐ着手したい重要項目） / P2（拡張）**。  
P0 では「API キーをセットすれば LangChain4j Workflow が `./sk run ...` を最後まで流しきる」ことを最速で満たすことを最優先します。各タスクは LangChain4j の Workflow / Agent API を骨格に据え、常に **全体を通した結合** を意識した垂直スライスで進めます。  
**注**：Act（Pure）詳細は別紙 **`spec_skillruntime.md`** を唯一のソース・オブ・トゥルースとします。

---

## 0. 原則（TDD ガイド）
- [ ] まず縦を通す：最初に **疎な実装でも E2E** を成立させ、その後に精度を高める  
- [ ] Red → Green → Refactor を小さく回し、Workflow ノードごとに契約テストを整備  
- [ ] LangChain4j Workflow を “外側の契約” と捉え、内側の実装は差し替え可能に保つ  
- [ ] API キー・環境変数は常に最初に確認し、実機 LLM での疎通を早期に行う  
- [ ] **L1/L2/L3 方針**（L1=メタのみ、L2=SKILL.md全文、L3=オンデマンド参照/実行）を厳守（詳細は `spec_skillruntime.md`）

---

## 1. マイルストーン
- [ ] **M0**：最小 E2E デモ（P0-1〜P0-4）  
- [ ] **M1**：安全性と精度の強化（P0-5〜P0-8）  
- [ ] **M2**：重要タスク拡張（P1）  
- [ ] **M3**：機能拡張（P2）

---

## 2. タスク詳細（P0：MVP 必須）

### P0-0. モジュールスケルトン & AgenticScope 契約定義
- Red  
- [x] `runtime.workflow`, `runtime.provider`, `runtime.skill`, `runtime.blackboard`, `runtime.context`, `runtime.guard`, `runtime.human`, `infra.logging`, `infra.config`, `app.cli` へのクラス生成が行われていない状態を検出する失敗テスト  
- [x] `WorkflowFactory` が LangChain4j `AgenticServices` を経由せずにカスタム Workflow を返そうとした場合に失敗するテスト  
  - [x] AgenticScope の必須キー（`plan.goal`, `act.output.<skillId>` など）が未設定のまま `AgenticScopeBridge` を呼び出すと例外となることを期待する失敗テスト  
- Green  
  - [x] 各パッケージにスケルトン（`package-info.java` または空クラス）を配置し、`WorkflowFactory`, `ProviderAdapter`, `SkillIndex`, `BlackboardStore`, `ContextPackingService`, `SkillInvocationGuard`, `HumanReviewAgentFactory`, `WorkflowLogger`, `RuntimeConfig` の雛形を実装  
  - [x] `AgenticScopeBridge` と `PlanState` / `ActState` / `ReflectFinalSummaryState` ほか AgenticScope DTO 群を作成し、未設定キー検知ロジックを実装  
- Refactor  
- [x] 静的な依存ルールで層間依存（`app` → `runtime` → `infra`）を固定し、テストを緑に保つ  
- DoD  
  - [x] `./gradlew test` がスケルトン・AgenticScope 契約テストを含めて緑  
  - [x] `WorkflowFactory` が LangChain4j Workflow（`AgenticServices.sequenceBuilder()` 由来の `Workflow` インスタンス）を返していることをテストで確認  
  - [x] `spec.md` 2.1 / 3.4 の記載とコードのパッケージ・キーが一致することを確認

### P0-1. Workflow & CLI ブートストラップ（最小 E2E）
- Red  
- [x] `./sk run --goal "demo"` が LangChain4j `Workflow` を起動し、Plan → Act → Reflect の 3 ノードが呼ばれることを検証する失敗テスト（ノードは Stub）  
- [x] `LangChain4jLlmClient` が環境変数 `OPENAI_API_KEY` 未設定で起動すると例外になる失敗テスト  
- Green  
  - [x] LangChain4j Workflow Builder で Plan / Act / Reflect の空ノードを構築し、PicoCLI（等）から起動  
  - [x] `--dry-run` 実行で Fake LLM を使い、`OPENAI_API_KEY` が無くてもテスト可能にする  
- Refactor  
  - [x] Workflow ノード間 DTO を整理し、`AgentService` に閉じ込める  
- DoD  
  - [x] `./sk run --goal "demo" --dry-run` が通る  
  - [x] README/setup.md に環境変数の設定手順を反映  
- [x] `WorkflowFactory` や CLI エントリから `dev.langchain4j.agentic.AgenticServices` など LangChain4j の Workflow/Agent API が直接呼ばれていることを確認（単体テストで検証）

### P0-2. LangChain4j LLM 接続（実 API キーで確認）
- Red  
  - [x] `LangChain4jLlmClient.forOpenAi()` が `ChatLanguageModel.generate()` を呼び、tokens 使用量を返す失敗テスト（Fake モデル）  
  - [x] `./sk run --goal "Working LLM"` 実行時、Assistant 応答がログに記録されることを検証する失敗テスト（Integration テストは Fake LLM で）  
- Green  
  - [x] `./sk run ...` で `LangChain4jLlmClient` を利用する実装を追加（`OPENAI_API_KEY` があれば本物を使用、`--dry-run` は Fake）  
- Refactor  
  - [x] Provider 層に tokens/time の共通メトリクス収集を実装  
- DoD  
  - [x] 実際に API キーを設定し `gpt-5` モデルで 1 度応答を取得（direnv 経由の `OPENAI_API_KEY`、`./sk run --goal "ブランド準拠でスライド"` 実行ログを記録）  
  - [x] 構造化ログに `tokens_in/out` が出力される

### P0-3. SkillIndex + Plan ノード（最小連携）
- Red  
  - [x] `skills/` 配下の複数スキル（例：`brand-guidelines` / `document-skills/pptx`）を `SkillIndexLoader` が読み込み、Plan ノードへ渡す失敗テスト  
  - [x] Plan ノードが goal→推奨順（brand→pptx）を返す失敗テスト  
  - [x] **skillId が任意のパス型であることを前提**に、固定フォルダ名（`resources/`, `scripts/`）に依存しない参照解決を要求する失敗テスト  
- Green  
  - [x] `SkillIndexLoader` と `DefaultPlanner` を Workflow Plan ノードとして接続し、Plan 結果をログに残す  
  - [x] System プロンプトへ L1 要約（name/description）を注入  
  - [x] 固定名に依存しない参照解決ロジック（相対リンク/明示パス/glob）を実装  
- Refactor  
  - [x] Plan 入出力 DTO を定義し、テストで固定値比較  
  - [x] DefaultPlanner を goal/metadata ベースの汎用ロジックへ更新し、特定スキル依存を排除（現状 `brand`/`pptx` 優先が残存）  
- DoD  
  - [x] `./sk run --goal "ブランド準拠で5枚スライド"` の Plan ログが確認できる  
  - [x] 参照先が任意ディレクトリでも Plan→Act で解決できる（固定名に依存しない）

### P0-4. invokeSkill Tool + SkillRuntime（最小成果物 / Pure Act）
- Red  
  - [x] Act ノードが LangChain4j Tool（`invokeSkill(skillId, inputs)`）を経由し、`SkillRuntime` を実行する失敗テスト  
  - [x] Runtime が 1 Stage で `build/out/deck.pptx`（仮テンプレでも可）を生成し、Blackboard に登録する失敗テスト  
  - [ ] **SKILL.md のみのスキル**（L3なし）でも完了する失敗テスト  
- Green  
  - [x] `InvokeSkillTool` を登録し、`DefaultInvoker` + `SkillRuntime` を実働化  
  - [ ] **Pure 方式**：順序配線せず、**単一エージェント＋ツール群**で Act を実装（`readSkillMd/readRef/runScript/validate/writeArtifact/blackboard.*` 等。仕様は `spec_skillruntime.md` に準拠）  
  - [ ] スタブ出力（固定テキスト）を排除し、Skill 定義に基づく実成果物生成を実装  
- Refactor  
  - [x] Blackboard API と Runtime 入出力を整理  
- DoD  
  - [x] `./sk run --goal "ブランド準拠でスライド"` が LLM→brand skill→pptx skill の順で呼ばれ、`build/out/deck.pptx` を生成  
  - [ ] **SKILL.mdのみ**でも expectedOutputs 検証を通過して終了（再現用の最小テストケースを作成）  
  - [ ] Act のログに L1/L2/L3 の投入ログが残る（Disclosure ログフォーマットを確定）  

### P0-5. Evaluator（Reflect）と再試行
- Red  
  - [ ] Reflect ノードが `deck.pptx` の存在とページ数をチェックし、NG なら Plan へ一度差し戻す失敗テスト  
- Green  
  - [ ] `DefaultEvaluator` を Workflow Reflect ノードとして組み込み、再試行ループ（1 回）を実装  
- Refactor  
  - [ ] 評価結果 DTO / ログ出力を整理  
- DoD  
  - [ ] Reflect ログに合否・再試行有無が出る  
  - [ ] 再試行 1 回で停止することをテストで確認

### P0-6. Sandboxed Scripts（安全性の確保）
- Red  
  - [ ] Allowlist 設定（`python3` / `node` / `bash` ほか許可拡張子）以外を拒否する失敗テスト  
  - [ ] `timeoutMs` 超過でプロセスを停止する失敗テスト  
  - [ ] **`build/` 外への書き込み**を拒否する失敗テスト  
- Green  
  - [ ] `Process` ラッパを実装し、Runtime から利用（作業ディレクトリ＝**skill ルート固定**、ネットワーク無効、環境変数最小化）  
- Refactor  
  - [ ] Allowlist/timeout/cwd/env 設定の注入ポイントを共通化  
- DoD  
  - [ ] 危険なスクリプトが実際に拒否される  
  - [ ] プロンプトには stdout JSON の要約のみ注入（コード本文は注入しない）

### P0-7. Context Cache & Progressive Disclosure（MVP 品質）
- Red  
  - [ ] 同一 docref 再投入時のキャッシュヒットを確認する失敗テスト  
  - [ ] 差分投入のみ追加される失敗テスト  
  - [ ] **L1=メタのみ / L2=SKILL.md全文 / L3=オンデマンド** を満たさない投入を拒否する失敗テスト  
- Green  
  - [ ] `ContextCache` を Plan / Act ノードに組み込み、L1/L2/L3 管理を開始  
- Refactor  
  - [ ] 抜粋/要約戦略の差し替えポイントを抽象化  
- DoD  
  - [ ] Disclosure 指標（before/after/tokens/L レベル）がログに記録される  
  - [ ] 2 回目実行で tokens 減少を確認

### P0-8. 構造化ログ & CI
- Red  
  - [ ] Workflow から `workflow_id/context_id/plan`、各ステップの `tokens_in/out` を JSON Lines で出力する失敗テスト  
- Green  
  - [ ] ログ出力ユーティリティを整備し、Plan/Act/Reflect ノードから記録  
  - [ ] GitHub Actions 等で `./sk run --dry-run` を実行する CI ジョブを追加  
- Refactor  
  - [ ] PII 除外ルールとログレベル設定を整理  
- DoD  
  - [ ] CI の最小ジョブが緑  
  - [ ] README にログ確認方法を追記

### P0-9. ドキュメント整合（spec ↔ spec_skillruntime）
- Red  
  - [ ] `spec.md` と `spec_skillruntime.md` の差分検出テスト（用語/L1-L3/入出力契約のズレ）  
- Green  
  - [ ] 差分の自動チェック（簡易 Lint）と PR ゲートを整備  
- DoD  
  - [ ] 以後の変更で両者の整合が常に維持される

---

## 3. タスク詳細（P1：重要）

### P1-1. Context Packing 高度化
- [ ] 章/見出し/コードブロック単位の動的抽出  
- [ ] Skill ごとのソフト上限（≤5k tokens）超過時に自動要約  
- DoD  
  - [ ] 縮退・差分投入がログで確認できる

### P1-2. スクリプト静的スキャン
- [ ] 未審査 `scripts/` を実行不可にする  
- [ ] 危険 API 検知（NG 理由付き）  
- DoD  
  - [ ] 審査レポート（OK/NG+理由）を出力

### P1-3. 失敗シナリオ拡充
- [ ] 入力不足／選択不適合／timeout／Disclosure 過剰投入の各シナリオ  
- DoD  
  - [ ] 次善策ガイドをログと CLI メッセージに出力

### P1-4. Skills 取得と CI 統合
- [x] `./gradlew updateSkills` で固定コミットのスキルを取得するタスクを実装  
- [ ] CI から `./gradlew updateSkills` を実行し、固定コミットを取得  
- DoD  
  - [ ] `skills/` 未追跡でもクリーン環境で E2E 緑

---

## 4. タスク詳細（P2：拡張）

### P2-1. Planner 並列化 / 枝刈り
- [ ] 複数計画案を並列生成 → 評価 → 最良案選択  
- DoD  
  - [ ] 候補比較ログを出力

### P2-2. 入出力スキーマ強化
- [ ] Skill I/O とユーザ入力の自動マッピング精度向上  
- DoD  
  - [ ] Plan の I/O 写像成功率が向上したことを評価レポートで確認

### P2-3. 互換層拡張（Prebuilt Skills 対応）
- [ ] docx / xlsx 等の追加スキル対応  
- DoD  
  - [ ] 追加 E2E シナリオ 1 件緑

---

## 5. テスト計画（抜粋）
- 単体（JUnit 5）  
  - [ ] Workflow スケルトン / Loader / ContextCache / ProviderAdapter / Runtime / Sandbox / Planner / Invoker / Evaluator / Logger  
- 結合  
  - [ ] Workflow（Plan→Act→Reflect）で brand→pptx チェーン  
  - [ ] Sandbox→Runtime（scripts 成功 / 拒否 / timeout）  
- E2E  
  - [ ] CLI で `docs/agenda.md` → `deck.pptx` 生成（正常）  
  - [ ] `agenda.md` 欠落 → 追加入力質問 → 最終的に失敗終了  
  - [ ] **SKILL.mdのみ** のスキルで完走（L3 未使用）  
- 計測  
  - [ ] 2 回目実行で tokens before→after 減少を確認

---

## 6. DoD（Definition of Done）
- [ ] LangChain4j Workflow / Agent API 上で全処理が完結  
- [ ] API キーありで `./sk run ...` が E2E で成功  
- [ ] 仕様の受け入れ基準に合致（成果物・ログ・再試行）  
- [ ] 構造化ログが必須項目を満たす（L1-L3 / before→after / cache）  
- [ ] サンドボックス以外で scripts を実行しない（allowlist/timeout/書込先制限）  
- [ ] **固定ディレクトリ名に依存しない**（skillId は任意パス、参照は相対/明示/glob で解決）  
- [ ] **`spec.md` と `spec_skillruntime.md` の整合**  
- [ ] クリーン環境で CI E2E 緑  
- [ ] ドキュメント更新（requirements/spec/spec_skillruntime/tasks/README/setup）

---

## 7. 作業ノート（推奨運用）
- [ ] `docs/agenda.md` 最小デモデータ（5 枚 / 英日併記）を準備  
- [ ] まず `--dry-run` で API なしのテスト → API キー設定後に本番 LLM を確認  
- [ ] JSON Lines ログを `jq` で検査するスクリプトを用意  
- [ ] ブロッカー発生時はタスクに `[ ] BLOCKED` と理由を追記し、解決タスクを先行

---

## 8. ワークフロー（進め方）
1. **タスク選択**：P0 から順に。着手時に `[ ] WIP` を付記。  
2. **ブランチを切る**：`git switch -c feat/<task-id>-<short-desc>`（例：`feat/P0-1-workflow-bootstrap`）。  
3. **Red**：対象タスク（Workflow ノード単位など）の失敗テストを追加。  
4. **Green**：テストが通る最小実装を入れ、`./gradlew test` を実行。  
5. **Refactor**：命名・重複・責務分離を整えつつテスト緑を維持。  
6. **チェック & ドキュメント**：該当タスクのチェックボックスを `[x]` に更新し、必要に応じて tasks/spec/requirements/`spec_skillruntime`/README を反映。  
7. **コミット & CI**：`git add` → `git commit`（タスク ID を含める）→ PR 作成 → CI 緑を確認。  
8. **次タスクへ**：`main` を同期し、次のタスクに着手。

> 原則：**常に E2E を壊さない**。小さく進めつつ、早期に実 LLM での挙動を確認しながら設計を固める。
