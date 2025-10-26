# 仕様書（spec.md）— langchain4j-claude-skills-agent

## 0. 目的とスコープ
- 任意の skill ルート（例：`skills/` 取得物や `brand-guidelines` など）に配置された **Claude Skills 互換の SKILL.md** 群を読み取り、**Plan → Act → Reflect** 骨格で自動選択・段階実行する “Skills-lite ランタイム” を提供する。  
  - LangChain4j を本番経路のオーケストレーションに採用し、Plan / Act / Reflect は Workflow ノードとして実装する。補助的なユーティリティのみ独自コードで補完する。
- **Progressive Disclosure 準拠**：メタだけ常駐 → 本文は必要時に投入（既定は全文、超過時は要約） → スクリプトは実行結果のみを注入（コンテキスト最適化）。
  - 参考: 概要 https://docs.claude.com/en/docs/agents-and-tools/agent-skills/overview  
          設計思想（Engineering Blog） https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills

非スコープ：
- Prebuilt Skills（pptx/xlsx 等）の完全互換
- 外部ネットワーク I/O、依存のオンライン解決
- 任意スクリプトの**無制限**実行（本仕様はサンドボックス必須）
- 大容量の長期ストレージ（ローカル一時のみ）

---

**注記**：単一 Skill を自律的に実行する Act（Pure）の詳細仕様は別紙 **spec_skillruntime.md** を参照。

## 1. リポジトリ前提と配置
- `skills/` は **anthropics/skills** のエクスポート置場（未追跡・`.gitignore` 登録）：https://github.com/anthropics/skills  
- Gradle の `updateSkills` タスクで固定コミット（既定：`c74d647e56e6daa12029b6a...b`）を展開し、`-PskillsCommit` で切り替えられるようにして再現性を確保。CI でも同タスクを実行する想定。  
- 初期デモは `brand-guidelines/` と `document-skills/pptx/` を含むが、**実装は汎用**（特定スキルはハードコードしない）。
- API 観点の前提（Files/コード実行/ベータ注意）：https://docs.claude.com/en/api/skills-guide

---

## 2. 全体アーキテクチャ
- **Concept**：Workflow（Plan/Reflect）＋ Skills-lite Invoker（Act）  
- **コンポーネント**
```

runtime/
├─ workflow/
│  ├─ PlannerAgent
│  ├─ InvokerAgent
│  └─ EvaluatorAgent
├─ invoker/
│  ├─ SkillSelector
│  ├─ SkillInputBinder
│  ├─ ArtifactWriter
│  └─ GuardRails
├─ provider/
│  ├─ OpenAIAdapter
│  └─ ClaudeAdapter
├─ infra/
│  ├─ config/
│  └─ fs/
├─ evaluation/
└─ shared/
├─ Blackboard
└─ SkillIndex

```
- **補助モジュール（例）**
```

├─ ProviderAdapter (OpenAI 既定 / Claude 代替)
├─ SkillRuntime（別紙: spec_skillruntime.md）
├─ ContextPacking (抜粋/要約/差分/キャッシュ)
├─ SkillIndex (SKILL.md 索引)
└─ Blackboard (中間成果)

```
- **固定骨格**：Plan → Act → Reflect（必要時 Loop）→ Done  
- **自律ウィンドウ**：Act 内で、許可枠の中で **どのスキルを何回・どの順に呼ぶか** を自律決定  
- **安全策**：回数/トークン/時間/Allowlist/スキーマ検証でガード

### 2.1 実装モジュール構成（LangChain4j Agentic 適用方針）
- `app.cli`：CLI エントリーポイント。ユーザ入力を受け取り、`runtime.AgentService` に委譲。  
- `runtime.workflow`：LangChain4j の Workflow/Agent API を直接扱う層。`PlannerAgent` / `InvokerAgent` / `EvaluatorAgent` を合成し、`AgenticServices.sequenceBuilder()` で Plan→Act→Reflect の固定骨格を生成する。  
  - `runtime.workflow.plan`：`PlannerAgent`（Skill 候補列の生成、期待出力の定義、評価観点の提示）  
  - `runtime.workflow.act`：`InvokerAgent`（SkillRuntime 呼び出し、Budgets の配布）  
  - `runtime.workflow.reflect`：`EvaluatorAgent`（Completeness/Compliance/Quality の判定、再試行戦略の決定）
- `provider`：LLM アダプタ（OpenAI/Claude）。温度/seed/上限の統一設定を適用する。  
- `shared`：Blackboard/SkillIndex/ContextCache 等の横断 DTO・リポジトリ。  
- `infra.config`：`RuntimeConfig`, `BudgetConfig`, `SkillRuntimeConfig` 等。CLI 起動時に読み込み、WorkflowFactory/ProviderAdapter へ注入。

---

## 3. データモデル（概念）
### 3.1 SKILL.md（サブセット）
- 必須フロントマター：`name, description`
- 任意フロントマター：`version, inputs(id/type/required), outputs, keywords, stages(id/purpose/resources)`
- 追加ファイル（例：`resources/`, `templates/`）やコード（例：`scripts/`）は**任意**
- 未対応項目は**無視＋警告**（前方互換）
- 参考: Claude Skills公式ドキュメント https://support.claude.com/en/articles/12512198-how-to-create-custom-skills

### 3.2 SkillIndex（抽出メタ）
- `skillId`（相対パス）、`name/description`（必須）、`version`（任意）、`inputs/outputs`（任意）、`keywords`（任意）、`stages` 要約（任意）、主要 `resources`（scripts 含む、任意）
- **System 提示用の要約（L1）**：`name / description` のみに圧縮

### 3.3 Blackboard（中間成果）
- `artifactHandle`：`build/` 直下に生成されたファイルのハンドル（`path`, `hash`, `meta`）  
- `evidenceLog`：各手の根拠（`reason_short`, `inputs_digest`, `cost`, `diff_summary`）  
- `metrics`：tokens/time/tool_calls/disclosure など

### 3.4 AgenticScope（実行時スナップショット）
- **Plan フェーズ**
  - `plan.goal`：ユーザ要求（自然文＋構造化ゴール）  
  - `plan.candidates`：Skill 候補列（`skillId`, `expectedOutputs`, `evaluationFocus`）  
  - `plan.constraints`：`Map<String, Object>`（`tokenBudget`, `timeBudgetMs`, `maxToolCalls` など）。  
  - `plan.evaluationCriteria`：Reflect 用メトリクス（`requiredArtifacts`, `qualityChecks`）。  
- **Act フェーズ**（InvokerAgent / 各スキル呼び出し）
  - `act.windowState`：残り予算（call/token/time）。  
  - `act.currentStep`：実行中ステップの `skillId` と `stageIntent`。  
  - `act.inputBundle`：スキルに渡した入力（SkillInputBinder が整形）。  
  - `act.output.<skillId>`：スキル実行結果（原文＋要約＋Blackboard ハンドル）。  
  - `shared.blackboardIndex`：Blackboard 登録済みキー一覧（`artifactHandle` と同期）。  
- **Reflect フェーズ**（EvaluatorAgent）
  - `reflect.review`：各成果物の検証メモ（`artifactKey`, `status`, `issues`).  
  - `reflect.retryAdvice`：再試行が必要な場合の補足指示。  
  - `reflect.finalSummary`：最終レポート（成功時は Deliverable 要約、失敗時は終了理由）。  
- **補助ステート**
  - `shared.contextSnapshot`：Progressive Disclosure の投入ログ。  
  - `shared.guardState`：Guard 判定の結果（違反種別、残リトライ数）。  

---

## 4. Workflow（Plan/Act/Reflect）の流れ（要点）
- **Plan**：関連 Skill を選定し、期待出力（`expectedOutputs`）・評価観点・実行順の仮説を立てる。  
- **Act**：各 Skill を **SkillRuntime（別紙）** で実行。予算（time/token/tool_calls）を渡し、自律ループで達成させる。  
- **Reflect**：成果物を Completeness/Compliance/Quality で評価。不足があれば再度 Act へ戻すか、代替 Skill を選定。  
- **終了**：全ての必須成果物が揃えば完了、未達なら終了理由と次善策を返す。

---

## 5. コンテキスト最適化（Progressive Disclosure）
> 「常時：メタ（L1）／必要時：本文（L2）／リソース・スクリプトは結果のみ（L3）」を厳格適用

### 5.1 Disclosure レベル
- **L1（常時）**：System には **`name/description` のみ**  
- **L2（必要時）**：関連と判定された Skill の **SKILL.md 本文を全文読込**  
  - ソフト上限：1 Skill あたり **≤ 5k tokens 相当**（超過時は章・見出し単位で要約/抜粋）  
  - （超過時の）抜粋単位：章・見出し・コードブロック・表  
- **L3（追加ファイル/コード）**：追加ファイル（例：`resources/*`, `templates/*`, `*.md`）は必要断片のみ、コード（例：`scripts/*`）は**実行して出力メタのみ**をプロンプトへ

### 5.2 差分投入と縮退
- 既投入文脈は **要約へ縮退**、以降は **差分のみ**追加  
- **ContextCache（context_id）**：抽出結果・トークン統計（before/after/ヒット率）を保持  
- 閾値（`max_context_tokens`）超過時は **重要度順で剪定**

---

## 6. Skills-lite 実行エンジン
### 6.1 Skill 選定（SkillSelector）
- 入力：`plan.candidates` と `SkillIndex`  
- 出力：実行順序（スコア：Relevance/Readiness/Impact-Cost-Risk）  
- メモ：Plan が複数候補を提示し、Selector が逐次決定する

### 6.2 入出力束ね（SkillInputBinder / ArtifactWriter）
- `SkillInputBinder`：`plan.inputs` と Blackboard を突き合わせ、スキルごとの入力を整形  
- `ArtifactWriter`：`build/<skillId>/...` への安全な書き出し。重複は `contentHash` で回避

### 6.3 スクリプト実行（scripts/）
- **目的**：`document-skills/pptx/scripts` 等を **安全・再現性高く**実行  
- **方式**：  
  - 既定＝**ローカルプロセス**（`python3` / `node` / `bash` Allowlist、作業ディレクトリ＝該当 skill ルート）  
  - 任意＝**コンテナ**（`--network=none`、skillRoot/build のみマウント）  
- **ガード**：I/O は **skillRoot** と `build/` のみ、`timeoutMs`、`maxStdoutBytes`、`maxFilesCreated`、`maxProcessCount=1`  
- **入出力**：入力 `args.json`、出力は `build/out/*` と **stdout JSON**（`status/artifacts/warnings`）  
- **コンテキスト注入**：**stdout JSON と生成物メタのみ**（コード本文・冗長ログは注入しない：L3）

### 6.4 GuardRails
- 予算：`maxToolCalls` / `timeBudgetMs` / `tokenBudget` / `diskWriteLimitMb` / `scriptTimeoutSec`  
- 例外：超過・違反で即終了（`reason`, `advice` を含む）

---

## 7. 評価（Reflect）と品質ゲート
- **Completeness**：`expectedOutputs` の全要素が存在  
- **Compliance**：配置・命名・ブランド指定・スキーマ合致  
- **Quality**：構文 OK、相互参照整合、空欄なし、差し戻し事項の解消  
- 評価ログは `reflect.review` として保存し、失敗時は `retryAdvice` を提示

---

## 8. ロギングと可観測性
- **Attempt ログ**：`action`, `reason_short`, `inputs_digest`, `output_meta`, `cost`, `diff_summary`, `file_ids`  
- **Disclosure 指標**：レベル別トークン（L1/L2/L3）、**before→after**、抜粋バイト、cache ヒット率、SKILL.md 抜粋サイズと上限適用回数  
- **メトリクス**：tokens/time/tool_calls/disclosure、各フェーズの所要時間、再試行回数

---

## 9. 例外と失敗ハンドリング
- **不足参照**（存在しない MD/スクリプト）：即検出 → 代替生成 1 回 → 不可なら差し戻し（不足一覧・推奨次手）  
- **スキーマ NG / タイムアウト / 循環参照**：終了理由と推奨次手（再入力・必要参照）を返却  
- **セキュリティ違反**：プロセス中断・生成物のロールバック・監査ログ保存

---

## 10. コンフィグ
- `RuntimeConfig`：モデル設定、温度=0/seed 固定（再現性既定）、`maxContextTokens`  
- `BudgetConfig`：`maxToolCalls`, `timeBudgetMs`, `tokenBudget`, `diskWriteLimitMb`, `scriptTimeoutSec`  
- `SkillRuntimeConfig`：Sandbox 設定（Allowlist 拡張子、作業ディレクトリ、コンテナ使用可否）

---

## 11. LangChain4j 適用（実装指針）
- **Builder**：  
  - `AgenticServices.sequenceBuilder()` で Plan→Act→Reflect を合成  
  - `toolExecutionListener` で予算監視（超過で Abort）  
  - `chatMemory` は Act セッション単位（Workflow フェーズごとにスコープ分離）
- **エージェント**：  
  - `PlannerAgent`：関連 Skill 列と期待出力  
  - `InvokerAgent`：SkillRuntime（別紙）を呼ぶ  
  - `EvaluatorAgent`：検証・再試行方針の決定  
- **I/O**：すべて構造化（record/class）。ファイル生成はハンドルで返却

---

## 12. テスト
- **ユニット**：SkillSelector スコア、ContextPacking、GuardRails、スキーマ検証  
- **コンポーネント**：Plan→Act→Reflect の契約（AgenticScope の受け渡し）  
- **E2E**：`brand-guidelines → pptx` 連携、2 回目実行で tokens/tool_calls 減少（キャッシュ検証）  
- **フェイル系**：不足参照/スクリプト失敗/連続無進捗/予算超過

---

## 13. 運用モード
- **Auto**：自動完走（既定）  
- **Guided**：Plan 提示に 1 回だけ承認を求める（デモ/監査）  
- **Locked**：Act の Autonomy Window を極小化（再現性優先）

---

## 14. 参考（配置例：任意）
```

repo/
├─ skills/                      # anthropics/skills のエクスポート（未追跡）
│  ├─ brand-guidelines/
│  │  ├─ SKILL.md
│  │  └─ resources/ (palette.yaml, typography.md, scripts/…)
│  └─ document-skills/
│     └─ pptx/
│        ├─ SKILL.md
│        └─ resources/ (templates/, scripts/, examples/)
├─ .context/                 # 抜粋キャッシュ・索引
└─ out/                      # 成果物（deck.pptx など）
├─ src/main/java/...         # アプリ本体
├─ build.gradle.kts          # skills 取得タスク等（定義は別紙）
└─ README.md

```

---

## 15. 将来拡張
- Plan の並列化・枝刈り（複数計画からの動的選好）  
- 入出力スキーマ宣言の強化と **自動マッピング精度** の向上  
- Prebuilt Skills 互換層の拡張（必要範囲のみ段階的に）  
- 評価関数の学習的改善（成功ログからスコアリングをチューニング）
