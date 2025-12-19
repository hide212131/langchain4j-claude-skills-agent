# T-9ciut コード品質ツールの統合

## Metadata

- Type: Design
- Status: Approved

## Links

- Related Analyses:
  - [AN-f545x Claude Skills エージェント実装 | LangChain4j Agent 統合分析](../../analysis/AN-f545x-claude-skills-agent.md)
- Related Requirements:
  - [NFR-mt1ve エラーハンドリングと堅牢性](../../requirements/NFR-mt1ve-error-handling-resilience.md)
  - [NFR-30zem Observability 統合](../../requirements/NFR-30zem-observability-integration.md)
- Related ADRs:
  - [ADR-ij1ew Observability 統合](../../adr/ADR-ij1ew-observability-integration.md)
  - [ADR-38940 セキュリティ・リソース管理フレームワーク](../../adr/ADR-38940-security-resource-management.md)
- Associated Plan Document:
  - [T-9ciut-code-quality-tools-plan](plan.md)

## Overview

JavaプロジェクトにCheckstyle、PMD、SpotBugs、Spotlessの4つのコード品質ツールを統合します。これにより、コーディング規約の自動チェック、潜在的なバグの検出、コードフォーマットの統一が可能になります。各ツールはGradleプラグインとして統合され、`./gradlew check`コマンドで一括実行できるようにします。

## Success Metrics

- [x] 全てのツールがGradleビルドに統合され、`./gradlew check`で実行可能
- [ ] 既存コードがツールのチェックを通過する（または適切な除外設定が完了）
- [ ] 設定ファイルが明確で保守可能な状態

## Background and Current State

- Context: langchain4j-claude-skills-agentプロジェクトはJavaベースのアプリケーション
- Current behavior: 現在、コード品質チェックツールが統合されていない
- Pain points: コーディング規約の不統一、潜在的なバグの見逃し、フォーマットの不一致
- Constraints: 既存のビルドシステム（Gradle）を利用し、チーム開発に適した設定が必要

## Proposed Design

### High-Level Architecture

```text
Gradle Build
    ├── checkstyleMain/Test (コーディング規約)
    ├── pmdMain/Test (コード品質)
    ├── spotbugsMain/Test (バグ検出)
    └── spotlessCheck/Apply (フォーマット)
         └── check task (全て統合)
```

### Components

#### 1. Checkstyle

- **責任**: Javaコーディング規約の検証
- **設定ファイル**: `config/checkstyle/checkstyle.xml`
- **Gradleプラグイン**: `checkstyle`（Gradle組み込み）
- **実行タスク**: `checkstyleMain`, `checkstyleTest`

#### 2. PMD

- **責任**: コード品質とバグパターンの検出
- **設定ファイル**: `config/pmd/ruleset.xml`
- **Gradleプラグイン**: `pmd`（Gradle組み込み）
- **実行タスク**: `pmdMain`, `pmdTest`

#### 3. SpotBugs

- **責任**: バイトコードレベルのバグパターン検出
- **設定ファイル**:
  - `config/spotbugs/exclude.xml`（除外ルール）
  - プラグイン設定（`build.gradle`内）
- **Gradleプラグイン**: `com.github.spotbugs`
- **実行タスク**: `spotbugsMain`, `spotbugsTest`

#### 4. Spotless

- **責任**: コードフォーマットの自動化
- **設定**: `build.gradle`内で直接設定
- **Gradleプラグイン**: `com.diffplug.spotless`
- **実行タスク**: `spotlessCheck`, `spotlessApply`

### Data Flow

1. 開発者がコードを変更
2. `./gradlew check`を実行
3. 各ツールが順次実行:
   - Spotless: フォーマットチェック
   - Checkstyle: コーディング規約チェック
   - PMD: コード品質チェック
   - SpotBugs: バグパターンチェック
4. いずれかが失敗した場合、ビルドが失敗し、問題箇所を報告
5. 開発者が問題を修正（またはSpotlessの場合は`./gradlew spotlessApply`で自動修正）

### Storage Layout and Paths

```
プロジェクトルート/
├── build.gradle (プラグイン設定)
├── config/
│   ├── checkstyle/
│   │   └── checkstyle.xml
│   ├── pmd/
│   │   └── ruleset.xml
│   └── spotbugs/
│       └── exclude.xml
└── build/
    └── reports/ (各ツールの実行結果レポート)
        ├── checkstyle/
        ├── pmd/
        ├── spotbugs/
        └── spotless/
```

### API/Interface Design

実行コマンド:

```bash
# 全チェックの実行
./gradlew check

# 個別実行
./gradlew checkstyleMain checkstyleTest
./gradlew pmdMain pmdTest
./gradlew spotbugsMain spotbugsTest
./gradlew spotlessCheck

# フォーマット自動修正
./gradlew spotlessApply
```

### Error Handling

- 各ツールは違反を検出した場合、わかりやすいエラーメッセージと該当箇所を出力
- エラーメッセージは日本語または明確な英語で記述
- ビルドログには詳細なレポートへのパスを表示
- 重大な違反はビルド失敗、軽微な警告は通知のみとする設定も可能

### Performance Considerations

- SpotBugsはバイトコードを解析するため、大規模プロジェクトでは実行時間が長くなる可能性
- 初回実行時は設定ファイルの読み込みとキャッシュ構築に時間がかかる
- Gradleのインクリメンタルビルドを活用し、変更されたファイルのみをチェック

## Alternatives Considered

1. **単一の統合ツール（例: SonarQube）を使用**
   - Pros: 一元管理、統合されたレポート
   - Cons: セットアップが複雑、オーバーヘッドが大きい、ローカル開発での使用が難しい

2. **Checkstyleのみ使用**
   - Pros: シンプル、軽量
   - Cons: コードフォーマット、バグ検出機能が不足

3. **SpotlessとCheckstyleのみ使用**
   - Pros: 基本的な品質チェックとフォーマットをカバー
   - Cons: PMDやSpotBugsが提供する高度なバグ検出機能が利用できない

Decision Rationale

- 複数のツールを組み合わせることで、各ツールの強みを活かせる
- Gradleプラグインとして統合されているため、設定が容易
- 業界標準のツールを使用することで、チーム全体の知識共有が容易

## Migration and Compatibility

- Backward compatibility: 既存のビルドプロセスに追加されるため、互換性の問題なし
- Rollout plan: 段階的に導入。まず設定を追加し、警告レベルで実行。その後、エラーレベルに変更
- Deprecation plan: N/A

## Testing Strategy

### Unit Tests

- N/A - ツール設定自体はテスト対象外

### Integration Tests

- 各ツールが正しく設定され、実行可能であることを手動で確認
- サンプルコードで意図的な違反を作成し、検出されることを確認

### Performance & Benchmarks

- 初回実行時と2回目以降の実行時間を測定
- 大規模ファイルでの実行時間を記録

## Documentation Impact

- `AGENTS.md`の「Developer Principles」セクションに、コード品質ツールの使用方法を追加
- プロジェクトのREADMEに、開発者向けのクイックスタートガイドを追加
- 各ツールの設定ファイルにコメントを追加し、カスタマイズ方法を説明

## Open Questions

- [ ] 既存コードが全てのルールを満たすか？ → 段階的な導入が必要な場合、除外設定を作成
- [ ] チーム全員がツールの使用方法を理解しているか？ → ドキュメント作成とトレーニングが必要

## Appendix

### Examples

```bash
# 開発フロー例
# 1. コード変更
vim src/main/java/com/example/MyClass.java

# 2. フォーマット自動修正
./gradlew spotlessApply

# 3. 全チェック実行
./gradlew check

# 4. エラーがあれば修正し、再度チェック
```

### Glossary

- Checkstyle: Javaコーディング規約チェックツール
- PMD: コード品質とバグ検出のための静的解析ツール
- SpotBugs: バイトコードを解析してバグパターンを検出するツール
- Spotless: コードフォーマット自動化ツール
