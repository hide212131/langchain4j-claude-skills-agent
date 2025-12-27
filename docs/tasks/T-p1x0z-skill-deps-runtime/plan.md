# T-p1x0z 依存バンドル・実行分離プラン

## Metadata

- Type: Plan
- Status: Draft

## Checklist

- [ ] Phase 1: スキル収集（Java SkillDownloader）
  - [ ] skill-sources.yaml の URL リストから SKILL.md をまとめて取得し、取得元コミットハッシュで pin する手順を確立
  - [ ] 取得した SKILL を `.skills/` に展開し、gitignore 管理にする
- [ ] Phase 2: 依存抽出とプロファイル自動生成
  - [ ] SKILL.md 依存宣言の抽出仕様を決める（言語/パッケージ/ツールの正規化ルール、LLM 併用。外部 API 利用を許可する前提）
  - [ ] 抽出結果から Dockerfile 生成の入力となるプロファイル YAML を自動生成し、`build/profiles.generated.yaml` に出力
- [ ] Phase 3: Dockerfile 自動生成と CI 検証
  - [ ] プロファイル YAML（入力）から `build/docker/{profile}/Dockerfile` を生成
  - [ ] CI で生成された Dockerfile/イメージに対し依存存在チェックを行うジョブを作成

## Risks / Mitigations

- プロファイル粒度が細かすぎるとイメージ爆発 → 初期は大括りプロファイルで開始し、必要に応じて分割。
- ACADS へのイメージ配布手順が未確定 → レジストリ運用は別途タスク化し、ここではカタログと検証ジョブに留める。

## Notes

- 依存未充足の場合は実行前に拒否し、実行時インストールは行わない（ADR-38940）。
- 本タスクの Java 実装は `io.github.hide212131.langchain4j.claude.skills.bundle` に配置する。
