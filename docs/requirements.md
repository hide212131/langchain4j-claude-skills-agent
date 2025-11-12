# 要件定義（langchain4j-claude-skills-agent / requirements）

## 目的
- `skills/` 取得物（例：`brand-guidelines` / `document-skills/pptx` など **任意のパス型 skillId**）に含まれる **Claude Skills 互換の `SKILL.md`** 群を、**LangChain4j の Workflow/Agent 機能 + GPT API** で読み取り、**自動選択・段階実行**する “Skills-lite ランタイム” を実装する。
- すべての機能を再現しなくてよい。**効率的なコンテキスト（Context Packing / Progressive Disclosure）** を体現する最小限の機能に絞る。

## 背景と方針
- Skills は **定義（`SKILL.md`＋任意の追加リソース）** が本体。実行エンジンは本来 Claude だが、本プロジェクトでは **OpenAI（GPT）経路**で動く **独自ランタイム** を提供する（Claude 直実行は将来互換の“代替経路”として保持）。
- **人間はスキルを選ばない**。LLM が `skills/` から **自動で選択・連携** する（Skills は「必要なときにだけ読み込む」設計が公式でも推奨）。
- **Hybrid**：外側は **Workflow（Plan）**、内側の各 Skill 実行（Act）は **Pure**。  
  - **Workflow**：要求を複数 Skill に分解・順序化し、評価（Reflect）で再試行を管理。  
  - **Pure（Act）**：**単一エージェント＋ツール群**で順序を配線せず、LLM が毎ターンのツール選択/停止判断を行う（詳細は `spec_skillruntime.md`）。

## 用語と前提（今回の明確化）
- **skillId**：`brand-guidelines`、`document-skills/pptx` など **任意の相対パス**。固定ディレクトリ名に依存しない。
- **ディレクトリ名の非依存**：`resources/` や `scripts/` などの**名称や存在は必須ではない**。  
  - `SKILL.md` 内の**相対リンク/明示パス/glob** に従って参照解決する。特定名をハードコードしない。
- **L1/L2/L3（Progressive Disclosure）**  
  - **L1（常時）**：`name` / `description`（frontmatter 相当）のみをシステム側に常駐。  
  - **L2（トリガ時）**：関連確定時に **`SKILL.md` 本文を全文**読み込み投入（目安 ≤5k tokens）。  
  - **L3（必要時）**：追加 MD/テンプレ/コードは **オンデマンド**で読み/実行。**コード本文は原則プロンプトに注入せず、実行結果の要約＋ハンドル（パス）＋stdout(JSON) のみ**を投入。
- **SKILL.md のみの Skill** にも対応（L3 を使わず達成可能）。

## デモ用ユースケース（2スキルの自動連携）
- **A: brand-guidelines**（ブランド規定の取り込み・要約・キャッシュ）
- **B: document-skills/pptx**（アウトライン＋ブランド規定を反映したスライド自動生成）
- 流れ：ユーザーは「ゴール（例：ブランド準拠の5枚スライド作成）」だけを指示 → Agent が **A→B** を自動選択して実行。

## アーキテクチャ概要
- **外側（Workflow）**：Plan → Act 呼出 → Reflect（必要時 Loop）→ Done  
  - Planner：候補 Skill と期待出力・評価観点を提示  
  - Evaluator：成果物を Completeness/Compliance/Quality で検証、再試行を制御
- **内側（SkillRuntime / Pure Act）**：`invokeSkill(skillId, inputs)` 相当の実行を、**単一エージェント＋ツール群**で実装  
  - ループ最小骨格：**Acquire → Decide → Apply → Record → Progress/Exit**  
  - 予算：`max_tool_calls` / `time_budget_ms` / `token_budget` / `disk_write_limit_mb` / `script_timeout_s`  
  - 退出：`expectedOutputs` 検証OK、または予算超過/規約違反/循環参照等で終了  
  - 詳細は **`spec_skillruntime.md`** に委譲（唯一のソース・オブ・トゥルース）

## 参照解決（今回の変更点）
- 固定名（例：`resources/`, `scripts/`）への依存を廃止。  
- **解決順**：`SKILL.md`（基準パス） → 相対リンク → 明示パス → glob。  
- 存在しない参照は即検出し、**代替生成 1 回**を試行。不可なら Workflow 側へ差し戻し（不足一覧と推奨次手を返す）。

## スクリプト実行（サンドボックス）
- **ネットワーク無効／実行時依存追加不可／許可拡張子のみ**（例：`.sh` `.py` `.js`）。  
- 作業ディレクトリは **skill ルート固定**、書込は **`build/` 配下のみ**。  
- **I/O 契約**：`stdin = args.json`、`stdout = JSON` を期待し、`stderr` は最大512行要約を Evidence 保存。  
- 失敗は **1 回再試行**（パラメータ変更 or 代替スクリプト）。

## 非機能要件 / セキュリティ・制約
- デフォルトで **ローカル・サンドボックス**（外部ネット無効、書き込みは `build/` 以下）  
- **再現性重視**：`temperature=0`、`seed` 固定（必要時のみ緩める）  
- 構造化ログ：tokens/time/tool_calls/disclosure、L1/L2/L3 投入ログ、Attempt ログ（`reason_short / inputs_digest / output_meta / diff / file_ids`）

## 受け入れ基準（抜粋）
- **skillId が任意パス**でも Plan→Act が E2E 完走する  
- **固定ディレクトリ名に依存しない参照解決**が機能する（相対/明示/glob）  
- **SKILL.md のみの Skill** でも完了（L3 未使用）  
- L1/L2/L3 の投入方針に従っている（L2=SKILL.md 全文、L3=結果のみ）  
- 2 回目実行でトークン/ツール呼出が減少（キャッシュ/差分投入が有効）

## 依存・セットアップ（抜粋）
- LangChain4j **Agentic** チュートリアル（Plan→Act の公式実装パターン）  
  https://docs.langchain4j.dev/tutorials/agents
- LangChain4j **エージェント構築例**（Workflow/Agent API の具体例）  
  https://github.com/langchain4j/langchain4j-examples/tree/main/agentic-tutorial
- Claude Skills 参考  
  - Overview: https://docs.claude.com/en/docs/agents-and-tools/agent-skills/overview  
  - Quickstart: https://docs.claude.com/en/docs/agents-and-tools/agent-skills/quickstart  
  - Skills API Guide: https://docs.claude.com/en/api/skills-guide  
  - GitHub (samples): https://github.com/anthropics/skills

---
