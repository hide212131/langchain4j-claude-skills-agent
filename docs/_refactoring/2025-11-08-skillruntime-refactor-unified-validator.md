# SkillRuntime リファクタリング指示書（Unified Validator 版）
**方針：`OutputsValidatorAgent` に「契約検証（Non-AI）」と「意味検証（LLM）」を内包し、Supervisor からは 1 つの宣言型 SubAgent として扱う**  
**併せて `outputKey` へ統一、Supervisor 応答戦略は `SUMMARY` / `SCORED` を利用**

---

## 情報の所在
- 対象：`app/src/main/java/.../SkillRuntime.java` と検証関連クラス
- スタイル参照：`docs/_refactoring/2025-11-06-agentservice-refactor-declarative-workflow-observability.md`
- 目的：SubAgent の個数と配線を簡素化しつつ、決定論（機械検証）と裁量（言語検証）を両立

---

## 0. ゴール（Done の定義）
- `OutputsValidatorAgent` を **単一の宣言型 SubAgent** として導入/更新。内部で段階的検証を実施。
  1) **Contract Check（Non-AI）**：存在/拡張子/スキーマ/サイズ/出力パス境界などの機械検証  
  2) **Semantic Check（LLM）**：`expectedOutputs` に対する充足度・体裁・ガイドライン準拠の言語検証
- 返却は **単一 JSON**（統一スキーマ）：
  ```json
  {
    "pass": true,
    "stage": "semantic",
    "missing": [],
    "violations": [],
    "rationale": "...",
    "metrics": { "files": 3, "bytes": 12403 }
  }
  ```

* すべての `@Agent` は **`outputKey` を使用**（`outputName` は廃止）
* Supervisor の `responseStrategy` 既定を **`SUMMARY`**。必要に応じて **`SCORED`** に切替可能
* 観測：Contract/Semantic の各フェーズ結果・失敗理由・トークン/レイテンシ・`validationReport` を構造化出力

---

## 1. 公開インターフェイス（宣言型 SubAgent）

```java
public interface OutputsValidatorAgent {

  @dev.langchain4j.service.SystemMessage("""
    You are a strict QA reviewer for software deliverables.
    First, a deterministic contract check will run internally (no LLM).
    Only if it passes, perform semantic QA.
    Return a single JSON:
      {
        "pass": boolean,
        "stage": "contract" | "semantic",
        "missing": string[],
        "violations": string[],
        "rationale": string,
        "metrics": { "files": int, "bytes": long }
      }
  """)
  @dev.langchain4j.service.UserMessage("""
    Expected outputs (contract + semantic expectations):
    {{expectedOutputs}}

    Produced artifacts index (name/path/size/kind/preview):
    {{artifactsIndex}}
  """)
  @dev.langchain4j.agentic.Agent(
    outputKey = "validationReport",
    description = "Unified validator that performs contract (deterministic) and semantic (LLM) checks."
  )
  String validate(
      @dev.langchain4j.service.V("expectedOutputs") String expectedOutputs,
      @dev.langchain4j.service.V("artifactsIndex") String artifactsIndex
  );
}
```

> `artifactsIndex` はテキスト先頭抜粋やメタデータのみ（本文やバイナリ本体は渡さない）。

---

## 2. 内部実装（段階的検証の統合）

### 2.1 Contract Check（Non-AI）

* 入力：`expectedOutputs`（構造的要件を抽出可能なら抽出）、`artifactsIndex`、ファイルシステム参照
* 検証例：

  * 必須ファイル/拡張子の存在
  * JSON/YAML パース、Schema 検証、キー欠落
  * サイズ/件数/ハッシュ（再現性）
  * 出力パス境界（サンドボックス外禁止）
* 失敗時：`stage="contract"`, `pass=false`, `violations` を埋め、**即時 JSON 返却**（LLM 未実行）

### 2.2 Semantic Check（LLM）

* 前提：Contract Check 通過
* 観点例：要件網羅性・誤り・説明性・体裁、ブランド/記法ガイドライン準拠、複数案比較
* 出力：`stage="semantic"`、`pass` 判定と簡潔な `rationale`

---

## 3. Supervisor 組み込み（抜粋）

```java
var outputsValidator = dev.langchain4j.agentic.AgenticServices
    .agentBuilder(OutputsValidatorAgent.class)
    .build();

var supervisor = dev.langchain4j.agentic.AgenticServices
    .supervisorBuilder(SkillActSupervisor.class)
    .chatModel(planningModel)
    .supervisorContext(createSupervisorContext(...)) // L1/L2/L3方針＋実行順ガイド
    .responseStrategy(useScoredResponse
        ? dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy.SCORED
        : dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy.SUMMARY)
    .subAgents(
        new ReadSkillMdAgent(toolbox),
        new ReadReferenceAgent(toolbox),
        new RunScriptAgent(toolbox),
        new WriteArtifactAgent(toolbox),
        outputsValidator   // ★ 統合済み：契約＋意味検証
    )
    .build();
```

Supervisor コンテキスト追記（例）：

```
- After producing artifacts, ALWAYS call OutputsValidatorAgent.
- If validationReport.stage == "contract" && pass == false:
    stop and summarize violations (do not run scripts again).
- If validationReport.stage == "semantic":
    summarize key findings and final pass/fail.
```

---

## 4. 設定・挙動

```yaml
skillruntime:
  qa:
    mode: final           # final | step | off
  supervisor:
    response: SUMMARY     # SUMMARY | SCORED
  limits:
    max_tool_calls: 20
    time_budget_ms: 120000
    disk_write_limit_mb: 5
```

* 既定は **`qa.mode=final`**（最終成果物のタイミングで 1 回のみ検証）
* **`qa.mode=step`**：主要ステップの都度実施（コスト/非決定性が増えるため必要時のみ）

---

## 5. 観測・ログ

* Contract（Non-AI）：結果、失敗項目、ファイル数/総バイト、書込境界違反の有無
* Semantic（LLM）：`rationale` 要約、トークン/レイテンシ
* `validationReport` を構造化ログ化し、ダッシュボードで pass 率/カテゴリ別違反件数を可視化
* Supervisor 応答戦略（`SUMMARY`/`SCORED`）の採択を記録

---

## 6. テスト計画

* 単体：

  * Contract ユーティリティ：存在/拡張子/スキーマ/サイズ/境界の表裏テスト
  * `OutputsValidatorAgent.validate(...)`：Contract 失敗時は **LLM 未実行**で JSON を返すこと
* 結合/E2E：

  * 正常系：Contract→Semantic→`pass=true`
  * 失敗系：Contract で弾き `stage="contract"` の JSON 返却
  * 応答戦略：`SUMMARY` と `SCORED` の最終レス差分を検証
* 回帰：

  * 既存成果物のパス構造・ログ粒度は維持

---

## 7. 実装メモ

* キー定数：`Keys.VALIDATION_REPORT = "validationReport"`
* 安全設計：ネットワーク無効、依存追加は既定禁止、書込先はサンドボックス配下のみ
* 入力制御：`artifactsIndex` の件数/長さ上限（バイナリは種類+サイズ+ハッシュ）
* 将来拡張：Contract 規則を外部 YAML 化し、CI で Contract のみ実行可能に

---

## 付録A：`artifactsIndex` 生成指針（疑似コード）

```java
List<Artifact> artifacts = context.listArtifacts();
String index = artifacts.stream()
  .map(a -> format("""
      - name: %s
        path: %s
        size: %d
        kind: %s
        preview: %s
      """,
      a.name(), a.path(), a.size(), a.kind(),
      a.isText() ? truncate(readFirstN(a.path(), 400)) : "(binary)"
  ))
  .collect(joining("\n"));
```

---

## 付録B：Contract 規則の例（YAML, 任意）

```yaml
required:
  - path: "build/out/result.json"
    kind: json
    schema: "schemas/result.schema.json"
limits:
  max_total_bytes: 5242880
  max_files: 50
allowed_extensions:
  - ".md"
  - ".json"
  - ".txt"
sandbox:
  output_root: "build/out"
  deny_outside_output_root: true
hash:
  enabled: true
  algorithm: "sha256"
```

---

## 実装状況

この仕様に基づき、以下の実装が完了しています：

### 完了項目
- ✅ `UnifiedOutputsValidatorAgent` クラスの作成
- ✅ Contract Check（Non-AI）の実装
  - ファイル存在チェック
  - サンドボックス境界検証
  - アーティファクトメトリクス収集（ファイル数、総バイト数）
- ✅ Semantic Check（LLM）のフレームワーク実装
- ✅ `ValidationReport` および `ValidationMetrics` レコードの追加
- ✅ Supervisor の `responseStrategy` を `SUMMARY` に変更
- ✅ Supervisor コンテキストの更新（validateOutputs への言及）
- ✅ 観測ログの追加（Contract/Semantic フェーズ）
- ✅ アーティファクトインデックス生成ヘルパーメソッド

### 注意事項
- 現在の langchain4j バージョン（1.7.1）では `@Agent` アノテーションに `outputKey` 属性がサポートされていないため、`name` と `description` のみを使用しています
- Semantic Check は現在フレームワークのみ実装されており、デフォルトで無効化されています（`enableSemanticCheck=false`）
- 既存の `Validation` 型との後方互換性を維持しています

### 今後の拡張
- LLM による Semantic Check の完全実装
- Contract 規則の外部 YAML 設定ファイル対応
- より詳細なスキーマ検証（JSON/YAML）
- ハッシュベースの再現性検証
