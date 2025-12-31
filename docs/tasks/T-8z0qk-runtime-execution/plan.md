# T-8z0qk ランタイム統合プラン

## Metadata

- Type: Plan
- Status: Draft

## Checklist

- [x] Phase 1: コード実行環境インターフェース実装
  - [x] `CodeExecutionEnvironment` インターフェース定義
  - [x] `ExecutionResult` レコード実装
  - [x] Docker実装 (`DockerCodeExecutionEnvironment`) をNon-AI Agentとして実装
  - [x] T-p1x0zで生成したイメージを使用したコンテナ起動・実行・クリーンアップ
  - [x] 依存未充足時のガード処理追加
- [x] Phase 2: Docker 実行環境の実装
  - [x] Docker実装 (`DockerCodeExecutionEnvironment`) を実装
  - [x] T-p1x0zで生成したイメージを使用したコンテナ起動・実行・クリーンアップ
  - [x] 依存未充足時のガード処理追加
  - [x] 外部ネットワーク遮断・インストール禁止の検証テスト
- [ ] Phase 3: ACADS実装（Phase 2）
  - [ ] ACADS実装 (`AcadsCodeExecutionEnvironment`) をNon-AI Agentとして実装
  - [ ] Azure Entra認証とトークン管理
  - [ ] セッションID管理（テナント分離）
  - [ ] ファイルパス変換（Docker `/workspace` ⇔ ACADS `/mnt/data`）
- [ ] Phase 4: 実行結果/成果物の取り扱い
  - [ ] 各ステップの cmd/exit/stdout/stderr/elapsed を構造化して返却
  - [ ] 成果物の一覧取得/ダウンロード API を提供
  - [x] 成果物の保存先ディレクトリを指定できるオプションを追加
  - [x] setup に build-skill-images サブコマンドを追加

## Risks / Mitigations

- **ACADS カスタムイメージ対応**: ACADSは標準Python環境のみ提供。カスタムイメージは[Custom Container Sessions](https://learn.microsoft.com/en/azure/container-apps/sessions-custom-container)で対応可能だがPhase 3検討事項。Phase 1ではDocker実装で進める。
- **実行時間**: ステップごとのタイムアウト設定とテストでの時間上限確認。
- **コンテナ起動オーバーヘッド**: Phase 1は「1実行=1コンテナ」で進め、将来的にセッションプール化を検討（Open Questions参照）。

## Notes

- **依存管理**: 実行時の `pip/npm install` は禁止。依存不足は事前検証で検知する。
- **参考実装**: `dev.langchain4j.code.azure.acads.SessionsREPLTool`のファイル操作・認証パターンを活用。
