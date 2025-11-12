# SkillRuntime（Pure Act）仕様

本書は `SkillRuntime`（1つのSkillを自律的にやり切る Act 部）の仕様を、`spec.md` から独立した文書としてまとめたものです。外側の Workflow（Plan/Reflect）は別章に委ね、本書は **Pure 型 Act のみ**を対象とします。

---

## 1. 目的
- `skillId` で特定される Skill を、**LLM 主導のツール選択**で実行し、`expectedOutputs` を満たす成果物を生成・検証・保存する。
- **順序はコードで配線しない**（= Pure）。我々はツール群・ガード・検証ルールのみ提供する。

---

## 2. 適用範囲と前提
- **skillId** はパス型任意（例：`brand-guidelines`、`document-skills/pptx`）。固定ディレクトリに依存しない。
- Skill は最低限 **`SKILL.md` を含む**。追加の Markdown／テンプレート／コード等は任意。
- クライアント（Workflow）から `goal/constraints/inputs/budgets/L1(meta)` が渡される。

---

## 3. Progressive Disclosure（L1/L2/L3）
- **L1（常時）**：`name` / `description`（`SKILL.md` の frontmatter 相当）のみ常駐。
- **L2（トリガ時）**：Skill が関連と判断されたら **`SKILL.md` 全文**を一度読み込み、コンテキストへ投入（目安 < 5k tokens）。
- **L3（必要時）**：`SKILL.md` から参照された追加ファイル（他MD/テンプレ/コード等）を**オンデマンド**で読み/実行。  
  - 生コードは原則プロンプトに入れず、**出力要約＋パス（fileId/相対パス）＋stdout(JSON)** を注入。

---

## 4. 自律ループ（最小骨格）
```

Acquire → Decide → Apply → Record → Progress/Exit

````
- **Acquire**：予算残・AgenticScope 上の最新成果（`act.output.*`）・開示レベル（L1/L2/L3）を同期。
- **Decide**：次に取るべき最小アクションを LLM が選択（参照読取／テンプレ展開／スクリプト実行／検証／書き出し 等）。
- **Apply**：選ばれたツールを実行。
- **Record**：成果物と根拠（Evidence）を記録。
- **Progress/Exit**：新規/改善アーティファクトがあれば継続。`expectedOutputs` 検証合格で終了。

**停滞対処**：連続無進捗×2 で **Micro-Reflect**（Act 内の軽量再計画）を 1 回だけ許可。

---

## 5. 予算と停止条件（Budgets/Exit）
- 既定：`max_tool_calls=24` / `time_budget_ms=120000` / `token_budget=60000` /  
  `script_timeout_s=20` / `disk_write_limit_mb=50`。
- **Exit 条件**：  
  - `expectedOutputs` が検証 OK（Completeness/Compliance/Quality）  
  - 予算超過／循環参照／サンドボックス規約違反 → **早期終了**

---

## 6. Artifacts & Evidence（AgenticScope + build/out）
- **Artifacts**：`build/` 配下（相対）へ生成、命名例 `act.<artifact>@vN`（`act.pptx.slide_outline@1` など）。
- **Evidence（毎手）**：`reason_short(≤5行)` / `inputs_digest` / `cost(tokens,time)` / `diff_summary` / `file_ids`。
- **Metrics**：`tokens/time/tool_calls/disclosure` を収集し、AgenticScope の `act.output.*` 並びに `SharedBlackboardIndexState` に書き戻す。

---

## 7. エージェント構成とツール
Pure Act Runtime は **Supervisor Agent + Sub Agent** 構成とし、Supervisor が判断したアクションを Sub Agent へ委譲する。Sub Agent は単機能で、LangChain4j の `@Agent` インターフェイスとして実装する。

### 7.1 SupervisorAgent
- `SkillActSupervisor`（本体）。`SkillRuntime` から呼び出され、Progressive Disclosure と自律ループを遂行。
- Sub Agent を `@Tool` と同様に呼び出せるよう LangChain4j の Pure Agent 機構で登録。

### 7.2 Sub Agents（Pure）
1. **ReadSkillMdAgent**：SKILL.md の L2 取得を担当。
2. **ReadRefAgent**：L3 参照解決（Markdown/テンプレ/コード等）を担当。
3. **RunScriptAgent**：スクリプト自動化（依存解決・サンドボックス順守）。
4. **WriteArtifactAgent**：生成成果物の `build/out` への永続化。

各 Sub Agent は必要最小のツールのみ保持し、Supervisor は Sub Agent に依頼するかどうかだけを決める。Sub Agent 同士は直接やり取りせず、成果は Supervisor が AgenticScope へ記録する。

### 7.3 利用可能ツール
Sub Agent が利用するツールは以下に限定し、**構造化 I/O**（成功/失敗を型で表現）とする。

- `fsRead(path: string) -> FileContent`：ファイル読み込み。ReadSkillMdAgent / ReadRefAgent から使用。
- `fsList(glob?: string) -> FileList`：必要な場合のファイル列挙（参照解決補助）。
- `fsWrite(relPathUnderBuild: string, bytes|text) -> ArtifactHandle`：WriteArtifactAgent 専用。
- `scriptRun(path: string, argsJson: object, dependencies: object, timeoutSeconds: int) -> ScriptResult`：RunScriptAgent 専用（サンドボックス仕様は §8 を継承）。
- `validate(schemaRef|rules, object|filePath) -> ValidationResult`
- `summarize(text|filePath) -> Summary`
- `diff(aPath, bPath) -> DiffSummary`
- `scopeState.write(outputKey, value)` / `scopeState.read(key)`：AgenticScope 経由で共有状態にアクセス
- （任意）`templateExpand(templatePath: string, json: object) -> GeneratedText|File`

Supervisor 自身はファイル・スクリプトへ直接アクセスせず、Sub Agent との対話で必要な情報と成果物を取得する。

---

## 8. スクリプト実行（Sandbox 規約）
- **制約**：許可拡張子（例 `.sh` `.py` `.js`）のみ実行。作業ディレクトリは **skill ルート** 固定。書込は **`build/` のみ**。仮想環境での `pip`/`npm` インストールを許容する。
- **環境管理**：`gradlew setupSkillRuntimes`（仮称）などの初期化タスクで `env/python`・`env/node` を作成し、`.gitignore` 済み。`runScript` は実行前に対象環境を起動し、要求された依存（skills に記載された `requirements.txt` や `package.json`、追加オプション等）をインストールしてからスクリプトを実行する。依存解決に失敗した場合はエラーを返し、Agent が再試行可否を判断する。
- **I/O 契約**：`stdin=args.json`、`stdout=JSON`（または明確にパース可能）、`stderr` は最大512行を Evidence に要約保存。`stdout` には依存インストール結果の要約も含める。
- **再試行**：失敗時は 1 回のみ（パラメータ変更 or 代替スクリプト）。再試行時も同じ仮想環境を再利用する。

---

## 9. 依存解決と品質ゲート
- **依存解決**：`runScript` は Agent から渡された `dependencies` パラメータ（例：`{"pip": [...], "npm": [...]}`）に従い、必要なパッケージを仮想環境へインストールする。失敗時は詳細ログを返し、Agent が継続/中断を判断する。
- **キャッシュ**：同一依存は仮想環境でキャッシュされるため再インストールを避けられる。バージョン固定は Skill 側の `requirements.txt` / `package-lock.json` 等で担保する。

## 10. 検証と品質ゲート
- **Completeness**：`expectedOutputs` の全要素が存在。
- **Compliance**：フォーマット／命名／配置／ブランド等の制約を満たす。
- **Quality**：構文 OK、相互参照整合、空欄なし、既知の指摘解消。  
→ `validate(...)` ツールを **必ず通過** させ、OK なら終了。

---

## 11. キャッシュと冪等
- **読取キャッシュ**：`contentHash(path)` により L2/L3 の重複注入を抑止。
- **AttemptMemo**：`(action + inputs_hash)` で同一手の再実行を抑止（失敗理由も保持）。

---

## 12. 失敗ハンドリング
- **不足参照**（存在しない MD/スクリプト）：即検出 → 代替手（生成のみで補完）を 1 回試行 → 不可なら上位へ差し戻し（不足一覧・提案付与）。
- **スキーマ NG／タイムアウト／循環参照**：終了理由と推奨次手（必要な参照、パラメータ）を返却。

---

## 13. Workflow との境界（I/O 契約）
- **入力（Workflow → SkillRuntime）**  
  `goal, constraints, inputs, budgets, L1(meta.name, meta.description), skillRoot`
- **出力（SkillRuntime → Workflow）**  
  `ActResult { artifacts[], validation, evidenceSummary, metrics, remainingBudgets, unmet[] }`
- **差し戻し条件**：予算超過／不足参照／品質未達 → `unmet[]` と提案を含め返す。

---

## 14. LangChain4j 実装ガイド（Pure）
- **形**：**SupervisorAgent + SubAgent 群**。Supervisor はツリー状に Sub Agent を束ね、LangChain4j の Pure Agent サンプル（[docs.langchain4j.dev/tutorials/agents#pure-agentic-ai](https://docs.langchain4j.dev/tutorials/agents#pure-agentic-ai)）を踏襲する。
- **Supervisor 定義（例）**
  ```java
  @Agent
  interface SkillActSupervisor {
      @SystemMessage("""
      You are the Act runtime for a single skill. Decide which specialised agent to invoke next.
      L1: keep only name/description by default. Call ReadSkillMdAgent when deeper context is needed (L2).
      For L3 references or automation, delegate to the appropriate sub agent.
      Finish after validateExpectedOutputs succeeds.
      """)
      ExecutionDirective run(@UserMessage ActInput input);
  }
  ```
- **Sub Agent 登録**
  ```java
  var readSkillAgent = AiServices.builder(ReadSkillMdAgent.class)
      .chatModel(model)
      .tools(new FsReadTool(...), scopeStateTool)
      .build();

  var readRefAgent = AiServices.builder(ReadRefAgent.class)
      .chatModel(model)
      .tools(new FsReadTool(...), scopeStateTool)
      .build();

  var runScriptAgent = AiServices.builder(RunScriptAgent.class)
      .chatModel(model)
      .tools(new ScriptTool(...), scopeStateTool)
      .build();

  var writeArtifactAgent = AiServices.builder(WriteArtifactAgent.class)
      .chatModel(model)
      .tools(new FsWriteTool(...), scopeStateTool)
      .build();

  var supervisor = AiServices.builder(SkillActSupervisor.class)
      .chatModel(model)
      .tools(readSkillAgent, readRefAgent, runScriptAgent, writeArtifactAgent, validatorTool, summarizeTool)
      .toolExecutionListener(new BudgetGuard(maxCalls, timeMs))
      .chatMemory(ChatMemoryProvider.inMemory())
      .build();
  ```
- **Supervisor 呼び出し**：`SkillRuntime` は従来通り Supervisor を一回起動し、予算管理・Progressive Disclosure は Supervisor 側で実施。
- **停止**：`validateExpectedOutputs` 成功、または予算超過/規約違反時に Supervisor から終了 Directive を返す。

---

## 15. 「SKILL.md だけ」のスキル

* L3 は未使用（=0）。候補アクションは **読解／生成／検証／書出し** に限定。
* `SKILL.md` に参照が書かれているが実体が無い場合はエラー検出 → 代替生成 1 回 → 不可なら差し戻し。

---

## 16. 既定パラメータ

* `max_tool_calls=24`, `time_budget_ms=120000`, `token_budget=60000`,
  `script_timeout_s=20`, `disk_write_limit_mb=50`, `temperature=0`, `seed固定`.

---

## 17. テスト指針

* **ユニット**：Disclosure 縮退／スコアリング（必要なら）／Schema 検証／ScriptRunner 制限。
* **コンポーネント**：`SKILL.md → Validate → WriteArtifact` の一連。
* **E2E**：2 回目実行で tokens/tool_calls の減少（キャッシュ有効）。
* **フェイル**：不足参照／スクリプト失敗／連続無進捗／予算超過。
