# 実装タスク集（tasks.md）— langchain4j-claude-skills-agent

本書は **t_wada 風TDD（Red → Green → Refactor）** で進めるための作業台帳。  
優先度は **P0（必須） / P1（重要） / P2（拡張）**。各タスクは **チェックボックス**で進捗管理します。

---

## 0. 原則（TDDガイド）
- [ ] 小さく歩く：最小の失敗テスト → 最短の実装 → リファクタを細かく回す  
- [ ] 仮実装 / 三角測量 / 明白な実装を状況で使い分ける  
- [ ] 外部I/F（CLI）と主ユースケース（brand→pptx）を“外から”固定する

---

## 1. マイルストーン
- [ ] **M1**：Index / Cache（P0-1〜2）  
- [ ] **M2**：Provider / Tool 定義（P0-3〜4）  
- [ ] **M3**：Runtime / Scripts（P0-5〜6）  
- [ ] **M4**：Planner（P0-7）  
- [ ] **M5**：Invoker（P0-8）  
- [ ] **M6**：Evaluator / Logs（P0-9〜10）  
- [ ] **M7**：CLI / E2E（P0-11）  
- [ ] **M8**：拡張・堅牢化（P1〜P2）

---

## 2. タスク詳細（P0：必須）

### P0-1. SkillIndex / Loader
- Red  
  - [ ] `SKILL.md` から `name/description/inputs/outputs/stages/keywords` を抽出する失敗テスト  
  - [ ] 未対応フィールドを無視し**警告ログ**が出る失敗テスト  
  - [ ] `resources/` と `scripts/` の相対パス収集テスト  
- Green  
  - [ ] 最小実装でテストを通す（YAML読み＋必要キー抽出）  
- Refactor  
  - [ ] 抽出器を純粋関数化（I/O分離）  
- DoD  
  - [ ] ユニットテスト緑（3件）  
  - [ ] System提示用 **L1要約**（name/description/発火条件短文）を生成

---

### P0-2. ContextCache（抜粋・要約・統計）
- Red  
  - [ ] 同一 docref の再投入で**キャッシュヒット**（tokens_in 減少）  
  - [ ] **差分投入**のみ追加されること  
- Green  
  - [ ] メモリ実装で通す  
- Refactor  
  - [ ] 抽出/要約の戦略差し替え化  
- DoD  
  - [ ] before/after/ヒット率が構造化ログに出る  
  - [ ] `putExcerpt/getExcerpt/recordStats` API 提供

---

### P0-3. ProviderAdapter（OpenAI 既定）
- Red  
  - [ ] メッセージ往復の契約テスト（Fake LLM）  
  - [ ] Tool 呼び出しのブリッジ（ToolSpec 生成）  
- Green  
  - [ ] 最小Adapter＋Fakeで通す  
- Refactor  
  - [ ] tokens/time 計測の注入ポイント整備  
- DoD  
  - [ ] `LlmResult{messages, toolCalls, tokensIn, tokensOut}` 返却

---

### P0-4. `invokeSkill` Tool 定義
- Red  
  - [ ] LLM が `invokeSkill(skillId, inputs)` を選び、`SkillRuntime` に委譲される  
- Green  
  - [ ] ダミーRuntimeで固定応答しテスト通過  
- Refactor  
  - [ ] 例外系の失敗メッセージ整形  
- DoD  
  - [ ] ToolSpec に説明と引数定義（L1要約由来）が載る

---

### P0-5. SkillRuntime（Stages Executor）
- Red  
  - [ ] 1段 stage が `Blackboard` に成果（JSON/file）登録  
  - [ ] 2段 stage で前段文脈を**要約縮退**して再参照（L2→要約→L2’）  
- Green  
  - [ ] Template / JSON検証の最小実装  
- Refactor  
  - [ ] I/Oユーティリティ分離  
- DoD  
  - [ ] 実行記録に `purpose / excerpts(bytes) / tokens_in/out` 出力

---

### P0-6. Script サンドボックス（ローカル実行）
- Red  
  - [ ] Allowlist 外通訳器の拒否テスト（python3/node/bash のみ許可）  
  - [ ] `timeoutMs` 超過で停止する  
  - [ ] `build/out/` 外への書き込みを拒否  
  - [ ] `stdout JSON (status/artifacts)` を取り込み Blackboard 登録  
- Green  
  - [ ] `Process` ラッパ最小実装  
- Refactor  
  - [ ] allowlist/timeout/cwd/env の設定注入  
- DoD  
  - [ ] 危険呼び出しが確実にブロックされる  
  - [ ] **プロンプトへは出力メタのみ**注入（コード本文/冗長ログは除外）

---

### P0-7. Planner（Plan）
- Red  
  - [ ] `goal="ブランド準拠の5枚PPTX"` → **A（brand）→B（pptx）** を推奨順で返す  
  - [ ] 失敗時の**追加入力質問**（例：ブランド素材の所在）を生成  
- Green  
  - [ ] 静的スコア＋LLM要約のハイブリッド実装  
- Refactor  
  - [ ] 重み付けを設定化  
- DoD  
  - [ ] Planに**成功条件**（max_slides/フォント/色）を含む

---

### P0-8. Invoker（Act with autonomy window）
- Red  
  - [ ] `max_tool_calls=3` 超で**打ち切り**  
  - [ ] **require_progress**：同一出力の繰り返し検知で停止  
  - [ ] Blackboard 成果を**次入力へ合成**して再呼び出し  
- Green  
  - [ ] 最小ループ＆ガードで通す  
- Refactor  
  - [ ] 実績（回数/時間/トークン）集計の共通化  
- DoD  
  - [ ] 連鎖ログ（時刻/skillId/入出要約/打切理由）出力

---

### P0-9. Evaluator / Reflect
- Red  
  - [ ] `deck.pptx` の存在・ページ数・メタが閾値OKで Pass  
  - [ ] NG なら **一度だけ** Plan に差し戻し再試行  
- Green  
  - [ ] 最小検証（ファイル存在＋簡易メタ）  
- Refactor  
  - [ ] スキーマ検証の戦略化  
- DoD  
  - [ ] 失敗時に**終了理由**と**次善策**を出す

---

### P0-10. 構造化ログ / メトリクス
- Red  
  - [ ] `workflow_id/context_id/plan`、L1-L3 別 tokens、cache ヒット率の記録  
  - [ ] 連鎖ごとの `tokens_in/out/excerpts(bytes)` 記録  
- Green  
  - [ ] JSON Lines 出力  
- Refactor  
  - [ ] ログレベルと PII 除外  
- DoD  
  - [ ] requirements/spec の必須項目を満たす

---

### P0-11. CLI / E2E（最小デモ）
- Red  
  - [ ] `--goal/--in/--out/--skills-dir` を受け E2E 正常終了  
  - [ ] `build/out/deck.pptx` 生成、ログ指標が出る  
- Green  
  - [ ] ピコCLI等で最小ワイヤリング  
- Refactor  
  - [ ] エラー分類とヘルプ整備  
- DoD  
  - [ ] README のコマンド例が**そのまま通る**  
  - [ ] CI の E2E テスト緑

---

## 3. タスク詳細（P1：重要）
### P1-1. Context Packing 高度化
- [ ] 章/見出し/コードブロック単位抽出の選択可  
- [ ] ソフト上限（Skill ≤5k tokens）超過で自動要約  
- DoD  
  - [ ] 縮退・差分投入がログで確認できる

### P1-2. スクリプトの簡易静的スキャン
- [ ] 未審査 `scripts/` を実行不可に  
- [ ] 危険API検知（NG理由付き）  
- DoD  
  - [ ] 審査レポート（OK/NG+理由）出力

### P1-3. 失敗系の拡充
- [ ] 入力不足／選択不適合／timeout／Disclosure過投入の各シナリオ  
- DoD  
  - [ ] 各シナリオの次善策ガイドを整備

### P1-4. skills 取得（Gradle）とCI統合
- [ ] `./gradlew updateSkills` をCIで実行  
- [ ] 固定コミット取得で再現性確保  
- DoD  
  - [ ] `skills/` 未追跡でもクリーン環境でE2E緑

---

## 4. タスク詳細（P2：拡張）
### P2-1. Planner 並列化/枝刈り
- [ ] 複数計画案 → 評価 → 最良案採用  
- DoD  
  - [ ] 候補比較ログ出力

### P2-2. 入出力スキーマ宣言の強化
- [ ] 自動マッピング精度向上  
- DoD  
  - [ ] Plan の I/O 写像成功率が向上

### P2-3. 互換層（Prebuilt Skills の追加対応）
- [ ] docx/xlsx 等の段階的追加  
- DoD  
  - [ ] 追加E2E 1件緑

---

## 5. テスト計画（抜粋）
- 単体（JUnit 5）  
  - [ ] Loader / ContextCache / ProviderAdapter / Runtime / Sandbox / Planner / Invoker / Evaluator / Logger  
- 結合  
  - [ ] Planner→Invoker→Runtime（brand→pptx チェーン）  
  - [ ] Sandbox→Runtime（scripts の成功/拒否/timeout）  
- E2E  
  - [ ] CLI で `agenda.md` → `deck.pptx` 生成（正常）  
  - [ ] 失敗：`agenda.md` 欠落 → 追加入力質問 → 失敗終了  
- 計測  
  - [ ] 2回目実行で tokens before→after 減少を確認

---

## 6. DoD（Definition of Done）チェックリスト
- [ ] 仕様の受け入れ基準に合致（成果物・ログ・再試行）  
- [ ] 構造化ログが必須項目を満たす（L1-L3 / before→after / cache）  
- [ ] スクリプト実行はサンドボックスのみ（allowlist/timeout/書込先制限）  
- [ ] `skills/` 未追跡でも **CIのクリーン環境でE2E緑**  
- [ ] ドキュメント更新（requirements/spec/tasks/README）

---

## 7. 作業ノート（推奨運用）
- [ ] テストデータ `docs/agenda.md` の最小版作成（5枚／英日併記見出し）  
- [ ] P0 では Fake/Stub を多用してスピード優先  
- [ ] JSON Lines ログを `jq` で検査する簡易スクリプト用意  
- [ ] まず失敗系（入力不足/timeout/過投入）から落として堅牢化

---

## 8. 進め方のワークフロー（1タスクずつ / 緑→コミット→次へ）

**原則**：一度に手を広げない。必ず **1タスクずつ完了（緑）させてから**次へ。

1) **タスク選択**
   - 最優先は **P0 → P1 → P2** の順。各グループは **上から順番**に着手。
   - 対象タスクのチェックボックスに **[ ] WIP** とメモを残す（取り掛かり可視化）。

2) **ブランチ運用**
   - 作業用ブランチを切る：  
     `git switch -c feat/<task-id>-<short-desc>`  
     例：`feat/P0-1-skillindex-loader`
   - バグ/修正は `fix/`、リファクタは `refactor/` を接頭に。

3) **Red**
   - 失敗するテストを書く（最小の1ケースから）。  
   - **まだコミットしない**（ローカルWIPは可）。

4) **Green**
   - 最短の実装でテストを緑にする。必要なら **仮実装→三角測量→明白な実装**で段階的に。
   - **スイート（単体→結合→E2E該当分）をローカルで全通し**。

5) **Refactor**
   - 命名・重複・分解・責務の整理。**常に緑を維持**。

6) **ドキュメント反映**
   - `tasks.md` の該当項目に **[x]** を入れる（サブ項目も更新）。  
   - 仕様/要件へ影響があれば `spec.md` / `requirements.md` も更新。

7) **コミット（緑になってから）**
   - すべて緑になったら **コミット** → **プッシュ**。  
   - メッセージ規約（例）：
     - `feat(runtime): P0-5 SkillRuntime stages executor green`
     - 本文に **完了内容・テスト範囲・関連タスクID** を記載
   - 例：
     ```
     git add -A
     git commit -m "feat(loader): P0-1 SkillIndex/Loader green
     
     - parse name/description/inputs/outputs/stages/keywords
     - warn on unknown keys
     - collect resources/ and scripts/ paths
     - unit: 3 tests green"
     git push -u origin feat/P0-1-skillindex-loader
     ```

8) **PR / CI**
   - PR を作成し、CI 全ジョブが緑であることを確認。  
   - 原則 **Squash & merge**。マージ後、ローカルを `main` に同期。

9) **次のタスクへ**
   - `main` を最新化 → 次のタスクブランチへ：  
     ```
     git switch main
     git pull
     git switch -c feat/<next-task>
     ```
   - `tasks.md` の **次タスクに着手マーク**（[ ] WIP）を付けて開始。

**補足**  
- 途中でブロッカーが出たら、現タスクに「保留理由」を追記し `[ ] WIP → [ ] BLOCKED` と記す。ブロッカー解消タスクを先に立てる。  
- E2E を壊す変更は **同一PR内で直す**（赤のままのマージ禁止）。  
- タグ付け：主要マイルストーン完了時に `v0.1.0-m1` などを付与しておくとデモ復元が容易。

