# T-p1x0z 複雑スキル依存バンドルデザイン

## Metadata

- Type: Design
- Status: Approved

## Links

- Associated Plan Document:
  - [T-p1x0z-skill-deps-runtime-plan](./plan.md)
- Related Requirements:
  - [FR-cccz4 単一スキルの複雑手続き実行](../../requirements/FR-cccz4-single-skill-complex-execution.md)
- Related ADRs:
  - [ADR-38940 セキュリティ・リソース管理フレームワーク](../../adr/ADR-38940-security-resource-management.md)
  - [ADR-ehfcj スキル実行エンジン設計](../../adr/ADR-ehfcj-skill-execution-engine.md)

## Overview

複雑スキル（例: skills/pptx）の依存をランタイムでインストールせず、SKILL.md の依存宣言に基づき事前ビルドしたベースイメージを選択できるようにする。ビルド（依存バンドル）に焦点を当て、ランタイム統合は別タスクで扱う。ベースは汎用テンプレート Dockerfile を 1 つ用意し、抽出したコマンドだけを差し込む。

## Success Metrics

- [ ] SKILL.md の依存宣言から依存セットを抽出し、`skill-deps.yaml`（Dockerfile 生成の入力）を自動生成できる。
- [ ] CI で依存検証ジョブが動作し、足りない場合はビルドで検知して失敗させられる。
- [ ] ランタイムでの `pip/npm install` なしに FR-cccz4 の acceptance である pptx ワークフローを開始できる状態（依存を含むイメージが準備済み）を整える。

## Design Details

### アーキテクチャ分割

- **ビルド（事前バンドル）**: `skill-deps.yaml` に基づきテンプレート Dockerfile を生成し、Docker（ローカル/CI）と ACADS 用イメージの双方を対象にビルド。
- **ランタイム実行**: 別タスク（ランタイム統合）で扱う。ここではイメージが選択できる状態までをゴールとする。

### コンポーネント

- Java 実装パッケージ: `io.github.hide212131.langchain4j.claude.skills.bundle`（ビルド系実装）。SKILL.md パース/LLM は既存の `...skills.runtime` を再利用。
- DependencyDeclarationParser: SKILL.md の依存宣言（言語/パッケージ/ツール）を抽出する。
- SkillDepsGenerator: 抽出した依存セットから `skill-deps.yaml`（Dockerfile 生成の入力）を自動生成する。
- DockerfileGenerator: `skill-deps.yaml` から Dockerfile を生成する。
- CI Dependency Check: 生成したイメージに対し `node/playwright/sharp`、`python -c "import markitdown"` など存在確認を行うジョブ。
- SkillDownloader (Java 実装): skill-sources.yaml に記載したリポジトリ URL から SKILL.md 群をダウンロードし、サンドボックス内 `/workspace/skills/<skillId>/` に配置する。取得元リポジトリはタグ/コミットで pin し、HTTP(S) のみを使用。

### データフロー

1. Java 実装の SkillDownloader で SKILL 一式を取得し、ホスト側 `build/skills/<skillPath>/` に配置（取得元は `run-env/skill-sources.yaml` で管理・pin）。イメージビルド時に Dockerfile で `/workspace/skills/<skillPath>/` へコピーする。
2. SKILL.md 読み込み → LLM を含むパーサで依存宣言を抽出
3. 抽出結果から `.skill-runtime/skill-deps.yaml` を自動生成
4. `template/Dockerfile` をベースに `skill-deps.yaml` の `commands` を差し込み、`Dockerfile` を自動生成
5. CI で生成イメージの依存充足を検証（不足ならビルド失敗で止める）

### LLM による依存抽出仕様

#### 対象データ

- frontmatter の `dependencies` を最優先で読み取り、未定義または空の場合に本文から抽出する。
- 本文は「依存に関係する節」のみを対象にする（例: install/setup/requirements/tools/依存/環境）。

#### 抽出プロンプト要件

- 出力は **厳密な YAML** のみ（余計な説明やマークダウンは禁止）で、構造は、[#skill-depsyaml-の例スキル単位](#skill-depsyaml-の例スキル単位)に従う
- 言語/パッケージ/ツールの観点で依存を抽出し、バージョン指定は保持する。
- 依存が推測のみで不確実な場合は `warnings` に理由を残す。
- `commands` には **実行可能なインストールコマンドのみ** を出力する。自然文で書かれた指示は LLM でコマンド化し、`warnings` に変換理由を残す。
- コマンド化の優先順は `apt-get install` / `npm install -g` / `pip install` を基本とする。

#### 失敗時の扱い

- 解析エラーはログに記録し、該当 SKILL は `warnings` を付けて継続する。
- `commands` に自然文が混入していた場合は LLM でコマンド化し、`warnings` に変換理由を残す。変換できない場合は `warnings` に理由を残し、手動修正が必要である旨を記録する。

#### セキュリティ

- LLM 送信は依存抽出に必要な最小限の断片に限定する。
- 外部 API 利用は ADR-38940 の制約に従い、送信内容は可視化ログで監査可能にする。

### ストレージ/設定配置

- `run-env/skill-sources.yaml.example` に典型的なスキル取得元 URL（タグ/コミットで pin）を記載し、環境ごとに `run-env/skill-sources.yaml` を用意して SkillDownloader（Java）でフェッチする。
- スキル定義は `build/skills/` に配置する。
- スキル単位の生成物は `build/skills/<skillPath>/.skill-runtime/` に集約する。`<skillPath>` は `build/skills` 配下の SKILL.md と同じ階層構造とする。
- `.skill-runtime/` 配下に `skill-deps.yaml`、`Dockerfile`、その他関連ファイルを出力する。
- テンプレート Dockerfile はソース管理配下の `run-env/template/Dockerfile` に置く。
- CI: `.github/workflows/` などに依存検証ジョブ（環境に合わせて調整）。
- 実行時設定: `application.yaml` にスキル→イメージタグのマッピングと環境種別（local/docker, acads）。

```
project-root
├─ app/src/main/java/.../skills/
│  ├─ runtime/                  # 既存の実装配置（SkillDocumentParser/LlmProvider など）
│  └─ bundle/                   # ビルド系実装（SkillDownloader/skill-deps 生成/Dockerfile 生成）
├─ run-env/
│  ├─ skill-sources.yaml.example # 取得元URLのサンプル（タグ/ハッシュでpin）
│  ├─ skill-sources.yaml         # 環境ごとに用意（gitignore想定）
│  ├─ template/
│  │  └─ Dockerfile              # 汎用コンテナのテンプレート
│  └─ scripts/                   # SKILL ダウンロード・検証スクリプト（CI/CDや手元実行用）
└─ build/                       # .gitignore 管理
   ├─ skills/                   # SKILL を展開するディレクトリ
   │  └─ anthropics/
   │     └─ skills/            # 例: https://github.com/anthropics/skills/tree/main/skills を取得した場合
   │        ├─ brand-guidelines/
   │        │  ├─ SKILL.md
   │        │  └─ .skill-runtime/
   │        │     ├─ skill-deps.yaml
   │        │     └─ Dockerfile
   │        └─ pptx/
   │           ├─ SKILL.md
   │           └─ .skill-runtime/
   │              ├─ skill-deps.yaml
   │              └─ Dockerfile
```

### skill-sources.yaml の例

```yaml
sources:
  - https://github.com/anthropics/skills.git#refs/tags/v1.0.0:skills:anthropics/skills
  - https://github.com/gotalab/skill-samples.git#refs/heads/main:skills:gotalab/skills
```

`<repo>#<ref>:<path>:<dest>` 形式で 1 行にまとめる簡易フォーマット。標準仕様はないため、このシンプルな文字列リストで運用する。

#### ベースイメージ方針（初期方針）

- Python/Node を含む汎用コンテナを 1 つ用意し、`template/Dockerfile` に固定する（ベースは `node:lts-bookworm-slim`）。
- 個別のスキルが複数言語の依存を要求する前提のため、ベースは分岐しない。

### skill-deps.yaml の例（スキル単位）

LLM は SKILL.md 1 件につき 1 件の `skill-deps.yaml` を生成する。
この例は 1 スキル分の出力であり、配列で複数件を返す形式にはしない。
キーは汎用的な名称に統一する。

```yaml
commands:
  - 'pip install "markitdown[pptx]"'
  - "pip install defusedxml"
  - "npm install -g pptxgenjs"
  - "npm install -g playwright"
  - "npm install -g react-icons react react-dom"
  - "npm install -g sharp"
  - "apt-get update && apt-get install -y libreoffice poppler-utils"
```

### セキュリティ・制約（ビルド時）

- 外部ネットワークは次の用途に限定して許可する。
  - `skill-sources.yaml` で pin した URL からのスキル取得
  - 依存抽出のための LLM 外部 API 呼び出し（送信内容は依存抽出に必要な最小限に限定）
- 作業ディレクトリ外への書き込み禁止（run-env と build/ 配下のみ使用）。
- 署名検証・ハッシュ固定は別タスクで拡張可能だが、本タスクでは pin + CI 検証で担保。

### 単一スキルの再生成

- `generate-skill-deps` は `--skill-path` を受け取り、指定 SKILL.md のみ `skill-deps.yaml` を再生成できるようにする。
- `--skill-path` 指定時は `--skills-root` の探索をスキップし、対象スキルのみを処理する。
- 失敗時は `warnings` を付けて継続し、既存の `skill-deps.yaml` への上書きは許容する。

### テスト戦略（ビルド成果物のみ）

- Unit: 依存宣言パースの成功/失敗、`skill-deps.yaml` 自動生成が期待通りの YAML を出すこと、Dockerfile 生成が依存を含むこと。
- Integration: 生成した Dockerfile でビルドし、必要な依存（node/playwright/sharp、python/markitdown 等）が存在するかを確認（実行フロー統合は別タスク）。
- CI: 依存検証ジョブが不足を検知して失敗すること。

## Open Questions

- スキル単位で `skill-deps.yaml` を管理する粒度が妥当か。
- ビルドしたイメージの配布方法（レジストリへの push とタグ/ハッシュ管理）の詳細。
