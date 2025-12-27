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

複雑スキル（例: skills/pptx）の依存をランタイムでインストールせず、SKILL.md の依存宣言に基づき事前ビルドしたベースイメージを選択できるようにする。ビルド（依存バンドル）に焦点を当て、ランタイム統合は別タスクで扱う。

## Success Metrics

- [ ] SKILL.md の依存宣言から依存セットを抽出し、プロファイル YAML（Dockerfile 生成の入力）を自動生成できる。
- [ ] CI で依存検証ジョブが動作し、足りない場合はビルドで検知して失敗させられる。
- [ ] ランタイムでの `pip/npm install` なしに FR-cccz4 の acceptance である pptx ワークフローを開始できる状態（依存を含むイメージが準備済み）を整える。

## Design Details

### アーキテクチャ分割

- **ビルド（事前バンドル）**: 依存プロファイル定義に基づきベースイメージをビルド。Docker（ローカル/CI）と ACADS 用イメージの双方を対象。
- **ランタイム実行**: 別タスク（ランタイム統合）で扱う。ここではイメージが選択できる状態までをゴールとする。

### コンポーネント

- Java 実装パッケージ: `io.github.hide212131.langchain4j.claude.skills.bundle`（ビルド系実装）。SKILL.md パース/LLM は既存の `...skills.runtime` を再利用。
- DependencyDeclarationParser: SKILL.md の依存宣言（言語/パッケージ/ツール）を抽出・正規化。
- ProfileGenerator: 抽出した依存セットからプロファイル YAML（Dockerfile 生成の入力）を自動生成する。
- DockerfileGenerator: プロファイル YAML から Dockerfile を生成する。
- CI Dependency Check: 生成したイメージに対し `node/playwright/sharp`、`python -c "import markitdown"` など存在確認を行うジョブ。
- SkillDownloader (Java 実装): skill-sources.yaml に記載したリポジトリ URL から SKILL.md 群をダウンロードし、サンドボックス内 `/workspace/skills/<skillId>/` に配置する。取得元リポジトリはタグ/コミットで pin し、HTTP(S) のみを使用。

### データフロー

1. Java 実装の SkillDownloader で SKILL 一式を取得し、ホスト側 `.skills/<skillId>/` に配置（取得元は skill-sources.yaml で管理・pin）。イメージビルド時に Dockerfile で `/workspace/skills/<skillId>/` へコピーする。
2. SKILL.md 読み込み → LLM を含むパーサで依存宣言を抽出
3. 抽出結果からプロファイル YAML を自動生成
4. プロファイル YAML から Dockerfile（ベースイメージ＋依存インストール）を自動生成
5. CI で生成イメージの依存充足を検証（不足ならビルド失敗で止める）

### ストレージ/設定配置

- ルート直下 `skill-sources.yaml.example` に典型的なスキル取得元 URL（タグ/コミットで pin）を記載し、環境ごとに `skill-sources.yaml` を用意して SkillDownloader（Java）でフェッチする。
- プロファイル YAML は自動生成。生成物は `build/profiles.generated.yaml` とし、CI のアーティファクトに含める。
- Dockerfile もプロファイル YAML から自動生成し、`build/docker/{profile}/Dockerfile` に出力。
- CI: `.github/workflows/` などに依存検証ジョブ（環境に合わせて調整）。
- 実行時設定: `application.yaml` にプロファイル→イメージタグのマッピングと環境種別（local/docker, acads）。

```
project-root
├─ skill-sources.yaml.example    # 取得元URLのサンプル（タグ/ハッシュでpin）
├─ skill-sources.yaml            # 環境ごとに用意（gitignore想定）
├─ app/src/main/java/.../skills/
│  ├─ runtime/                  # 既存の実装配置（SkillDocumentParser/LlmProvider など）
│  └─ bundle/                   # ビルド系実装（SkillDownloader/プロファイル生成/Dockerfile生成）
├─ scripts/                     # SKILL ダウンロード・検証スクリプト（CI/CDや手元実行用）
├─ .skills                      # SKILL を展開するディレクトリ（.gitignore 管理）
│  └─ anthropics/
│     └─ skills/               # 例: https://github.com/anthropics/skills/tree/main/skills を取得した場合
│        ├─ brand-guidelines/
│        │  └─ SKILL.md
│        └─ pptx/
│           └─ SKILL.md
└─ build/
   ├─ profiles.generated.yaml   # 依存抽出から自動生成されたプロファイル定義
   └─ docker/
      └─ {profile}/Dockerfile  # プロファイルごとの自動生成 Dockerfile
```

### skill-sources.yaml の例

```yaml
sources:
  - https://github.com/anthropics/skills.git#refs/tags/v1.0.0:skills:anthropics/skills
  - https://github.com/gotalab/skill-samples.git#refs/heads/main:skills:gotalab/skills
```

`<repo>#<ref>:<path>:<dest>` 形式で 1 行にまとめる簡易フォーマット。標準仕様はないため、このシンプルな文字列リストで運用する。

#### プロファイル命名/分割ルール（初期方針）

- 基本は「依存セットの大分類」で分ける。例: `node-playwright-sharp`（Node+Playwright+Sharp+pptxgenjs）、`python-pptx-markitdown`（Python+markitdown\[pptx]+defusedxml）、`libreoffice-poppler` 付加プロファイル。
- 追加ルール:
  - メジャーバージョン差異がある場合は新プロファイルを起こす（例: Node18/Node20、Python3.10/3.11）。
  - 追加ツールが必要な場合（libreoffice/poppler 等）は suffix を付けた派生プロファイルを作る。
  - ネットワーク許可/遮断は原則遮断。もし例外許可をする場合は別プロファイルに分ける。
- SKILL.md 依存抽出で既存プロファイルに当てはまらない組み合わせが出たら、新規プロファイルを自動生成し、バージョン/ツールの差分が最小になるよう命名する（例: `python-pptx-markitdown-lo`）。

#### ベースイメージ選択ルール（初期方針）

- 言語ランタイムごとに既定のベースを用意し、依存宣言から最小要件を満たすものを選ぶ。
  - Node: `node:<major>-bookworm`（例: `node:20-bookworm`）
  - Python: `python:<major.minor>-slim`（例: `python:3.11-slim`）
- 追加ツールが必要な場合は、ベースを派生させる（例: libreoffice/poppler を追加した派生ベース）。
- 複数言語が同時に必要な場合は、優先順位を `python > node` とし、片方を追加インストールする。
- 依存バージョンの最小要件が満たせない場合は新しいベースイメージ系統を作成する。

### プロファイル YAML の例（Dockerfile 生成の入力）

```yaml
profiles:
  pptx-skill:
    includes:
      base_image: python:3.11-slim
      install_commands:
        - 'pip install "markitdown[pptx]"'
        - "pip install defusedxml"
        - "npm install -g pptxgenjs"
        - "npm install -g playwright"
        - "npm install -g react-icons react react-dom"
        - "npm install -g sharp"
        - "apt-get update && apt-get install -y libreoffice poppler-utils"
      normalized_packages:
        npm:
          - pptxgenjs
          - playwright
          - react-icons
          - react
          - react-dom
          - sharp
        pip:
          - "markitdown[pptx]"
          - defusedxml
        apt:
          - libreoffice
          - poppler-utils
```

`install_commands` を主要入力とし、`normalized_packages` は任意の補助情報とする。生成後のイメージタグは CI のビルド成果物として管理し、プロファイル YAML には書かない。

### セキュリティ・制約（ビルド時）

- 外部ネットワークは次の用途に限定して許可する。
  - `skill-sources.yaml` で pin した URL からのスキル取得
  - 依存抽出のための LLM 外部 API 呼び出し（送信内容は依存抽出に必要な最小限に限定）
- 作業ディレクトリ外への書き込み禁止（.skills と build/ 配下のみ使用）。
- 署名検証・ハッシュ固定は別タスクで拡張可能だが、本タスクでは pin + CI 検証で担保。

### テスト戦略（ビルド成果物のみ）

- Unit: 依存宣言パースの成功/失敗、プロファイル自動生成が期待通りの YAML を出すこと、Dockerfile 生成が依存を含むこと。
- Integration: 生成した Dockerfile でビルドし、必要な依存（node/playwright/sharp、python/markitdown 等）が存在するかを確認（実行フロー統合は別タスク）。
- CI: 依存検証ジョブが不足を検知して失敗すること。

## Open Questions

- プロファイル粒度（細分化 vs 大括り）の最適点はどこまでか。
- ビルドしたイメージの配布方法（レジストリへの push とタグ/ハッシュ管理）の詳細。
