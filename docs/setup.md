# 開発環境セットアップガイド

本書は langchain4j-claude-skills-agent をローカル/CI 上で実行するために必要な環境変数と手順をまとめたものです。  
**P0-1（Workflow & CLI ブートストラップ）** 着手前に最低限の設定を済ませ、`--dry-run` と実 LLM の両方で疎通確認できる状態を目指してください。

## 1. 必須環境変数
- `OPENAI_API_KEY`  
  - 既定のプロバイダ（OpenAI GPT 系）で使用します。  
  - 本番用・個人用キーともに **リポジトリへコミットしない** こと。
- `ANTHROPIC_API_KEY`（任意）  
  - 代替プロバイダ（Claude）を利用する場合に設定します。現時点では未配線ですが、将来の切替用として予約。

### 設定場所の推奨
1. ローカル開発：`direnv` もしくはシェルの `~/.zshrc` 等に `export OPENAI_API_KEY=...` を記述。`.env` を使う場合は `.gitignore` で除外すること。  
2. CI：ジョブのシークレット変数に登録し、ワークフロー定義で `env:` に注入。  
3. 共有端末：OS のセキュアストア（macOS Keychain / Windows Credential Manager 等）経由で設定し、シェル起動時に読み出す。

未設定で CLI を起動すると `LangChain4jLlmClient` が明示的に失敗させます。`--dry-run` モードは環境変数なしでも動作しますが、実 API 検証の前に必ずセットアップしてください。

## 2. LangChain4j クライアント
- `app/build.gradle.kts` に `langchain4j-bom` / `langchain4j-open-ai` を追加済み。  
- 本番系クライアントは `LangChain4jLlmClient`（`provider` パッケージ）で提供され、OpenAI の function calling を利用して `invokeSkill` ツールを呼び出します。  
- テストでは既存の `FakeLlmClient` を継続して使用し、外部 API に依存しません。

## 3. ランタイム仮想環境と依存管理
- プロジェクトルート配下に `env/python`（`python -m venv`）と `env/node`（`npm init` 相当）を作成し、共通の依存インストール先とします。どちらも `.gitignore` 済み。  
  `./gradlew setupSkillRuntimes`（仮称。P0 タスクで追加予定）を実行すると両方の仮想環境が初期化されます。既存環境を再作成したい場合は `./gradlew setupSkillRuntimes --refresh-dependencies` を利用してください。
- `runScript` ツールは常にこれらの仮想環境を用いてスクリプトを実行します。Agent が `dependencies` パラメータ（`pip` や `npm` 配列など）を渡した場合、`runScript` は実行直前に指定パッケージをインストールします。
- 手動で依存を追加したい場合は各環境を直接アクティベートして `pip install` / `npm install` を実行できますが、再現性のため `requirements.txt` / `package.json` にも併記してください。

## 4. モデルとガードレール
- 既定モデル：`gpt-5`（`LangChain4jLlmClient.forOpenAi` のデフォルト）。必要に応じて CLI や設定ファイルで上書きできるよう後続タスクで調整します。  
- トークン/時間/ツール呼び出しの上限値は環境変数または設定ファイルに外出し予定。暫定値はコード側に埋め込み中。

## 5. CLI オプション（`./sk run`）
- `--goal <text>`: 必須。エージェントに与える高レベルの目的。  
- `--skills-dir <path>`: 任意。スキルディレクトリの位置（既定値はリポジトリ直下の `skills/`）。  
- `--dry-run`: 任意。Fake LLM を使って外部 API を呼び出さずにワークフローを検証。  
- `--debug-skill-ids id1,id2,...`: 任意。カンマ区切りで指定したスキル ID をその順で実行し、プランナーの自動選択をバイパスします。デバッグ用途のみで使用し、存在しないスキル ID は警告として出力されます。
## 6. skills ディレクトリ
- `skills/` には `brand-guidelines/` と `document-skills/pptx/`（いずれも anthropics/skills 由来）を配置します。  
- `./gradlew updateSkills` を実行すると、固定コミット（既定は `c74d647e56e6daa12029b6acb11a821348ad044b`）のアーカイブをダウンロードして `skills/` に展開します。  
  - `-PskillsCommit=<sha>` で任意のコミットへ切り替え可能です。展開後の `.skills-version` に解決済みコミットが記録されます。  
- 未取得の場合は一時的に手動コピーでも構いませんが、P0-1 の段階で最低限この 2 スキルが存在することを確認してください。  
- `skills/` はリポジトリ未追跡（`.gitignore` 登録済み）のため、個人環境ではローカルに置き、CI では本タスク（P1-4）で自動取得します。

## 7. クイックチェック
1. **ドライラン確認（API キー不要）**  
   ```bash
   ./gradlew test            # 省略可：単体テストを実行
  ./gradlew setupSkillRuntimes
   source .envrc
   ./sk run \
     --goal "dry-run sanity check" \
     --skills-dir skills \
     --dry-run
   ```  
   成功すれば Workflow/CLI 配線は完了です（Fake LLM が使用され、成果物はダミーの場合があります）。
2. **実 LLM 疎通（API キー必須）**  
  ```bash
  # direnv を使う場合は `source .envrc` または `direnv allow` で読み込む
  export OPENAI_API_KEY=...   # まだならセット（手動設定の場合）
  ./gradlew setupSkillRuntimes
  source .envrc
  ./sk run \
    --goal "ブランド準拠で5枚のスライドを作る" \
    --skills-dir skills
   ```  
   初回実行で `Tokens in/out/total` の集計ログと Plan サマリが出力されることを確認し、スクリーンショットやログ抜粋を共有できるようにしておきます。

   LLM への依存を避けてデバッグしたい場合は、以下のようにスキル実行順を固定できます。  
   ```bash
   source .envrc
   ./sk run \
     --goal "ブランドチェックの動作検証" \
     --skills-dir skills \
     --dry-run \
     --debug-skill-ids brand-guidelines,document-skills/pptx
   ```  
   このモードでは指定順に Act ステージが進み、Plan 段階の LLM 呼び出しは省略されます。

3. **VS Code デバッグ実行（再現性確保）**  
  `.vscode/launch.json` には `SkillsCliApp (debug skill ids)` 構成を用意しています。Run and Debug パネルで選択して F5（または Run ボタン）を押すと、`SkillsCliApp` の `main` に直接アタッチした状態で以下の引数が渡されます。  
  ```bash
  run --goal "ブランド準拠で5枚のスライドを作る" --skills-dir skills --debug-skill-ids brand-guidelines,document-skills/pptx
  ```  
  デバッグ環境向けの環境変数はプロジェクトルートの `.env` に `KEY=VALUE` 形式で記述し（例：`OPENAI_API_KEY=...`）、`launch.json` の `envFile` から読み込ませます。目標やスキル並びを変えたい場合は、`launch.json` を複製して `args` を調整すると便利です。

## 8. 今後のタスク連携
- P0-1 完了後は LangChain4j Workflow ノード（Plan / Act）の充実と安全性向上を段階的に進めます。  
- 追加の設定項目（モデル名、スロットリング、プロキシなど）が増えた場合は本書に追記してください。

## 9. パッケージ雛形の作成手順
- ソース構成は `spec.md 2.1` に従い、基底パッケージ `io.github.hide212131.langchain4j.claude.skills` の下に以下のディレクトリを作成します。  
  ```bash
  cd app/src/main/java/io/github/hide212131/langchain4j/claude/skills
  mkdir -p app/cli \
           runtime/workflow/plan \
           runtime/workflow/act \
           runtime/workflow/support \
           runtime/provider \
           runtime/skill \
           runtime/blackboard \
           runtime/context \
           runtime/guard \
           runtime/human \
           infra/logging \
           infra/config
  ```
- それぞれに `package-info.java` もしくは空のスケルトンクラスを置き、パッケージ階層を検証できる状態にします。  
- 新規パッケージ追加時は `docs/spec.md` / `docs/tasks.md` のモジュール構成を更新し、依存方針との整合を保ってください。

## 10. JUnit セットアップ
- 依存関係は `app/build.gradle.kts` に追加済み（JUnit 5 / AssertJ）。  
- `./gradlew test` を実行し、テスト環境が正常に起動することを確認します。  
- **Codex CLI（サンドボックス）での実行**：Gradle はファイルロック取得に制限がかかるため、テスト実行時は昇格付きコマンドを使用してください。  
  ```json
  {
    "command": ["bash", "-lc", "./gradlew test"],
    "workdir": "/Users/hide/Github/langchain4j-claude-skills-agent",
    "with_escalated_permissions": true,
    "justification": "Gradle requires unrestricted filesystem access to obtain file locks while running tests"
  }
  ```

以上で LangChain4j + OpenAI を利用するための最低限の下準備は完了です。レビュー時はこのガイドを参照しつつ、環境変数の有無を確認してください。
