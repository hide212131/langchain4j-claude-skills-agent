# T-8z0qk ランタイム統合デザイン

## Metadata

- Type: Design
- Status: Draft

## Links

- Associated Plan Document:
  - [T-8z0qk-runtime-execution-plan](./plan.md)
- Related Requirements:
  - [FR-cccz4 単一スキルの複雑手続き実行](../../requirements/FR-cccz4-single-skill-complex-execution.md)
- Related ADRs:
  - [ADR-ehfcj スキル実行エンジン設計](../../adr/ADR-ehfcj-skill-execution-engine.md)
  - [ADR-38940 セキュリティ・リソース管理フレームワーク](../../adr/ADR-38940-security-resource-management.md)
- Related Tasks:
  - [T-p1x0z 複雑スキル依存バンドル](../T-p1x0z-skill-deps-runtime/README.md)

## Overview

**スキル実行中に必要となるコード実行環境**の設計を定義する。すべてのスキルがコード実行を必要とするわけではない（例: LLM問い合わせのみのスキル）が、FR-cccz4のpptxワークフローのように、コード生成・配置・実行が必要な場合に使用する「コード実行サンドボックス」のインターフェースと実装方針を規定する。

依存バンドル済みイメージを前提に、SKILL.md依存解決後、サンドボックス（Docker/ACADS）で外部ネットワーク遮断・実行時インストール禁止を守りつつ、コード実行とファイルI/Oを安全に行う。

**設計スコープ**:

- スキル実行エンジン（ADR-ehfcj）が、必要に応じて呼び出す「コード実行環境」コンポーネント
- コード実行が不要なスキルは、この環境を使用せずに完結する

## Success Metrics

- [ ] 依存解決済みイメージを受け取り、実行前チェックが通った場合のみサンドボックス起動する。
- [ ] 実行時に外部ネットワークを使用せず、`pip/npm install` を行わない。
- [ ] skills/pptx の新規作成/既存編集ワークフローが E2E で動作し、FR-hjz63 の可視化トレースに全ステップが記録される。

## Design Details

### 全体フロー

- 依存解決入力: T-p1x0z で決定したプロファイル名/イメージタグ。
- 実行フロー: SKILL.md 読込 → 依存解決結果チェック → サンドボックス起動（Docker/ACADS）→ `/workspace` で pptx ワークフロー実行 → 成果物/ログをカタログ化。
- ガード: 依存未充足・イメージ不整合時は即エラー。実行中の `pip/npm install` 呼び出しは禁止。
- トレース: 各ステップの cmd/exit/stdout/stderr/elapsed と成果物を FR-hjz63 に準拠して記録。

### コード実行環境インターフェース

**位置づけ**: スキル実行エンジン（ADR-ehfcj）が、SKILL.mdのオペレーション宣言（`process-single`, `process-chain`, `browser-automation`等）に基づき、必要時のみ起動する。

実行環境（Docker/ACADS）の抽象化インターフェース:

```java
/**
 * コード実行環境の抽象インターフェース
 * Phase 1: Docker実装、Phase 2: ACADS実装で共通利用
 *
 * 使用例:
 * - SKILL.mdでprocess-chain宣言されたスキル実行時
 * - LLMが生成したコード（Python/Node.js）の実行
 * - 成果物ファイル（pptx等）の生成・取得
 *
 * 使用しない例:
 * - テキストのみ返すLLM問い合わせスキル
 * - 外部API呼び出しのみのスキル（コード実行不要）
 *
 * 設計原則:
 * - メタデータ取得はコマンド実行で行う（例: identify image.png → JSON出力）
 * - インターフェースは最小限に保ち、拡張はコマンド側で対応
 */
public interface CodeExecutionEnvironment extends AutoCloseable {
    /**
     * コマンド実行（詳細結果取得）
     * @param command 実行コマンド（例: "python scripts/thumbnail.py output.pptx"）
     * @return 実行結果（stdout/stderr/exitCode/elapsed含む構造化データ）
     */
    ExecutionResult executeCommand(String command);

    /**
     * ローカルファイルをサンドボックスへアップロード
     * @param localPath ローカルファイルパス
     * @return サンドボックス内パス（/workspace/... または /mnt/data/...）
     */
    String uploadFile(Path localPath);

    /**
     * ファイルダウンロード（成果物取得）
     * @param remotePath サンドボックス内パス
     * @return ファイル内容（バイナリ）
     */
    byte[] downloadFile(String remotePath);

    /**
     * ファイル一覧取得（パターンマッチング）
     * @param pattern glob pattern (例: "*.pptx", "thumbnails/*.png")
     * @return マッチしたファイルパスリスト
     */
    List<String> listFiles(String pattern);

    /**
     * 環境のクリーンアップ
     */
    @Override
    void close();
}

/**
 * コマンド実行結果
 * @param command 実行したコマンド
 * @param exitCode 終了コード（0=成功）
 * @param stdout 標準出力
 * @param stderr 標準エラー出力
 * @param elapsedMs 実行時間（ミリ秒）
 */
public record ExecutionResult(
    String command,
    int exitCode,
    String stdout,
    String stderr,
    long elapsedMs
) {}
```

### ACADS固有設計（Phase 2）

**API仕様**: [Azure Container Apps Dynamic Sessions REST API](https://learn.microsoft.com/en-us/rest/api/containerapps/dynamic-sessions)

**実装上の主要ポイント**:

- **認証**: Azure Entra (`DefaultAzureCredential`), スコープ `https://dynamicsessions.io/.default`
- **セッションID**: `{tenantId}/{conversationId}` または `{tenantId}/{skillExecutionId}` 形式でテナント分離
- **ファイルパス**: `/mnt/data/<filename>` フラット構造（Docker環境との変換が必要）
- **制約**: ファイルサイズ上限 128MB、外部ネットワークアクセス不可

### LangChain4j Agentic統合（Non-AI Agent）

**実装パターン**: [langchain4j-agentic Non-AI Agents](https://docs.langchain4j.dev/tutorials/agents#non-ai-agents)

`CodeExecutionEnvironment`の各実装（Docker/ACADS）をNon-AI Agentとして実装し、AIエージェント（スキルプランナー等）と組み合わせてワークフローに統合する。

**設計ポイント**:

```java
// インターフェース定義（既出のCodeExecutionEnvironmentをそのまま使用）
public interface CodeExecutionEnvironment extends AutoCloseable { ... }

// Docker実装（Non-AI Agent）
public class DockerCodeExecutionEnvironment implements CodeExecutionEnvironment {
    @Agent(description = "Executes code in Docker containers", outputKey = "executionResult")
    @Override
    public ExecutionResult executeCommand(@V("command") String command) { ... }
}

// ACADS実装（Non-AI Agent）
public class AcadsCodeExecutionEnvironment implements CodeExecutionEnvironment {
    @Agent(description = "Executes code using ACADS platform", outputKey = "executionResult")
    @Override
    public ExecutionResult executeCommand(@V("command") String command) { ... }
}
```

**ワークフロー統合例**:

```java
// 実行環境選択
CodeExecutionEnvironment executor = switch (config.backend()) {
    case DOCKER -> new DockerCodeExecutionEnvironment(dockerClient);
    case ACADS -> new AcadsCodeExecutionEnvironment(acadsClient);
};

// AIエージェントとNon-AIエージェントを組み合わせたシーケンス
UntypedAgent workflow = AgenticServices
    .sequenceBuilder()
    .subAgents(skillPlanner, executor, resultValidator)
    .build();
```

**活用する既存実装**: `dev.langchain4j.code.azure.acads.SessionsREPLTool`

- `DefaultAzureCredential`による認証フロー
- トークン管理（`AtomicReference<AccessToken>`）
- ファイル操作の内部クラス設計（`FileUploader`/`FileDownloader`/`FileLister`）
- multipart/form-dataファイルアップロード実装

### Docker固有設計（Phase 1）

**実装ポイント**:

- **Dockerイメージ起動**: T-p1x0zで生成したイメージを使用
- **ボリュームマウント**: `/workspace` ディレクトリをホストとコンテナ間で共有
- **コード実行**: `docker exec <containerId> python -c "<code>"` でコマンド実行
- **ファイルアップロード**: ボリュームマウント済みなので `Files.copy` で直接コピー
- **ファイルパス**: `/workspace/<filename>` 形式で返却
- **クリーンアップ**: コンテナ停止と削除

**使用判定**: SKILL.mdのオペレーション宣言に基づく

- `process-single`, `process-chain`, `browser-automation` → 起動
- 宣言なし → スキップ（LLM応答のみで完結）

### スキル実行フロー統合

**スキル実行エンジン（ADR-ehfcj）との連携ポイント**:

1. **オペレーション宣言に基づく判定**
   - SKILL.mdから `process-single`, `process-chain`, `browser-automation` の有無を確認
   - 宣言がない場合はコード実行環境を起動せず、LLM応答のみで完結

2. **コード実行環境の起動**
   - `CodeExecutionEnvironmentFactory` でDocker/ACADSを環境設定に応じて生成
   - try-with-resources パターンで自動クリーンアップ

3. **依存チェック**
   - 依存未充足時は即座に `SkillDependencyException` をスロー
   - 実行時の `pip/npm install` は禁止

4. **コード生成・実行**
   - スキル定義とリクエストからコードを生成
   - `executeCode()` で実行、結果（stdout/stderr/result）を取得

5. **成果物取得**
   - 生成されたファイルを `listFiles()` で検出
   - 必要に応じて `downloadFile()` で取得
   - `Artifact` オブジェクトとしてカタログ化

6. **エラーハンドリング**
   - HTTP 413: `FileSizeLimitExceededException` （128MB超過）
   - HTTP 404: `FileNotFoundException`
   - その他: `SkillExecutionException`

### トレース記録（FR-hjz63準拠）

**実装ポイント**:

1. **実行ステップのトレース**
   - イベントタイプ: `SKILL_EXECUTION_STEP`
   - メタデータ: `step`, `command`, `exit_code`, `elapsed_ms`
   - ペイロード: `stdout`, `stderr`
   - 各コード実行ごとに記録

2. **成果物のトレース**
   - イベントタイプ: `ARTIFACT_GENERATED`
   - メタデータ: `filename`, `path`, `size_bytes`
   - ファイル生成完了時に記録

3. **可視化連携**
   - `VisibilityEventPublisher` 経由で可視化基盤に送信
   - T-7k08g（Prompt Visibility）との統合

## Open Questions

- ACADSはPython環境が事前構成済み、カスタムイメージは[Custom Container Sessions](https://learn.microsoft.com/en-us/azure/container-apps/sessions-custom-container)で対応（Phase 3検討事項）
- 失敗時の中間成果物保持ポリシーをどこまで残すか。
- Phase 1（Docker）とPhase 2（ACADS）の切り替え設定方法（環境変数/設定ファイル/ビルダーパターン）。
- **コンテナライフサイクル最適化**: 現状は「スキル1実行=1コンテナ」だが、コンテナ起動オーバーヘッドを考慮すると、将来的には複数実行の共有やセッションプール化を検討する必要がある。`/workspace/{tenantId}/{sessionId}/`のようなパス階層はその際のテナント/セッション分離に活用可能。
