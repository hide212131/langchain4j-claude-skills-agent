# T-8z0qk ランタイム統合プラン

## Metadata

- Type: Plan
- Status: Draft

## Checklist

- [ ] Phase 1: 依存解決入力の受け渡し
  - [ ] T-p1x0z で作成したプロファイル/イメージタグを読み取る設定を実装
  - [ ] 依存未充足時に実行を開始しないガードを追加
- [ ] Phase 2: サンドボックス実行フロー
  - [ ] Docker/ACADS で共通の `/workspace` モデルを用いて pptx ワークフローを実行
  - [ ] 外部ネットワーク遮断・インストール禁止を検証するテストを追加
- [ ] Phase 3: トレース/成果物
  - [ ] 各ステップの cmd/exit/stdout/stderr/elapsed を FR-hjz63 準拠で記録
  - [ ] pptx/サムネイル/生成 HTML/JS/ログを成果物カタログに登録

## Risks / Mitigations

- ACADS へのイメージ配布が未確定 → まず Docker 実行で統合、ACADS 用クライアントはモック/インターフェースで抽象化。
- 実行時間が長くなるリスク → ステップごとのタイムアウトを設定し、テストで時間上限を確認。

## Notes

- 実行時の `pip/npm install` は禁止。依存不足は事前検証で検知する。
