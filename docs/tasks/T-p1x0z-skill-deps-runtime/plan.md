# T-p1x0z 依存依存バンドルプラン

## Metadata

- Type: Plan
- Status: Complete

## Checklist

- [x] Phase 1: スキル収集（Java SkillDownloader）
  - [x] skill-sources.yaml の URL リストから SKILL.md をまとめて取得し、取得元コミットハッシュで pin する手順を確立
  - [x] 取得した SKILL を `build/skills/` に展開し、gitignore 管理にする
- [x] Phase 2: 依存抽出と skill-deps.yaml 自動生成
  - [x] SKILL.md 依存宣言の抽出仕様を決める（言語/パッケージ/ツールの正規化ルール、LLM 併用。外部 API 利用を許可する前提）
  - [x] 抽出結果から Dockerfile 生成の入力となる `skill-deps.yaml` を自動生成し、`build/skills/<skillPath>/.skill-runtime/` に出力
- [x] Phase 3: Dockerfile 自動生成と CI 検証
  - [x] `skill-deps.yaml`（入力）から `.skill-runtime/Dockerfile` を生成
  - [x] CI で生成された Dockerfile/イメージに対し依存存在チェックを行うジョブを作成

## Risks / Mitigations

- スキル単位の依存抽出が不正確だとビルド失敗や不足が起きる → LLM 抽出 + ルールベースの補助で検出し、CI で検証する。
- ACADS へのイメージ配布手順が未確定 → レジストリ運用は別途タスク化し、ここではカタログと検証ジョブに留める。

## Notes

- 依存未充足の場合は実行前に拒否し、実行時インストールは行わない（ADR-38940）。
- 本タスクの Java 実装は `io.github.hide212131.langchain4j.claude.skills.bundle` に配置する。
