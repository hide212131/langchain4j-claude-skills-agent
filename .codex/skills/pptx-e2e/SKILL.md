---
name: pptx-e2e
purpose: PPTX E2E テスト共通の準備と実行手順
---

# PPTX E2E テスト手順（共通）

## 概要

PPTX-001〜PPTX-XXX の E2E テストを実行し、分岐・成果物・ログが期待どおりに出ることを確認する。
本手順は「実行意図を明確にし、検証可能な成果物を残す」ことを重視する。

## 期待される成果

- 目的に対応する分岐（markitdown/OOXML/テンプレ処理など）が選択される。
- `./build` 配下の成果物、または標準出力に必要な結果が残る。
- ログに `plan/act/reflect` および `task.output.*` が残る。

## 前提

- Docker が利用できること（スキルダウンロードで必須）。
- 環境変数は `.env` に存在すること。
- `app/build/install/skills/bin/skills` 実行前に `.env` を反映すること。
- 入力 PPTX が必要なケースでは、以下のファイルを使用すること。\
  `app/src/test/resources/skills/pptx/langchain4j_presentation.pptx`

## 実行前チェック

- スキル配下に必要なドキュメントとスクリプトがあることを確認する。
- Windows スタイルのパスは使わず、すべて `/` で記述する。

```bash
ls build/skills/anthropics/skills/pptx
ls build/skills/anthropics/skills/pptx/scripts
```

## 準備

1. スキルをダウンロードする（Docker 必須）。

```bash
./gradlew :app:installDist
app/build/install/skills/bin/skills setup download
```

2. ビルドを実行する（Java 変更がある場合は必ず）。

```bash
./gradlew check
./gradlew test
./gradlew build
```

3. `.env` を反映する。

```bash
set -a; source .env; set +a
```

4. PPTX スキルが取得できていることを確認する。

```bash
test -f build/skills/anthropics/skills/pptx/SKILL.md && echo "pptx skill ok"
```

5. 入力ファイルを用意する（PPTX が必要なケースのみ）。

```bash
INPUT_PPTX=app/src/test/resources/skills/pptx/langchain4j_presentation.pptx
```

## 実行

ゴール文は達成したい内容と出力条件のみを記述し、手順や SKILL.md の中身に依存する指示は含めないこと。
テキスト出力が期待されるケースでは、出力条件として「実行結果は markdown で出力」と明記すること。

例:

```
スピーカーノートも含めて内容を確認したい。実行結果は markdown で出力。
```
ログは `SKILL_LOG_FILE=./build/logs/pptx-XXX.log` のように `build/` 配下へ保存すること。

```bash
set -a; source .env; set +a
app/build/install/skills/bin/skills run \
  --skill build/skills/anthropics/skills/pptx/SKILL.md \
  --goal "<PPTX-XXX の要求文>" \
  --input-file "${INPUT_PPTX}" \
  --output-dir ./build \
  --llm-provider openai
```

## ケース設計の指針（PPTX-XXX 共通）

- **実行意図を明確にする**: 何を「実行」し、何を「参照として読む」かをゴール文で区別する。
- **ファイルアクセスパターンをテストする**: `ooxml.md` や `scripts/` を必要時に読み、不要時は読まない。
- **スクリプト優先**: 変換・解析はスクリプトで実行し、LLM には委ねない。
- **検証ステップを必ず入れる**: `validate` やサムネイル生成など、品質確認の手順を明示する。

## 確認ポイント

- 実行計画でケースに応じた分岐（markitdown/OOXML/テンプレ処理など）が選ばれること。
- `./build` 配下に成果物が出力される、または標準出力に必要な情報が現れること。
- 期待されるログ（`plan/act/reflect` や `task.output.*`）が残ること。

## 失敗時のチェック

- `OPENAI_API_KEY` が設定されているか。
- 入力 PPTX が指定されているか（PPTX が必要なケースのみ）。
- `build/skills/anthropics/skills/pptx/` 配下に `ooxml.md` や `scripts/` が存在するか。
- スクリプトのパスがずれている場合は `find` で探索する（ハードコードしない）。

```bash
find build/skills/anthropics/skills/pptx -name "*.py" -o -name "*.js"
```
