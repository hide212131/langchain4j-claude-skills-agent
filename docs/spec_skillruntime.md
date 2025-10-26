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
- **Acquire**：予算残・Blackboard・開示レベル（L1/L2/L3）を同期。
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

## 6. Blackboard（Artifacts & Evidence）
- **Artifacts**：`build/` 配下（相対）へ生成、命名例 `act.<artifact>@vN`（`act.pptx.slide_outline@1` など）。
- **Evidence（毎手）**：`reason_short(≤5行)` / `inputs_digest` / `cost(tokens,time)` / `diff_summary` / `file_ids`。
- **Metrics**：`tokens/time/tool_calls/disclosure` を収集。

---

## 7. ツール群（LangChain4j @Tool 仕様）
> すべて **構造化 I/O**（成功/失敗を型で表現）。固定サブディレクトリ名に依存しない。

- `readSkillMd(skillRoot) -> SkillDoc`
- `readRef(path: string) -> MdDoc`（相対リンク/明示パス/glob を許容）
- `listFiles(glob?: string) -> FileList`
- `readFile(path: string) -> FileContent`
- `templateExpand(templatePath: string, json: object) -> GeneratedText|File`
- `validate(schemaRef|rules, object|filePath) -> ValidationResult`
- `runScript(path: string, argsJson: object) -> ScriptResult`（サンドボックス、下記参照）
- `writeArtifact(relPathUnderBuild: string, bytes|text) -> ArtifactHandle`
- `summarize(text|filePath) -> Summary`
- `diff(aPath, bPath) -> DiffSummary`
- `blackboard.put(key, value)` / `blackboard.get(key)`

---

## 8. スクリプト実行（Sandbox 規約）
- **制約**：ネットワーク不可／実行時依存追加不可／許可拡張子（例 `.sh` `.py` `.js`）／CPU/メモリ/時間制限。  
  作業ディレクトリは **skill ルート** 固定。書込は **`build/` のみ**。
- **I/O 契約**：`stdin=args.json`、`stdout=JSON`（または明確にパース可能）、`stderr` は最大512行を Evidence に要約保存。
- **再試行**：失敗時は 1 回のみ（パラメータ変更 or 代替スクリプト）。

---

## 9. 検証と品質ゲート
- **Completeness**：`expectedOutputs` の全要素が存在。
- **Compliance**：フォーマット／命名／配置／ブランド等の制約を満たす。
- **Quality**：構文 OK、相互参照整合、空欄なし、既知の指摘解消。  
→ `validate(...)` ツールを **必ず通過** させ、OK なら終了。

---

## 10. キャッシュと冪等
- **読取キャッシュ**：`contentHash(path)` により L2/L3 の重複注入を抑止。
- **AttemptMemo**：`(action + inputs_hash)` で同一手の再実行を抑止（失敗理由も保持）。

---

## 11. 失敗ハンドリング
- **不足参照**（存在しない MD/スクリプト）：即検出 → 代替手（生成のみで補完）を 1 回試行 → 不可なら上位へ差し戻し（不足一覧・提案付与）。
- **スキーマ NG／タイムアウト／循環参照**：終了理由と推奨次手（必要な参照、パラメータ）を返却。

---

## 12. Workflow との境界（I/O 契約）
- **入力（Workflow → SkillRuntime）**  
  `goal, constraints, inputs, budgets, L1(meta.name, meta.description), skillRoot`
- **出力（SkillRuntime → Workflow）**  
  `ActResult { artifacts[], validation, evidenceSummary, metrics, remainingBudgets, unmet[] }`
- **差し戻し条件**：予算超過／不足参照／品質未達 → `unmet[]` と提案を含め返す。

---

## 13. LangChain4j 実装ガイド（Pure）
- **形**：**単一エージェント＋ツール群**。**順序は配線しない**（LLM が毎ターン選択）。
- **Agent 定義（例）**
  ```java
  @Agent
  interface SkillActAgent {
    @SystemMessage("""
    You are the Act engine for a single Skill.
    L1: name/description only. When relevant, load full SKILL.md (L2).
    Read extra files / run scripts only on demand (L3).
    Finish when expectedOutputs are produced and validated.
    """)
    ActResult run(@UserMessage ActInput input);
  }
````

* **構築**

  ```java
  var agent = AiServices.builder(SkillActAgent.class)
      .chatLanguageModel(model /* temperature=0, seed固定 推奨 */)
      .tools(new FsTools(...), new ScriptTool(...), new ValidatorTool(...),
             new ArtifactTool(...), new BlackboardTool(...))
      .chatMemory(ChatMemoryProvider.inMemory())
      .toolExecutionListener(new BudgetGuard(maxCalls, timeMs))
      .build();
  ```
* **停止**：`expectedOutputs` 検証 OK or 予算超過。任意で `finalize(result)` ツールを用意して即終了も可。

---

## 14. 「SKILL.md だけ」のスキル

* L3 は未使用（=0）。候補アクションは **読解／生成／検証／書出し** に限定。
* `SKILL.md` に参照が書かれているが実体が無い場合はエラー検出 → 代替生成 1 回 → 不可なら差し戻し。

---

## 15. 既定パラメータ

* `max_tool_calls=24`, `time_budget_ms=120000`, `token_budget=60000`,
  `script_timeout_s=20`, `disk_write_limit_mb=50`, `temperature=0`, `seed固定`.

---

## 16. テスト指針

* **ユニット**：Disclosure 縮退／スコアリング（必要なら）／Schema 検証／ScriptRunner 制限。
* **コンポーネント**：`SKILL.md → Validate → WriteArtifact` の一連。
* **E2E**：2 回目実行で tokens/tool_calls の減少（キャッシュ有効）。
* **フェイル**：不足参照／スクリプト失敗／連続無進捗／予算超過。
