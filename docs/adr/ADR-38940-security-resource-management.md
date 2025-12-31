# ADR-38940 セキュリティ・リソース管理フレームワーク

## Metadata

- Type: ADR
- Status: Approved
- ID: ADR-38940

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../analysis/AN-f545x-claude-skills-agent.md)
- Related ADRs:
  - [ADR-ehfcj スキル実行エンジン設計](ADR-ehfcj-skill-execution-engine.md)
  - [ADR-ae6nw AgenticScope の活用シナリオ](ADR-ae6nw-agenticscope-scenarios.md)
  - [ADR-q333d Agentic パターンの選択基準](ADR-q333d-agentic-pattern-selection.md)
- Impacted Requirements:
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [NFR-3gjla セキュリティとリソース管理](../requirements/NFR-3gjla-security-resource-governance.md)
  - [NFR-yiown スキル検証・監査](../requirements/NFR-yiown-skill-verification-auditability.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
- Related Tasks:
  - [T-9ciut コード品質ツールの統合](../tasks/T-9ciut-code-quality-tools/README.md)
  - [T-p1x0z 複雑スキル依存バンドル](../tasks/T-p1x0z-skill-deps-runtime/README.md)
  - [T-8z0qk 複雑スキルランタイム統合](../tasks/T-8z0qk-runtime-execution/README.md)
  - [T-63g7b スキル実行エージェント設計](../tasks/T-63g7b-skill-execution-agent/README.md)

---

## Context

Claude Skills は「任意の CLI コマンド実行」「Python/Node.js など任意コード実行」を前提とした、高い自由度の設計となっている。一方、本プロジェクトの LangChain4j ベース実装は、以下のような特徴を持つ環境で運用される想定である。

- Java / LangChain4j を用いたサーバサイド実装
- マルチテナント / 多数ユーザリクエスト
- スキル成果物に **pptx / 画像 / zip などのバイナリファイルが多い**
- 複数 Skill を連鎖させ、**前の Skill の成果物を次の Skill の入力として利用**するユースケースが中心

このとき、以下の観点でセキュリティとリソース管理を両立させる必要がある。

1. **スキル実行の安全性**
   - 任意コード実行によるホスト OS 汚染・情報漏えいの防止
   - マルチテナント環境でのテナント間隔離
2. **リソース枯渇耐性**
   - 無限ループ・メモリ爆発などによる JVM/ホストの障害防止
   - CPU・メモリ・同時実行数の制御
3. **成果物（バイナリ）の扱いやすさ**
   - LangChain4j Code Execution Engines は戻り値が **文字列のみ**であり、
     - pptx などのバイナリ
     - 複数 Skill 間の成果物受け渡し
       にそのままでは適さない
   - **共通サンドボックスファイルシステム**上で成果物をやりとりし、Skill 間では **パス（または Artifact ID）だけを文字列で受け渡す**方針が望ましい
4. **環境差（ローカル / テスト / 本番）**
   - ローカル／テスト環境では、開発者が手元で完結する Docker ベースのサンドボックスが使いやすい
   - エンタープライズ本番では、Azure のマネージドサービス（Azure Container Apps Dynamic Sessions; 以下 ACADS）を用いた堅牢なサンドボックスを使いたい

また、LangChain4j の Code Execution Engines（GraalVM, Judge0, Azure ACADS 統合など）は、基本的に以下のような性質を持つ。

- `CodeExecutionEngine#execute(String code)` という **文字列入出力 API** が中心
- テキスト完結の軽量処理には有効だが、**バイナリ成果物や共有ファイルシステムと直接結びついていない**
- 本プロジェクトの「成果物連携が前提のスキル体系」に対しては、**メインの実行基盤ではなく補助的利用**にとどめた方が設計として自然

以上を踏まえ、**本番は ACADS を標準サンドボックス、ローカル／テストは Docker をそのローカル対とする**方針でセキュリティ・リソース管理アーキテクチャを決定する。

---

## Success Metrics

本 ADR に基づく実装が以下を満たすことを成功条件とする。

1. **許可リスト型の実行可能スキル・操作が定義され、未許可スキルの実行が拒否される**
   - スキル毎に許可操作（ファイル操作・外部呼び出し種別）が定義されている
   - ポリシー違反コードは実行前に拒否、またはサンドボックス内で即時中断される

2. **スキル実行のタイムアウト・メモリ上限が機能し、リソース枯渇が防止される**
   - 内部実行（プロセス内）で一定時間を超えた場合は強制停止
   - 外部サンドボックス（Docker / ACADS）ではコンテナ単位で CPU/メモリ上限が機能する

3. **複数スキル並行実行時、各スキルおよび各テナントのリソース消費が独立して制限される**
   - テナント毎に同時実行数・日次実行時間・日次トークン上限などのクォータが設定されている
   - 一テナントの過負荷が他テナントやプラットフォーム全体へ波及しない

4. **スキル成果物（pptx 等）が「共通サンドボックスファイルシステム」で安全にやりとりできる**
   - 複数 Skill 間でファイルパス（または Artifact ID）を渡すことで成果物を連携可能
   - ファイル I/O はサンドボックス FS に制限され、ホスト FS や他テナントの領域にはアクセスできない
   - FR-cccz4 の pptx ワークフローでは、入力に応じたファイルパスのみを使用し、固定サンプルやハードコードに依存しないことを保証する

5. **環境別フェーズ移行がスムーズに行える**
   - Phase 1: ローカル／テスト環境で Docker ベースサンドボックスを利用開始
   - Phase 2: エンタープライズ本番環境で ACADS ベースサンドボックスを導入
   - いずれも共通の「サンドボックス FS + ファイルパス連携」概念で実装されており、アプリケーションレベルの変更が最小限で済む

---

## Decision

### 概要

以下の 3 点を本 ADR における主要な設計決定とする。

1. **本番の標準サンドボックスとして ACADS を採用し、Docker をそのローカル対として位置付ける**
2. **Phase 1 / Phase 2 の二段階導入を明示する**
3. **LangChain4j Code Execution Engines は、性能向上・軽量補助用途に限定し、優先度を Phase 2 以降に下げる**

### Phase 別方針

1. **Phase 1: ローカル / テスト環境 – Docker ベース自前サンドボックス**
   - 開発者マシンおよび CI テスト環境では、**Docker コンテナ + 仮想共有ファイルシステム（Docker Volume）** によるサンドボックスを採用する。
   - 各 Skill 実行は、`skill-sandbox` コンテナ上で行い、`/workspace` にマウントされたボリュームを通じて成果物（pptx 等）を読み書きする。
   - 複数 Skill の連携は、「前段 Skill が `/workspace/...` に出力したファイルのパスを、後段 Skill の入力パラメータとして渡す」パターンで統一する。
   - この時点では、LangChain4j Code Execution Engines（GraalVM 等）は **導入必須ではない**。必要であれば軽量なテキスト処理等の補助用途として限定使用する。

2. **Phase 2: エンタープライズ本番環境 – ACADS ベースサンドボックス**
   - 本番環境では、Azure Container Apps Dynamic Sessions（ACADS）を **標準サンドボックス実行環境**として採用する。
   - 各セッション（1 コンテナ）に **＼workspace マウント（Azure Files / Blob など）** を提供し、Phase 1 の `/workspace` と同等の共通ファイルシステムを実現する。
   - スキル成果物はこの共有 FS 上でやりとりし、ローカル / テスト（Docker）と本番（ACADS）で **同一のファイルパス連携モデル**を維持する。
   - 信頼度の低いスキル（外部・ユーザ起源など）も、原則 ACADS 内で実行し、ホストからの完全隔離を前提とする。

3. **Phase 3（以降）: Code Execution Engines の活用（優先度低）**
   - LangChain4j Code Execution Engines（GraalVM, Judge0, ACADS 向けラッパ等）は、
     - 文字列完結の処理
     - 高頻度・低レイテンシが求められる軽量処理
       に限定して活用する。
   - バイナリ成果物や複雑なスキルチェーンは、引き続き Docker / ACADS サンドボックス + サンドボックス FS で扱う。
   - 導入タイミングは Phase 2 以降とし、性能ボトルネックや開発効率向上の必要性が明確になった段階で検討する。

### Considered Options

- Option A: Java プロセス内でのメモリ・時間制限のみ
- Option B: 全スキルを外部コンテナ / プロセスで実行
- Option C: ハイブリッド – ACADS を本番標準サンドボックス、Docker をローカル対、Code Execution Engines は補助用途（採用）

### Option Analysis

- **Option A** — Pros: シンプルな実装 | Cons: セキュリティ隔離が弱く悪意あるコードに対して脆弱
- **Option B** — Pros: セキュリティが高い | Cons: オーバーヘッドと運用負荷が大きい
- **Option C** — Pros: セキュリティと実行性能・開発体験の両立、ローカルと本番で共通モデル再利用可能 | Cons: ACADS 依存、Phase 1/2 の二段階構成による運用複雑化

---

## Rationale

#### 成果物中心の Skill 連携設計との整合性

本プロジェクトの Claude Skills 実装では、pptx / 画像 / zip などのバイナリ成果物を多用し、それらを複数 Skill 間で受け渡す構成が中核となる。

- LangChain4j Code Execution Engines の現状インターフェースは **文字列入出力**に限られ、バイナリ成果物や複数ファイルを自然に扱うには不向きである。
- 文字列経由でバイナリを base64 エンコード／デコードする設計も可能だが、
  - 大きなファイルでオーバーヘッドが大きくなる
  - 「どのファイルがどの Skill の成果物か」の追跡が困難になる
    という課題がある。

これに対して、「**サンドボックス FS を共通インフラとし、Skill 間ではファイルパス（または Artifact ID）のみを文字列でやりとりする**」設計にすることで、

- バイナリ主体のスキルチェーンを自然に表現できる
- ローカル（Docker）と本番（ACADS）で同一の概念を再利用できる
- 監査・可観測性（どの Skill がどのファイルを生成／更新したか）の実装がしやすい

というメリットが得られる。

#### セキュリティと性能の両立

- ACADS は Azure が提供する **コンテナ・サンドボックス環境**であり、ホスト OS から隔離されたセッション単位のコンテナでコードを実行できる。
- エンタープライズ本番で「任意コード実行」を許容する以上、**プロセス内実行単独ではリスクが高く、OS レベルサンドボックスを標準とする必要がある**。

一方で、すべてを常に外部コンテナに投げると、開発・テストの生産性やレスポンス時間に大きな影響が出る。

- ローカル開発では、Docker によるサンドボックスを用意することで、
  - 「コンテナ上で動く」という本番近い挙動を保ちつつ
  - 開発者の手元だけで完結する
- 本番では ACADS を用い、同種の API 形状（セッション ID／共有 FS／コード実行エンドポイント）で、環境差を吸収する

という **Phase 1 → Phase 2 の二段階構成**は、セキュリティと実行性能・開発体験の両立に合理的である。

#### Code Execution Engines の優先順位付け

Code Execution Engines 自体は有用な機能だが、本プロジェクトにおいては以下の理由で「主役」にはしない。

- 文字列のみのインターフェースであり、**本プロジェクトのコア要件（バイナリ成果物連携）とは整合しない**。
- 高度な性能チューニング（例: 軽量な Python/JS 補助処理）を必要とする段階は、**まずは全体アーキテクチャ（サンドボックス FS + ACADS/Docker）が安定してから**でよい。
- Code Execution Engines は ACADS や Docker と組み合わせて「サンドボックス内でさらに別言語コードを実行する」といった使い方も可能であり、Phase 2 以降に「必要なところだけ導入する」方がリスクが小さい。

このため、Code Execution Engines は **Phase 2 以降の性能向上／開発効率向上のためのオプション**として位置付ける。

---

## Consequences

### Positive

- 本番環境のサンドボックス基盤が明確になる。エンタープライズ本番は ACADS を前提とし、非機能要件（セキュリティ・隔離・スケール）の多くを Azure に委譲できる。
- ローカル／テストと本番で「共通サンドボックス FS」という一貫したモデルを採用できる。Docker Volume と Azure Files / Blob を入れ替えるだけで、アプリケーション設計はほぼ共通化される。
- バイナリ成果物を中心としたスキルチェーン設計がシンプルになる。Skill 間はファイルパス文字列の受け渡しに集中し、ファイルの実体はサンドボックス FS に閉じ込められる。
- Code Execution Engines の採用タイミングを後ろ倒しにできる。先に「安全な実行基盤と成果物連携」を固め、そのうえで性能ボトルネックが顕在化した部分だけに Code Execution Engines を適用できる。

### Negative

- ACADS への依存。本番環境が Azure 前提となるため、クラウドベンダロックインが一定程度発生する。将来的に他クラウド（GCP, AWS）の類似サービスへ移行する場合のコストが増大する。
- Phase 1 / Phase 2 の二段階構成による運用の複雑さ。Docker サンドボックスと ACADS サンドボックスの両方を管理・監視する必要がある。
- Code Execution Engines を全面採用するパスよりも、初期の「簡単さ」は損なわれる。文字列入出力だけで完結する実装に比べて、サンドボックス FS とファイルパス連携を導入する分、実装コストは増加する。

### Neutral

- 将来的に、ACADS 以外のサンドボックス技術（WASM ランタイム等）を採用する場合でも、「サンドボックス FS + ファイルパス連携」という設計自体は再利用可能である。

---

## Implementation Notes

#### サンドボックス FS の論理構造

共通の論理構造を Docker / ACADS の双方で採用する。

- 例: `/workspace/{tenantId}/{sessionId}/{skillId}/...`
  - `tenantId`: テナント識別子
  - `sessionId`: 会話／ワークフロー／ジョブ単位での実行セッション ID
  - `skillId`: 実行した Skill の ID

スキル成果物は、このディレクトリ配下に出力し、**LLM / Skill 間では相対パス（または固定のベースパスを前提としたパス）を文字列としてやりとり**する。

#### ローカル / テスト（Phase 1）の Docker サンドボックス設計

- `skill-sandbox` コンテナイメージ
  - 必要なランタイム（Python, Node.js, CLI ツール等）をインストール
  - `/workspace` に named volume をマウント
- スキル実行用 API
  - LangChain4j からは、HTTP or gRPC 経由で `skill-sandbox` に「コード実行」「ファイル操作」を依頼する
  - 引数にはファイルパス（`/workspace/...`）および追加パラメータを含める
- セキュリティ
  - コンテナに割り当てる CPU / メモリ上限を Docker 側で設定
  - コンテナ内ユーザを非特権ユーザに限定
  - 外部ネットワークはデフォルト遮断（ブラウザ自動化が必要な場合もローカルファイル入力に限定）
- リソース監視
  - コンテナ起動／終了ログ、実行時間、メモリ使用量などをメトリクスとして収集

#### 本番（Phase 2）の ACADS 統合

- ACADS のセッションごとにコンテナを起動し、`/workspace` に Azure Files / Blob をマウントする。
- Docker と同様に、`/workspace/{tenantId}/{sessionId}/{skillId}` 構造を維持する。
- LangChain4j からは、ACADS の REST API を叩くクライアントコンポーネントを実装し、Docker サンドボックスと同等の抽象インターフェースで利用できるようにする（セッション作成 / コード送信 / 実行完了待機など）。
- ネットワークは原則遮断（例外は明示許可した外部 API のみ）。ブラウザ自動化が必要な場合もローカルファイルに限定し、外部サイトへのアクセスは禁止。

#### 依存宣言のパースと事前バンドル方針

- SKILL.md に記載された依存（言語/パッケージ/ツール）をパースし、「依存セット → ベースイメージタグ」をマッピングする。特定スキルをハードコーディングせず、宣言に基づく汎用マッピングとする。
- ベースイメージは少数のプロファイルで共通化し、例として:
  - `node-playwright-sharp`（pptxgenjs/Sharp/Playwright を含む）
  - `python-pptx-markitdown`（markitdown\[pptx], defusedxml を含む）
  - 必要に応じて追加の OS ツールを持つ派生（libreoffice/poppler 等）
- CI で依存検証ジョブを実行し、イメージタグごとに `node --version`、`playwright --version`、`python -c "import markitdown"` など存在チェックを行い、満たさない場合はビルド失敗とする。
- ランタイムは実行前に「SKILL.md 依存セット」と「利用するイメージタグ」を突き合わせ、未充足なら実行を拒否してエラーを返す。実行時の `pip/npm install` は行わない。
- 依存取得が必要な場合は、ビルドパイプラインで社内ミラーなど信頼済みソースを用いて取得し、イメージに焼き込む。実行環境は外部ネットワークを遮断したままとする。

#### 信頼度レベルと実行ポリシー

スキルごとに、信頼度レベルをメタデータとして定義する。

```java
public enum SkillTrustLevel {
    TRUSTED,      // 内部開発・完全検証済みスキル
    VERIFIED,     // 外部提供だがコードレビュー済み
    UNVERIFIED    // 未検証スキル／ユーザ提供スキル
}
```

- `TRUSTED` / `VERIFIED`：
  - 原則として ACADS サンドボックス内で実行
  - 将来的に、Code Execution Engines を組み合わせて一部処理を高速化する余地あり

- `UNVERIFIED`：
  - 常に ACADS サンドボックスで実行
  - 実行前に管理者承認や静的解析フローを挟むことも検討

#### スキル種別ごとのポリシー例（FR-cccz4 / skills/pptx）

- 許可コマンド（Phase 1/2 共通）:
  - Node: `node scripts/html2pptx.js`（ラッパを含む）、必要な npm グローバル依存（pptxgenjs, sharp, playwright）
  - Python: `python scripts/thumbnail.py`, `python ooxml/scripts/unpack.py`, `python ooxml/scripts/validate.py`, `python ooxml/scripts/pack.py`
  - Unix 基本ツール: `ls`, `cat`, `find`, `tar`, `zip/unzip` など read-only/圧縮用途に限定
- 禁止/制限:
  - 外部ネットワークアクセスは禁止（フォント/アイコン等も同梱リソースのみ使用）
  - 任意の `pip install` / `npm install` は禁止。依存はサンドボックスイメージに事前焼き込み。
  - 作業ディレクトリ外（`/workspace/{tenant}/{session}/...` 以外）への書き込み禁止。
  - 固定サンプルファイルに依存する実装は禁止。入力で指定されたファイルパス・テンプレートのみ利用する。
- リソース上限（初期値の目安、環境で調整可能）:
  - Node/Python プロセス: CPU 1-2 vCPU, メモリ 1-2 GiB, タイムアウト 120 秒/ステップ
  - ブラウザ自動化（Playwright）: 追加で 512 MiB、タイムアウト 180 秒/ステップ
- 成果物取り扱い:
  - 生成物（pptx, サムネイル, 生成した HTML/JS, 実行ログ）は成果物カタログに登録し、FR-hjz63 の可視化トレースに載せる。
  - 失敗時は中間生成物の保持/削除ポリシーを明示し、ログに記録する。

#### Code Execution Engines の利用ガイドライン（Phase 2 以降）

- 利用対象:
  - JSON 整形・変換
  - 軽量なテキスト処理
  - 小規模な数値計算

- 非利用（基本禁止）対象:
  - ファイル I/O を伴う処理
  - 外部ネットワークアクセス
  - 長時間実行や大規模データ処理

- Code Execution Engines は「プロセス内実行」であることを前提に、**常に ACADS / Docker のサンドボックス環境と併用**する（ホスト JVM 上で直接任意コードを走らせない）。

---

## Platform Considerations

- 本番環境は Azure 前提（ACADS, Azure Files / Blob）。
- ローカル／テストでは Docker / Docker Compose 前提。
- LangChain4j / Java のバージョンアップに伴う Code Execution Engines 仕様変更は、Phase 3 以降に限定的に対応する。

---

## Security & Privacy

- 任意コード実行は原則としてサンドボックス（Docker / ACADS）内に限定される。
- ファイル I/O は `/workspace` 以下に限定し、ホスト FS や他テナント領域へのアクセスを禁止する。
- スキル信頼度レベルと許可操作（ファイル操作・外部呼び出し）のホワイトリストを定義し、ポリシー違反を検出・拒否する。
- 実行ログには、入力データや成果物パスを記録するが、機密データはマスキング／トークナイズを行う。

---

## Monitoring & Logging

- テナント・スキル・セッション単位のメトリクス:
  - 実行時間
  - CPU / メモリ使用量
  - 入出力トークン数
  - 成果物ファイル数・サイズ

- セキュリティイベント:
  - ポリシー違反（許可されていない操作／ファイルアクセス）
  - タイムアウト発生
  - クォータ超過

- 可視化:
  - Observability プラットフォーム（例: Application Insights, Langfuse 等）に連携し、ダッシュボードで監視する。

---

## Open Questions

- [ ] ACADS の詳細なコストとスケーリング戦略 → Next step: 初期 PoC で実行頻度・実行時間を計測し、コスト試算を行う
- [ ] Docker ベースのローカルサンドボックスにおけるイメージ設計の標準化 → Method: Python/Node/CLI のバージョン・ライブラリ構成を決定
- [ ] Code Execution Engines を具体的にどのスキルで導入するか → Next step: 性能ボトルネック計測結果に基づき、適用候補スキルを洗い出す

---

## External References

- [Azure Container Apps Dynamic Sessions（ACADS）Documentation](https://learn.microsoft.com/azure/container-apps/sessions)
- [LangChain4j Code Execution Engines Documentation](https://docs.langchain4j.dev/)
- [Docker / Kubernetes Security Best Practices](https://docs.docker.com/engine/security/)
- [WebAssembly Security Considerations](https://webassembly.org/)
