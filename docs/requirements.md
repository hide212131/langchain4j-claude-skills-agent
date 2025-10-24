# 要件定義（langchain4j-claude-skills-agent / requirements）

## 目的
- `skills/` フォルダに置かれた **Claude Skills 互換の SKILL.md** 群を、**LangChain4j の Agent 機能 + GPT API** で読み取り、**自動選択・段階実行**する “Skills-lite ランタイム” を実装する。
- すべての機能を再現しなくてよい。**効率的なコンテキスト（Context Packing / Progressive Disclosure）** を体現する最小限の機能に絞る。

## 背景と方針
- Skills は **定義（SKILL.md＋resources）** が本体。実行エンジンは本来 Claude だが、本プロジェクトは **LLM非依存** を志向し、**OpenAI（GPT）経路**で動く **独自ランタイム** を提供する（Claude 直実行は将来互換の“代替経路”として保持）。
- **人間はスキルを選ばない**。LLM が `skills/` から **自動で選択・連携** する（Skills は「必要なときにだけ読み込む」設計が公式でも推奨）。
- **Agentic**（Plan → Act → Reflect）でサブタスク化し、**pause/resume 相当**の再入を考慮。

## デモ用ユースケース（2スキルの自動連携）
- **A: brand-guidelines**（ブランド規定の取り込み・要約・キャッシュ）
- **B: document-skills/pptx**（アウトライン＋ブランド規定を反映したスライド自動生成）
- 流れ：ユーザーは「ゴール（例：ブランド準拠の5枚スライド作成）」だけを指示 → Agent が **A→B** を自動選択して実行。

## スコープ（MVP）
- **Skills ディスカバリ**：`skills/` を再帰スキャンし、各 `SKILL.md` の YAML フロントマター（`name, description, inputs, outputs, stages, keywords`）と主要 `resources/` をインデックス化。
- **自動選択**：Agent へ「利用可能スキルの要約リスト」を提示。**ツールは1つ**（`invokeSkill(skillId, inputs)`）。LLM が skillId と inputs を決める。
- **Skills-lite 実行**：
  - `stages` を順次実行し、必要な `resources/*` のみロード。
  - 最小ツール：`FileTool(read/write/zip)`、`TemplateTool`、`JsonTool(validate/transform)`。
  - 生成物：`build/out/*`、中間：`build/.context/*`。
- **Context Packing（Progressive Disclosure）**：
  - 長文は **参照と抜粋**（章・見出し・コードブロック単位）で投入。
  - **段階的開示**：各 stage で必要断片だけを追加し、既投入の文脈は要約へ縮退。
  - **Context Cache**：`context_id` 単位で再利用（トークン節約の可視化）。
- **CLI**（例）：
  - `skills run --goal "ブランド準拠で5枚のPPTX" --in docs/agenda.md --out build/out/deck.pptx`

## 非スコープ（MVP）
- Prebuilt Skills（pptx/xlsx 等）の完全互換機能すべて
- 外部ネットワーク I/O、依存のオンライン解決
- 大容量ファイルの長期ストレージ（ローカル一時のみ）

## 受け入れ基準
- 人がスキル名を指定しなくても、**brand-guidelines → pptx** の順で自動選択される。
- 出力 `deck.pptx` が **ブランド色・書体・トーン**を反映。
- ログに **tokens(before→after)**、**抜粋投入サイズ**、**キャッシュヒット率**、**選択理由（短文）** が出力される。
- エラー時に **不足入力の指摘 or 代替案の自動再試行**（1回）を行い、最終的に明確なメッセージで終了。

## 技術要件
- **言語/ビルド**：Java 21、**Gradle (Kotlin DSL)**
- **LangChain4j**：**1.7系**を想定（最新安定は `docs/setup.md` に明記・随時更新）
- **LLM/Provider**：
  - 既定：**OpenAI（GPT-5）** … `OPENAI_API_KEY`
  - 代替：**Claude**（将来互換のパススルー） … `ANTHROPIC_API_KEY`
- **主要モジュール**：
  - **Loader/Indexer**：`skills/` から `SkillIndex` を作成
  - **ProviderAdapter**：OpenAI/Claude のチャット・ツール呼び出しを抽象化
  - **Agent 層**：Plan→Act→Reflect、**単一ツール `invokeSkill`** を仲介
  - **Runtime（Skills-lite）**：`stages` 実行、リソース解決、ブラックボード（中間成果共有）
  - **ContextCache**：抜粋・要約・トークン統計の保存
- **モジュール構成（spec.md 2.1 準拠）**：
  - `app.cli` → CLI エントリーポイント（PicoCLI 等）。  
  - `runtime.workflow`（`plan` / `act` / `reflect` / `support`）、`runtime.provider`, `runtime.skill`, `runtime.blackboard`, `runtime.context`, `runtime.guard`, `runtime.human`。  
  - `infra.logging`, `infra.config`。  
  - 依存方向は `app` → `runtime` → `infra` で固定し、ArchUnit 等で検証する。
- **AgenticScope 契約（spec.md 3.4 準拠）**：
  - `plan.goal`, `plan.inputs`, `plan.candidateSteps`, `plan.constraints`, `plan.evaluationCriteria` を Plan ノードが必ず書き込む。  
  - Act ノードは `act.windowState`, `act.currentStep`, `act.inputBundle`, `act.output.<skillId>`, `shared.blackboardIndex` を更新する。  
  - Reflect ノードは `reflect.review`, `reflect.retryAdvice`, `reflect.finalSummary` を生成し、`shared.contextSnapshot`/`shared.guardState`/`shared.metrics` を更新する。  
  - これらは `AgenticScopeBridge` と DTO で型安全にアクセスし、未設定キーを検出した場合は例外を送出する。

## アーキテクチャ概要
- `CliApp` → `AgentService`（LangChain4j Workflow Runner）  
  → `ProviderAdapter`（OpenAI 既定）  
  → `SkillRuntime`（`invokeSkill` 実体, stages 実行, Tools 呼び出し）  
  ↔ `SkillIndex`（SKILL.md/リソースのメタ）  
  ↔ `ContextCache`（要約/抜粋/トークン計測）

## セキュリティ/制約
- デフォルトで **ローカル・サンドボックス**（外部ネット無効、書き込みは `build/` 以下）
- 実行可能なツールは **Allowlist** 管理（Skill 側の要求とアプリ側の許可が一致した場合のみ可）
- **scripts/** の実行は許可するが、**必ずサンドボックス下**（Allowlist 実行環境・I/O制限・timeout・ネット遮断）で行う  
  - 例：許可する通訳器＝`python3` / `node` / `bash`、作業ディレクトリ＝該当 skill ルート  
  - 入出力は `args.json`（入力）と `build/out/*`（出力）に限定  
  - **プロンプトへはスクリプトの「出力メタ」だけ**を注入（コード本文や冗長ログは注入しない：Progressive Disclosure）

## ログ/観測
- `tokens_in/out`、プロンプト投入バイト、キャッシュヒット率、選択スキルと理由、stage 遷移、ツール結果サマリ
- 失敗時は原因分類（入力不足/整合性エラー/実行拒否）と再試行ログ

## 開発プロセス
本プロジェクトの文書化と実装は、次の順で進める。

1) **requirements.md**（要件定義）  
2) **spec.md**（仕様書：I/F・ワークフロー・コンテキスト最適化・セキュリティ）  
3) **tasks.md**（実装タスク：優先度・見積り・完了条件）

### 実装の進め方：t_wada 風TDD（Red–Green–Refactor）
- **原則**：小さく歩く（Baby Steps）、問題を小さく分割、テストの構造化と継続的リファクタリング。  
- **戦略**：仮実装（Fake it）→ 三角測量（Triangulation）→ 明白な実装（Obvious Implementation）を状況で使い分け。  
- **各タスクのサイクル**：  
  1. **Red**（失敗するテストを書く）  
  2. **Green**（最短で通す）  
  3. **Refactor**（重複排除・命名改善・設計整理）  
  4. テストを追加し三角測量で一般化

## ディレクトリ構成
```

repo-root/
├─ skills/                             # Skills 置き場（自動スキャン対象・未追跡）
│  ├─ brand-guidelines/
│  │  ├─ SKILL.md
│  │  └─ resources/                   # ブランド規定テキスト/配色定義など
│  │     ├─ palette.yaml
│  │     └─ typography.md
│  └─ document-skills/
│     └─ pptx/
│        ├─ SKILL.md
│        └─ resources/                # スライド用テンプレ/例
│           ├─ templates/
│           │  ├─ title-slide.md
│           │  └─ content-slide.md
│           └─ examples/
│              └─ sample-outline.md
│
├─ docs/                               # ドキュメント
│  ├─ requirements.md
│  ├─ spec.md
│  └─ tasks.md
│
├─ build/                              # 実行時に生成
│  ├─ .context/                        # 抜粋キャッシュ/索引
│  └─ out/                             # 生成成果物（deck.pptx など）
│
├─ src/main/java/...                   # アプリ本体（単一Gradleの場合）
├─ build.gradle.kts
└─ README.md

````

## コマンド例
```bash
# 目標だけ伝える（自動選択・自動連携）
skills run \
  --goal "ブランドガイドに従って docs/agenda.md から5枚のスライドを作る" \
  --in docs/agenda.md \
  --out build/out/deck.pptx \
  --skills-dir skills/

# キャッシュを活かして再実行（トークン削減がログで確認できる）
skills run --goal "同上" --in docs/agenda.md --out build/out/deck2.pptx
````

## リスクと対策

* **SKILL.md の表現差/拡張**：サブセット準拠。未対応フィールドは無視し、警告ログを出す。
* **スキル選択ミス**：Agent の Reflect で 1 回だけ再選択（別候補）を試行。
* **トークン肥大**：抜粋アルゴの閾値と段階的開示を強制（超過時は要約にフォールバック）。
* **scripts の安全性**：監査済みスクリプトのみ実行。Allowlist/timeout/ネット遮断で被害半径を限定。

## 実装参照（LangChain4j）

* LangChain4j **Agentic** チュートリアル（Plan→Act→Reflect の公式実装パターン）
  [https://docs.langchain4j.dev/tutorials/agents](https://docs.langchain4j.dev/tutorials/agents)
* LangChain4j **エージェント構築例**（Workflow/Agent API の具体例）
  [https://github.com/langchain4j/langchain4j-examples/tree/main/agentic-tutorial](https://github.com/langchain4j/langchain4j-examples/tree/main/agentic-tutorial)

> LangChain4j の Workflow / Agent API を採用し、Plan・Act・Reflect の各ステップを公式の Agentic コンポーネントとして組み立てること。独自実装はテストダブルや補助ロジックに限定し、本番経路は LangChain4j のエージェント基盤上で動作させる。

## 参考（一次情報）

* **Agent Skills 概要（Overview）**：[https://docs.claude.com/en/docs/agents-and-tools/agent-skills/overview](https://docs.claude.com/en/docs/agents-and-tools/agent-skills/overview)
* **Quickstart（API からの最短導入）**：[https://docs.claude.com/en/docs/agents-and-tools/agent-skills/quickstart](https://docs.claude.com/en/docs/agents-and-tools/agent-skills/quickstart)
* **Skills API ガイド（Files/コード実行/運用の前提）**：[https://docs.claude.com/en/api/skills-guide](https://docs.claude.com/en/api/skills-guide)
* **公式 GitHub（サンプル＆フォルダ構造）**：[https://github.com/anthropics/skills](https://github.com/anthropics/skills)
* **アナウンス（機能の位置づけ）**：[https://www.anthropic.com/news/skills](https://www.anthropic.com/news/skills)
