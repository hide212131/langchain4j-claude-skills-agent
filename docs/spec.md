# 仕様書（spec.md）— langchain4j-claude-skills-agent

## 0. 目的とスコープ
- `skills/` 配下に配置した **Claude Skills 互換の SKILL.md** 群を読み取り、**LangChain4j の Workflow / Agent API** を土台にした **Plan → Act → Reflect** 骨格で自動選択・段階実行する “Skills-lite ランタイム” を提供する。  
  - LangChain4j を本番経路のオーケストレーションに採用し、Plan / Act / Reflect は Workflow ノードとして実装する。補助的なユーティリティのみ独自コードで補完する。
- **Progressive Disclosure 準拠**：メタだけ常駐 → 本文は必要時に最小抜粋 → スクリプトは実行結果のみを注入（コンテキスト最適化）。
  - 参考: 概要 https://docs.claude.com/en/docs/agents-and-tools/agent-skills/overview  
          設計思想（Engineering Blog） https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills

非スコープ：
- Prebuilt Skills（pptx/xlsx 等）の完全互換
- 外部ネットワーク I/O、依存のオンライン解決
- 任意スクリプトの**無制限**実行（本仕様はサンドボックス必須）
- 大容量の長期ストレージ（ローカル一時のみ）

---

## 1. リポジトリ前提と配置
- `skills/` は **anthropics/skills** のエクスポート置場（未追跡・`.gitignore` 登録）：https://github.com/anthropics/skills  
- Gradle で **取得・更新タスク**（sparse checkout / 固定コミット指定）を提供し、CI でも毎回取得して再現性を確保。  
- 初期デモは `brand-guidelines/` と `document-skills/pptx/` を含むが、**実装は汎用**（特定スキルはハードコードしない）。
- API 観点の前提（Files/コード実行/ベータ注意）：https://docs.claude.com/en/api/skills-guide

---

## 2. 全体アーキテクチャ
```

CliApp → AgentService (Workflow Runner)
├─ Planner (Plan)
├─ Invoker (Act with autonomy window)
├─ Evaluator (Reflect / Loop)
├─ ProviderAdapter (OpenAI 既定 / Claude 代替)
├─ SkillRuntime (stages 実行)
├─ ContextPacking (抜粋/要約/差分/キャッシュ)
├─ SkillIndex (SKILL.md 索引)
└─ Blackboard (中間成果)

```
- **固定骨格**：Plan → Act → Reflect（必要時 Loop）→ Done  
- **自律ウィンドウ**：Act 内で、許可枠の中で **どのスキルを何回・どの順に呼ぶか** を自律決定  
- **安全策**：回数/トークン/時間/Allowlist/スキーマ検証でガード

### 2.1 実装モジュール構成（LangChain4j Agentic 適用方針）
- `app.cli`：CLI エントリーポイント。ユーザ入力を受け取り、`runtime.AgentService` に委譲。  
- `runtime.workflow`：LangChain4j の Workflow/Agent API を直接扱う層。`PlanWorkflow`, `ActWorkflow`, `ReflectWorkflow` を `AgenticServices.sequenceBuilder()` で合成し、Plan→Act→Reflect の固定骨格を生成する。  
  - `runtime.workflow.plan`：`PlannerAgent`（Supervisor/Sequence のどちらを採用するかを含む）、`PlanContextBuilder`、`PlanResultMapper`。  
  - `runtime.workflow.act`：`InvokerAgent`（Supervisor + 自律ウィンドウ設定）、`SkillInvocationGuard`、`AgenticScopeSynchronizer`。  
  - `runtime.workflow.reflect`：`EvaluatorAgent`、`ValidationRules`, `RetryPolicyEvaluator`。  
  - `runtime.workflow.support`：`WorkflowFactory`（チャットモデル/ツールの差し替え）、`AgenticScopePrinter` のラッパ。  
- `runtime.provider`：`ProviderAdapter` と各実装（`OpenAiProvider`, `ClaudeProvider`）。`AgenticServices.agentBuilder(...).chatModel(provider.chatModel())` で利用。  
- `runtime.skill`：`SkillIndex`, `SkillRepository`, `SkillContextLoader`, `SkillInputBinder`。Plan/Act/Reflect から共通利用。  
- `runtime.blackboard`：`BlackboardStore`, `ArtifactHandle`, `AgenticScopeBridge`。AgenticScope の `readState`/`writeState` と命名規約（`<stage>.<artifact>` 形式）をここで統制。  
- `runtime.context`：`ContextPackingService`, `ProgressiveDisclosurePolicy`。Plan/Act ノードからの投入を共通化。  
- `runtime.guard`：トークン/時間/回数の制限、Allowlist/Denylist 判定、スキーマ検証を担う。Act/Evaluator から呼び出し。  
- `runtime.human`：Human-in-the-loop 連携（`HumanReviewAgentFactory`）。`AgenticServices.humanInTheLoopBuilder()` を用い、Act/Reflect の再試行経路に挿入。  
- `infra.logging`：`WorkflowLogger`, `DisclosureMetricsTracker`。LangChain4j サンプルの `CustomLogging` 由来の PRETTY ログ整形＋構造化ログ出力。  
- `infra.config`：`RuntimeConfig`, `BudgetConfig`, `SkillRuntimeConfig` 等。CLI 起動時に読み込み、WorkflowFactory/ProviderAdapter へ注入。

---

## 3. データモデル（概念）
### 3.1 SKILL.md（サブセット）
- 必須フロントマター：`name, description, version, inputs(id/type/required), outputs, keywords, stages(id/purpose/resources)`  
- `resources/`：テンプレート、例、スキーマ、**scripts/**（任意）
- 未対応項目は**無視＋警告**（前方互換）

### 3.2 SkillIndex（抽出メタ）
- `skillId`（相対パス）、`name/description/version`、`inputs/outputs` 型、`keywords`、`stages` 要約、主要 `resources`（scripts 含む）
- **System 提示用の要約（L1）**：`name / description / 発火条件（短文）` に圧縮

### 3.3 Blackboard（中間成果）
- 生成物レジストリ：`key -> { kind(file/json/text), path, summary, lineage }`  
- 例：`brand_profile@1`, `deck@1` など

### 3.4 AgenticScope ステート設計
- **命名規約**：`<phase>.<artifact>`（phase = `plan`, `act`, `reflect`, `shared`）。再試行サイクルは `.<iteration>` を付与（例：`act.output@2`）。  
- **Plan フェーズ**（PlannerAgent → Act への引き渡し）
  - `plan.goal`：正規化済みゴールテキスト。  
  - `plan.inputs`：ユーザ提供素材のサマリと参照パス。  
  - `plan.candidateSteps`：`List<PlanStep>`（`skillId` / `intent` / `expectedInputKeys` / `expectedOutputKeys` / `evalFocus`）。  
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
  - `shared.metrics`：ログ収集用のトークン/時間計測キャッシュ。  
- AgenticScope への read/write は `AgenticScopeBridge` 経由で集約し、Plan/Act/Reflect ノードは domain オブジェクトに変換して扱う。

---

## 4. 固定ワークフロー
1) **Plan**  
   - 入力：`goal`、ユーザ入力（例：`docs/agenda.md`）、`SkillIndex`、制約（例：`max_slides`）  
   - 出力：**候補スキル列（推奨順）**、各ステップの目的・入出力写像、**評価基準**  
   - 選定基準：goal/keywords 類似度、入力/出力整合、履歴（成功ログ）、制約充足
2) **Act（自律ウィンドウ）**  
   - 公開ツールは **単一**：`invokeSkill(skillId, inputs)`  
   - ガード：`max_tool_calls`、`token_budget`、`time_budget_ms`、`skill_allowlist/denylist`、`require_progress`  
   - 各呼び出し後に成果物を Blackboard へ登録。**次の入力合成はエージェントが決定**。  
3) **Reflect**  
   - Evaluator が成果物を検証（スキーマ整合、制約満足、品質スコア）  
   - 未達は **一度だけ再試行**（抜粋強化・代替スキル案）  
4) **Done**  
   - 成果物と構造化ログを返却

---

## 5. コンテキスト最適化（Progressive Disclosure）
> 「常時：メタ（L1）／必要時：本文抜粋（L2）／リソース・スクリプトは結果のみ（L3）」を厳格適用

### 5.1 Disclosure レベル
- **L1（常時）**：System には **SkillIndex の要約**のみ（`name/description/発火条件` 短文）  
- **L2（必要時）**：関連と判定された Skill の **SKILL.md 本文を抜粋注入**  
  - ソフト上限：1 Skill あたり **≤ 5k tokens 相当**（超過時は更に要約）  
  - 抜粋単位：章・見出し・コードブロック・表  
- **L3（リソース/スクリプト）**：`resources/*` は必要断片のみ、`scripts/*` は**実行して出力メタのみ**をプロンプトへ

### 5.2 差分投入と縮退
- 既投入文脈は **要約へ縮退**、以降は **差分のみ**追加  
- **ContextCache（context_id）**：抽出結果・トークン統計（before/after/ヒット率）を保持  
- 閾値（`max_context_tokens`）超過時は **重要度順で剪定**

---

## 6. Skills-lite 実行エンジン
### 6.1 Loader / Indexer
- `skills/` を再帰走査して SKILL.md を読み、サブセット抽出 → `SkillIndex` 構築

### 6.2 Stages Executor
- `stages[*].purpose` を提示し、必要 `resources` と `inputs` を束ねてプロンプト化  
- 生成・検証・テンプレ展開・ファイル出力を行い、Blackboard に登録

### 6.3 スクリプト実行（scripts/）
- **目的**：`document-skills/pptx/scripts` 等を **安全・再現性高く**実行  
- **方式**：  
  - 既定＝**ローカルプロセス**（`python3` / `node` / `bash` Allowlist、作業ディレクトリ＝該当 skill ルート）  
  - 任意＝**コンテナ**（`--network=none`、skills/build のみマウント）  
- **ガード**：I/O は `skills/` と `build/` のみ、`timeoutMs`、`maxStdoutBytes`、`maxFilesCreated`、`maxProcessCount=1`  
- **入出力**：入力 `args.json`、出力は `build/out/*` と **stdout JSON**（`status/artifacts/warnings`）  
- **コンテキスト注入**：**stdout JSON と生成物メタのみ**（コード本文・冗長ログは注入しない：L3）

---

## 7. プロンプト戦略
- **System**：役割・安全制約・固定ワークフロー・ガードレール・**SkillIndex 要約のみ（L1）**  
- **Developer**：評価基準、I/O 制約、抜粋/要約ルール、Disclosure 運用指針  
- **User**：ゴール文＋入力（例：`docs/agenda.md`）  
- **Plan**：候補スキル列（理由付き）／入出力写像／成功条件  
- **Act**：各呼び出しの目的・必要抜粋・入力候補を明示  
- **Reflect**：達成度の差分指摘と再試行案（1 回）  
- （API 実装例・前提の参照：Quickstart https://docs.claude.com/en/docs/agents-and-tools/agent-skills/quickstart ／ Skills Guide https://docs.claude.com/en/api/skills-guide）

---

## 8. Provider / LangChain4j 連携
- **ProviderAdapter**：OpenAI（既定）/Claude（代替）の会話・ツール呼び出し差分を吸収。  
- **LangChain4j 利用範囲**：Workflow / Agent API を活用し、Planner・Invoker・Evaluator を Workflow ノードとして構成する。`LangChain4jLlmClient` はこれらのノードから利用する既定のチャットモデル実装。  
  - エージェント構成は LangChain4j の Agentic チュートリアル/サンプルを基準とし、必要な拡張（Blackboard・ContextCache 等）はノードの内部またはカスタムフックで実装する。  
  - チュートリアル：https://docs.langchain4j.dev/tutorials/agents  
  - Claude 連携の例：https://github.com/langchain4j/langchain4j-examples/tree/main/anthropic-examples
- **Agentic API コーディングスタイル**（`langchain4j/docs/tutorials/agents.md`, `langchain4j-examples/agentic-tutorial` を参照）  
  - Agent インタフェースは `@Agent` に `name`/`description`/`outputName` を明示し、`@UserMessage` と必要なら `@SystemMessage` でプロンプトを固定。メソッド引数は `@V` でバインドし、1 エージェント＝1 目的（単一メソッド）とする。  
  - 実装は `AgenticServices.agentBuilder(...).chatModel(...).outputName(...).build()` を基本形とし、スキル側の Structured Output（record/class）を優先。共通設定（モデル、ツール、非同期可否）はビルダーに対する関数で合成可能にする。  
  - Workflow は `sequenceBuilder`（直列）、`parallelBuilder`（並列＋`executor` 管理）、`conditionalBuilder`（ガード判定）、`loopBuilder`（再帰処理）を使い分け、スーパーエージェントには `supervisorBuilder` を用いて Plan/Invoker の自律判断を行う。Composite Agent の `outputName` は AgenticScope のステート名と一致させ、Blackboard 連携用に命名規約（例：`stageName.artifactKind`）を設ける。  
  - AgenticScope の read/write を通じてステートを共有し、Plan/Reflect ノードは Scope から構造化データを取得する。`AgenticServices.agentAction(...)` や専用クラス経由で非 LLM 処理（集計・検証）を挿入し、`humanInTheLoopBuilder` で Human-in-the-loop ステップを統合する。  
  - ログの可視化はサンプルの `AgenticScopePrinter`/`CustomLogging` 相当の仕組みで踏襲し、Scope の変化とサブエージェント呼び出し履歴（名前・説明）を PRETTY ログ形式で残す。Reflect 段階での検証・再試行ポリシーは Supervisor/Loop パターンと一貫させる。

---

## 9. ログと可観測性（Disclosure 指標を含む）
- `workflow_id`, `context_id`, **確定プラン**（候補→確定の遷移）  
- **自律ウィンドウ実績**：呼び出し回数／消費トークン／経過時間／打切り理由  
- **Disclosure 指標**：レベル別トークン（L1/L2/L3）、**before→after**、抜粋バイト、cache ヒット率、SKILL.md 抜粋サイズと上限適用回数  
- 各ステップ：`skillId`／入力要約／生成成果／`tokens_in/out`  
- Evaluator：合否/スコア/NG 理由、再試行有無  
- 全体：`time_total_ms`

---

## 10. 失敗時の扱い
- **入力不足**：Plan に差し戻し、明確な追加入力質問を 1 回  
- **スキル選択ミス**：Reflect で代替候補を提示し再計画→再試行（1 回）  
- **出力検証 NG**：抜粋強化・テンプレ修正提案を伴う再試行（1 回）  
- **Disclosure 違反**：過投入検知時は自動縮退して再試行  
- 収束不可：**終了理由と次善策**（手動手順・参考情報）を返す

---

## 11. セキュリティと制約
- 書き込みは `build/` 配下のみ。外部ネット無効。任意スクリプトは**サンドボックス**必須  
- Allowlist/Denylist によるツール・スキル制御。危険命令は拒否  
- SKILL.md 未対応項目は**無視＋警告**。機密情報はプロンプト・ログに投入しない  
- スクリプトは **監査済みのみ**実行（初回取り込み時に簡易静的スキャン）

---

## 12. テストと受け入れ
- **E2E**：ゴールのみ指定で完走し、制約を満たす成果物（例：ブランド適合の `deck.pptx`）  
- **再現性**：同条件で Plan → Act → Reflect のログ構造が一致（自律部の揺れは許容）  
- **コンテキスト最適化**：2 回目以降で **before→after tokens** が減少し、L2/L3 の縮退・差分投入がログで確認できる  
- **フェイル系**：入力不足／選択不適合／評価 NG／スクリプト失敗／Disclosure 過投入の各系で安全収束  
- **取得手順**：CI/ローカルともに skills 取得タスク実行後にビルド（`skills/` は未追跡だが再生成可）

---

## 13. 運用要件
- ランタイム：Java 21、Gradle（Kotlin DSL）  
- LangChain4j：最新安定を `docs/setup.md` に明記して固定  
- Provider：既定＝OpenAI（GPT 系, `OPENAI_API_KEY`）、代替＝Claude（`ANTHROPIC_API_KEY`）  
- 環境設定：モデル名、各種予算（token/time/calls）、skills 探索ルート、Allowlist/Denylist、Disclosure 上限  
- 監査：構造化ログの保存、成功/失敗メトリクスの可視化

---

## 14. ディレクトリ（概念）
```

repo-root/
├─ skills/                      # anthropics/skills のエクスポート（未追跡）
│  ├─ brand-guidelines/
│  │  ├─ SKILL.md
│  │  └─ resources/ (palette.yaml, typography.md, scripts/…)
│  └─ document-skills/
│     └─ pptx/
│        ├─ SKILL.md
│        └─ resources/ (templates/, scripts/, examples/)
├─ docs/ (requirements.md, spec.md, tasks.md, agenda.md …)
├─ build/
│  ├─ .context/                 # 抜粋キャッシュ・索引
│  └─ out/                      # 成果物（deck.pptx など）
├─ src/main/java/...            # アプリ本体
├─ build.gradle.kts             # skills 取得タスク等（定義は別紙）
└─ README.md

```

---

## 15. 将来拡張
- Plan の並列化・枝刈り（複数計画からの動的選好）  
- 入出力スキーマ宣言の強化と **自動マッピング精度** の向上  
- Prebuilt Skills 互換層の拡張（必要範囲のみ段階的に）  
- 評価関数の学習的改善（成功ログからスコアリングをチューニング）
