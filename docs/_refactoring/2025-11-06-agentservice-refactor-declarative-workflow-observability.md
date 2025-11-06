# AgentService リファクタリング指示書

**採用方針：宣言的ワークフロー＋観測の外出し（Observability as a cross-cutting concern）**

> **なぜこの方針か（丁寧な理由付け）**
>
> * **トレース過多の可読性劣化**：`AgentService.run(...)` にログ/Span/属性付与/イベントが散在すると、**本来の業務制御（Plan→Act→Reflect）と混線**し、変更箇所の特定や影響範囲の見積りが困難になります。観測は**横断関心**なので、**モデル/エージェントのリスナーに集約**することで、コードの主語（ワークフローの意図）を取り戻し、**「何をやる」対「どう観測する」**を明確に切り分けられます。
> * **`agentAction` 依存による手続き化**：大きな無名ラムダに State 読み書き/分岐/変換/ループを詰め込むと、**制御構造が埋没**し、**試行回数や分岐条件がコード構造に表現されない**ため、保守コストが高騰します。これを **`sequenceBuilder` / `loopBuilder` / `conditionalBuilder`** に移し、Plan/Act/Reflect を **Typed/Non-AI エージェント**化すると、**流れが宣言的に可視化**され、変更耐性が高まります。さらに、LLM 入出力は**構造化出力**を用い、**「型で壊れにくくする」**ことで、テスト容易性が増します。

---

## 情報の所在
https://docs.langchain4j.dev/tutorials/agents
https://docs.langchain4j.dev/tutorials/observability

---

## 0. ゴール（Doneの定義）

* `AgentService.run(...)` は**ワークフロー宣言＋入力差し込み＋実行**のみ。
* トレース/メトリクスは**モデル/エージェントの before/after リスナー**に集約。
* `agentAction(...)` は**0 〜 最小限**（一時的な小変換のみ）。
* Plan/Act/Reflect は**サブエージェント**（Typed/Non-AI）として分離。
* 主要 State は **record/POJO** で表現（キー文字列と Map 多用を排除）。
* 既存の公開インターフェース（返却型/ログ粒度）は**後方互換**。

---

## 1. 作業の大枠（段階的・誤りにくい順）

### フェーズ1：観測の外出し（安全・差分小）

- **やること**
  - [x] チャットモデルに **`.logRequests(true) / .logResponses(true)`** を設定。
  - [x] **ChatModelListener** を導入（prompt/params/latency/tokenUsage/finishReason/error を一元収集）。
  - [x] エージェント呼び出しに **`beforeAgentInvocation` / `afterAgentInvocation`** を設定（ステージ開始/終了イベントをここに集約）。
  - [x] `AgentService.run(...)` 内の `tracer.trace(...)` / `Span.current().setAttribute(...)` / `tracer.addEvent(...)` の重複を**撤去**。

- **受け入れ基準**
  - [x] 既存テストが通る。
  - [x] LLM I/O とトークン使用量が**モデル側**でログ/計測される。
  - [ ] `run(...)` の行数とネストが顕著に減る（目安 −20〜30%）。

---

### フェーズ2：Plan/Act/Reflect をサブエージェント化（`agentAction` の置換）

- **やること**
  - [x] **Non-AI エージェント**3種を新設：
    * `PlanOperator`：`planner.plan(...)` / `planWithFixedOrder(...)` を実行し `plan` を出力キーで保存。
    * `ActOperator`：`invoker.invoke(...)` を実行し成果物/実行スキルを保存。
    * `ReflectOperator`：`evaluator.evaluate(...)` を実行し `needsRetry` とサマリを保存。
  - [ ] （任意）Plan 補助の LLM 呼び出しがある場合は **Typed LLM エージェント**（例：`PlanDraftAgent`）を追加し、`assistantDraft` を `outputKey` で保存。
  - [x] 既存の **巨大 `agentAction` 3つを撤去**し、上記サブエージェントを **`sequenceBuilder`** で直列接続。

- **受け入れ基準**
  - [x] `AtomicReference` と `scope.write/readState` の**散在**が解消（必要箇所のみ残る）。
  - [x] ステージ間の契約（入出力）が**型で表現**される。

**サンプル（概念）**

```java
public interface PlannerAgent {
  @Agent(outputKey = "plan")
  PlanResult plan(@V("goal") String goal, @V("forcedSkillIds") List<String> forced);
}

public final class PlanOperator implements PlannerAgent {
  private final AgenticPlanner planner;
  @Override public PlanResult plan(String goal, List<String> forced) {
    return (forced != null && !forced.isEmpty())
        ? planner.planWithFixedOrder(goal, forced)
        : planner.plan(goal);
  }
}
```

---

### フェーズ3：手書きリトライを `loopBuilder()` へ（制御構造の宣言化）

- **やること**
  - [x] 外側の `for (attempt...)` を削除し、**`loopBuilder()`** に移行。
  - [x] Reflect ステージの `needsRetry` 判定を読む `exitCondition` を設定。
  - [x] `maxIterations` は現状の回数（例：2）を踏襲。`attempt` は scope またはループカウンタで供給。

- **受け入れ基準**
  - [x] 成功時は 1 回で終了、再試行必要時は最大回数で打ち切り。
  - [x] リトライ系テストが等価に通る。

```java
AtomicReference<AttemptSnapshot> lastAttempt = new AtomicReference<>();
AttemptAgent attemptAgent = new AttemptAgent(request, maxAttempts, stageVisits, lastAttempt);

UntypedAgent loop = AgenticServices.loopBuilder()
    .subAgents(attemptAgent)
    .maxIterations(maxAttempts)
    .exitCondition((scope, iteration) -> {
        AttemptSnapshot snapshot = lastAttempt.get();
        return snapshot == null || snapshot.evaluation() == null || !snapshot.evaluation().needsRetry();
    })
    .output(scope -> lastAttempt.get())
    .build();
```

---

### フェーズ4：強制スキル指定の分岐を `conditionalBuilder()` へ（if/else の外出し）

- **やること**
  - [x] `forcedSkillIds` の有無で **固定順プラン** / **通常プラン** を選択する分岐を **Plan ステージ外側**へ。
  - [x] `conditionalBuilder().when(..., fixedPlanner).otherwise(dynamicPlanner)` に置換。

- **受け入れ基準**
  - [x] PlanOperator 内の分岐ロジックが**簡素化**している。
  - [x] 入力差に応じて分岐ログが before/after に記録される。

---

### フェーズ5：最終出力を `output(...)` に一元化（末尾の整形撤去）

- **やること**
  - [x] `sequenceBuilder(...).output(scope -> new ExecutionResult(...))` に集約。
  - [x] `plan/act/evaluation/metrics` の組み立ては**ここだけ**で実施。末尾の重複整形を**撤去**。

- **受け入れ基準**
  - [x] 外部公開シグネチャは不変。
  - [x] `run(...)` は**配線コードのみ**に近づく。

```java
AtomicReference<AttemptSnapshot> lastAttempt = new AtomicReference<>();
UntypedAgent loop = AgenticServices.loopBuilder()
    .subAgents(attemptAgent)
    .maxIterations(maxAttempts)
    .exitCondition((scope, iteration) -> shouldExit(lastAttempt.get()))
    .output(scope -> lastAttempt.get())
    .build();

UntypedAgent workflow = AgenticServices.sequenceBuilder()
    .subAgents(loop)
    .output(scope -> assembleExecutionResult(request, stageVisits, lastAttempt))
    .build();
```

---

### フェーズ6：State の型安全化＋構造化出力（壊れにくさとテスト容易性）

- **やること**
  - [ ] `PlanConstraints` 等の `Map` を **record/POJO** に置換。
  - [ ] LLM 応答で配列/表構造を返す箇所は **構造化出力（JSON Schema / JSON モード）**で POJO に直接マッピング。
  - [ ] State アクセスは `AgentState` のような薄いラッパ導入で **キー定数の重複**を排除。

- **受け入れ基準**
  - [ ] 主要 State は**コンパイル時**に整合性チェック可能。
  - [ ] 変換目的の `agentAction` が**残っていない**。

---

### フェーズ7：残置観測の重複排除・チューニング

- **やること**
  - [ ] モデルログ（`.logRequests/.logResponses`）、`ChatModelListener`、before/after の**役割が重複**している箇所を整理。
  - [ ] トークン/レイテンシ/エラー指標は**一元出力**（ダッシュボード要件に合わせて）に集約。

- **受け入れ基準**
  - [ ] ログの冗長がなく、**粒度と責務の境界**が明確。
  - [ ] 観測の欠落がない（比較チェック）。

---

## 2. 実装のポイント（誤りやすい箇所の先回り）

* **リスナーの相関ID**：`beforeAgentInvocation` で開始時に ID を生成し、`afterAgentInvocation`/`onError` に継承。Agent 名・attempt・出力キーを属性化。
* **型付きエージェントの `outputKey`**：Plan/Act/Reflect の主要成果は **必ず名前付きキー**で保存（最終 `output(...)` で取り出しやすく）。
* **非AIエージェントの境界**：業務ロジック（planner/invoker/evaluator呼び出し）は **Non-AI エージェント**に寄せ、LLM コールは **Typed LLM エージェント**へ分離。
* **エラー処理**：ビルダーの `errorHandler(...)` で**共通方針**（再試行しない/メタ付与のみ/fail fast など）を統一。
* **並列化の余地**：将来、独立の前処理/後処理は `parallelBuilder()` or `.async(true)` + `executor()` で拡張（今回は範囲外）。
* **テスト戦略**：

  * 分岐：`forcedSkillIds` あり/なしで Plan が切替わる。
  * 反復：`needsRetry=true/false` の両ケース。
  * 例外：planner/invoker/evaluator で例外送出 → errorHandler と観測の整合。
  * 回帰：`ExecutionResult` の後方互換、メトリクスの合算/集計。

---

## 3. ロールアウト計画（小さなPRで確実に）

* **PR1（フェーズ1＋5）**：観測を外出し＋`output(...)` 集約（挙動不変・差分小）。
* **PR2（フェーズ2＋3）**：サブエージェント化＋ループ宣言化（既存テスト最小更新）。
* **PR3（フェーズ4）**：条件分岐の外出し。
* **PR4（フェーズ6＋7）**：型・構造化出力移行＋観測の重複整理。

**ロールバック指針**

* 各PRは**コンパイル単位**で完結。問題時は直前の PR をリバート。
* PR1 時点で観測を**二経路（旧/新）**で一時的に重畳し、PR4 で旧系を撤去。

---

## 4. 最終イメージ（抜粋）

```java
// Plan 分岐（固定順/通常）
UntypedAgent planBranch = AgenticServices.conditionalBuilder()
  .when(s -> !s.readState("forcedSkillIds", List.of()).isEmpty(), fixedPlannerAgent)
  .otherwise(dynamicPlannerAgent)
  .outputKey("plan")
  .build();

// 1 attempt = Plan → Act → Reflect
UntypedAgent attempt = AgenticServices.sequenceBuilder()
  .subAgents(planBranch, actOperator, reflectOperator) // 各Agentは outputKey を明示
  .build();

// リトライを宣言化
UntypedAgent loop = AgenticServices.loopBuilder()
  .subAgents(attempt)
  .maxIterations(2)
  .exitCondition(s -> !s.readState("needsRetry", false))
  .build();

// 観測はフックへ、最終組立は output(...) へ
UntypedAgent workflow = AgenticServices.sequenceBuilder()
  .subAgents(loop)
  .beforeAgentInvocation(obs::onBefore)   // ステージ開始
  .afterAgentInvocation(obs::onAfter)     // ステージ終了
  .output(s -> assembleExecutionResult(s))// 返却一元化
  .build();
```

---

### これで解決できること

* **可読性**：ビジネス意図（順次/分岐/反復）が**コード構造として現れる**。
* **保守性**：観測は 1〜2 箇所に集約、**変更影響とデバッグが容易**。
* **堅牢性**：State は型で守られ、**agentAction 依存を解消**。
* **拡張性**：並列化・Supervisor化・構造化出力の高度化に**スムーズに拡張**可能。

以上の手順に沿って、AIエージェントは段階的にリファクタリングを進めてください。
