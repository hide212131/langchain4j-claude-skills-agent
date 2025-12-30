# langchain4j-claude-skills-agent

Traceability process materials (TDL docs, templates, and supporting scripts) in this branch are adapted from the Kopi project (https://github.com/kopi-vm/kopi) and remain under the Apache License 2.0. We do not claim copyright over those copied portions; original rights stay with the Kopi authors.

## 実行方法

### CLIのビルドとインストール

```bash
./gradlew :app:installDist
```

実行可能ファイルは `app/build/install/skills/bin/skills` に生成されます。

### スキルのセットアップ

#### 1. スキルのダウンロード

```bash
# run-env/skill-sources.yaml に基づいてスキルをダウンロード
app/build/install/skills/bin/skills setup download

# カスタム設定ファイルを指定
app/build/install/skills/bin/skills setup download --config=/path/to/config.yaml --output=./my-skills
```

ダウンロードされたスキルは `build/skills/` (デフォルト) に保存されます。

#### 2. 依存関係の生成 (オプション)

```bash
# ダウンロードしたスキルから skill-deps.yaml を生成
app/build/install/skills/bin/skills setup generate-deps --skills-dir=build/skills
```

#### 3. Dockerfileの生成 (オプション)

```bash
# skill-deps.yaml から Dockerfile を生成
app/build/install/skills/bin/skills setup generate-dockerfile --skills-dir=build/skills
```

### スキルの実行

```bash
# スキルを実行
app/build/install/skills/bin/skills run --skill=build/skills/anthropics/skills/pptx/SKILL.md --goal="プレゼン資料を作成"

# Gradleから直接実行も可能
./gradlew run --args="run --skill app/src/test/resources/skills/e2e/SKILL.md --goal \"デモゴール\" --visibility-level basic"
```

#### オプション

- `--skill` : 実行する SKILL.md のパス（必須）
- `--goal` : エージェントに与えるゴール（任意）
- `--skill-id` : スキルIDを明示的に指定（任意）
- `--visibility-level` : 可視化ログレベル。`basic`（デフォルト）または `off`
- `--llm-provider` : LLMプロバイダ (`mock` または `openai`)
- `--exporter` : 可視化エクスポーター (`none` または `otlp`)

正常終了時は Plan/Act/Reflect のログと成果物が標準出力に表示されます。

### CLIコマンド一覧

```bash
# ヘルプを表示
app/build/install/skills/bin/skills --help

# setupコマンドのヘルプ
app/build/install/skills/bin/skills setup --help

# runコマンドのヘルプ
app/build/install/skills/bin/skills run --help
```

## 開発

### コード品質チェック

このプロジェクトでは以下のコード品質ツールを使用しています：

- **Checkstyle**: コーディング規約のチェック
- **PMD**: コード品質とバグパターンの検出
- **SpotBugs**: バイトコードレベルのバグ検出
- **Spotless**: コードフォーマットの自動化

```bash
# すべての品質チェックを実行
./gradlew check

# コードフォーマットの自動修正
./gradlew spotlessApply

# 個別のツールを実行
./gradlew checkstyleMain checkstyleTest
./gradlew pmdMain pmdTest
./gradlew spotbugsMain spotbugsTest
./gradlew spotlessCheck
```

### テストの実行

```bash
./gradlew test
```
