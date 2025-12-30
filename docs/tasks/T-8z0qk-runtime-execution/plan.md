# T-8z0qk ランタイム統合プラン

## Metadata

- Type: Plan
- Status: Draft

## Checklist

- [ ] Phase 1: コード実行環境インターフェース実装
  - [ ] `CodeExecutionEnvironment` インターフェース定義
  - [ ] `ExecutionResult` レコード実装
  - [ ] Docker実装 (`DockerCodeExecutionEnvironment`) をNon-AI Agentとして実装
  - [ ] T-p1x0zで生成したイメージを使用したコンテナ起動・実行・クリーンアップ
  - [ ] 依存未充足時のガード処理追加
- [ ] Phase 2: LangChain4j Agentic統合
  - [ ] `@Agent` アノテーションによるワークフロー統合
  - [ ] AIエージェント（スキルプランナー）とNon-AIエージェント（実行環境）のシーケンス構築
  - [ ] 環境切り替え設定（Docker/ACADS）の実装
  - [ ] 外部ネットワーク遮断・インストール禁止の検証テスト
- [ ] Phase 3: ACADS実装（Phase 2）
  - [ ] ACADS実装 (`AcadsCodeExecutionEnvironment`) をNon-AI Agentとして実装
  - [ ] Azure Entra認証とトークン管理
  - [ ] セッションID管理（テナント分離）
  - [ ] ファイルパス変換（Docker `/workspace` ⇔ ACADS `/mnt/data`）
- [ ] Phase 4: トレース/成果物
  - [ ] 各ステップの cmd/exit/stdout/stderr/elapsed を FR-hjz63 準拠で記録
  - [ ] `VisibilityEventPublisher` 経由での可視化基盤連携
  - [ ] pptx/サムネイル/生成ファイルを成果物カタログに登録

## Risks / Mitigations

- **ACADS カスタムイメージ対応**: ACADSは標準Python環境のみ提供。カスタムイメージは[Custom Container Sessions](https://learn.microsoft.com/en/azure/container-apps/sessions-custom-container)で対応可能だがPhase 3検討事項。Phase 1ではDocker実装で進める。
- **実行時間**: ステップごとのタイムアウト設定とテストでの時間上限確認。
- **コンテナ起動オーバーヘッド**: Phase 1は「1実行=1コンテナ」で進め、将来的にセッションプール化を検討（Open Questions参照）。

## Notes

- **依存管理**: 実行時の `pip/npm install` は禁止。依存不足は事前検証で検知する。
- **Non-AI Agent**: `CodeExecutionEnvironment`の各実装は[LangChain4j Non-AI Agents](https://docs.langchain4j.dev/tutorials/agents#non-ai-agents)パターンで実装し、AIエージェントとシームレスに統合。
- **参考実装**: `dev.langchain4j.code.azure.acads.SessionsREPLTool`のファイル操作・認証パターンを活用。
