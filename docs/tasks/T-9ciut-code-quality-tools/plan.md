# T-9ciut コード品質ツールの統合

## Metadata

- Type: Implementation Plan
- Status: Draft

## Links

- Associated Design Document:
  - [T-9ciut-code-quality-tools-design](design.md)

## Overview

JavaプロジェクトにCheckstyle、PMD、SpotBugs、Spotlessの4つのコード品質ツールを統合し、コードの一貫性、品質、セキュリティを自動的にチェックする仕組みを構築します。

## Success Metrics

- [ ] 全てのツールがGradleビルドに統合され、`./gradlew check`で実行可能
- [ ] 各ツールの設定ファイルが適切に作成され、動作確認完了
- [ ] 既存のテストが全てパスする
- [ ] ドキュメントが更新され、使用方法が明確

## Scope

- Goal: コード品質ツールの統合により、開発プロセスでの品質担保を自動化
- Non-Goals:
  - 既存コードの大規模リファクタリング
  - カスタムルールの実装
  - CI/CDパイプラインへの統合（別タスクで対応）
- Assumptions:
  - Gradleビルドシステムが既に構成されている
  - Java 17以上が使用されている
- Constraints: 既存のビルドプロセスに影響を与えない形で追加

## ADR & Legacy Alignment

- [x] 最新のADR/設計ドキュメントを確認済み - 本タスクは標準的なツール導入のため特定のADRは不要
- [x] 既存コードとの整合性確認 - 段階的な導入により既存コードへの影響を最小化

## Plan Summary

- Phase 1 – Checkstyleの設定と統合
- Phase 2 – PMD、SpotBugs、Spotlessの設定と統合
- Phase 3 – 動作確認とドキュメント更新

### Phase Status Tracking

チェックボックス（`[x]`）は各タスク完了後に即座にマーク。意図的にスキップまたは延期する項目は、取り消し線とメモで注釈。

---

## Phase 1: Checkstyleの設定と統合

### Goal

- Checkstyleプラグインの統合と基本設定ファイルの作成

### Inputs

- Documentation:
  - `AGENTS.md` – 開発者原則とコード品質要件
- Source Code to Modify:
  - `build.gradle` – プラグイン設定の追加
- Dependencies:
  - External: Gradle Checkstyleプラグイン（組み込み）

### Tasks

- [x] **Gradleプラグイン設定**
  - [x] `build.gradle`にCheckstyleプラグインを追加
  - [x] Checkstyleバージョンを指定
  - [x] `checkstyle`タスクを`check`タスクに依存させる
- [x] **Checkstyle設定ファイル作成**
  - [x] `config/checkstyle/checkstyle.xml`を作成
  - [x] 基本的なGoogle Java Style Guideベースのルールセットを設定
  - [x] プロジェクト固有の調整を適用
- [x] **初回実行と調整**
  - [x] `./gradlew checkstyleMain checkstyleTest`を実行
  - [x] 既存コードで問題が発生する場合、適切な除外設定を追加（maxWarningsを50に設定）

### Deliverables

- `build.gradle`にCheckstyleプラグイン設定
- `config/checkstyle/checkstyle.xml`設定ファイル

### Verification

```bash
# ビルドとチェック
./gradlew checkstyleMain checkstyleTest
./gradlew check
```

### Acceptance Criteria (Phase Gate)

- Checkstyleが正常に実行され、レポートが生成される
- 既存コードがCheckstyleチェックをパスする（または適切な除外設定が完了）

### Rollback/Fallback

- `build.gradle`からCheckstyleプラグイン設定を削除
- `config/checkstyle/`ディレクトリを削除

---

## Phase 2: PMD、SpotBugs、Spotlessの設定と統合

### Phase 2 Goal

- 残り3つのツール（PMD、SpotBugs、Spotless）の統合と設定

### Phase 2 Inputs

- Dependencies:
  - Phase 1: Checkstyleの設定と統合が完了していること
- Source Code to Modify:
  - `build.gradle` – 追加プラグイン設定

### Phase 2 Tasks

- [ ] **PMD設定**
  - [ ] `build.gradle`にPMDプラグインを追加
  - [ ] `config/pmd/ruleset.xml`を作成
  - [ ] 基本的なルールセットを設定
  - [ ] `./gradlew pmdMain pmdTest`で動作確認
- [ ] **SpotBugs設定**
  - [ ] `build.gradle`にSpotBugsプラグインを追加
  - [ ] `config/spotbugs/exclude.xml`を作成（必要に応じて）
  - [ ] レポート形式を設定（HTML推奨）
  - [ ] `./gradlew spotbugsMain spotbugsTest`で動作確認
- [ ] **Spotless設定**
  - [ ] `build.gradle`にSpotlessプラグインを追加
  - [ ] Java用のGoogle Java Formatを設定
  - [ ] `./gradlew spotlessCheck`で動作確認
  - [ ] `./gradlew spotlessApply`で自動フォーマット実行
- [ ] **統合確認**
  - [ ] `./gradlew check`で全ツールが実行されることを確認

### Phase 2 Deliverables

- `build.gradle`にPMD、SpotBugs、Spotlessプラグイン設定
- `config/pmd/ruleset.xml`設定ファイル
- `config/spotbugs/exclude.xml`設定ファイル（必要に応じて）

### Phase 2 Verification

```bash
./gradlew pmdMain pmdTest
./gradlew spotbugsMain spotbugsTest
./gradlew spotlessCheck
./gradlew check
```

### Phase 2 Acceptance Criteria

- 全てのツールが正常に実行され、レポートが生成される
- `./gradlew check`が成功する
- 既存のテストが全てパスする

### Phase 2 Rollback/Fallback

- 各プラグインの設定を`build.gradle`から個別に削除
- 対応する設定ファイルを削除

---

## Phase 3: 動作確認とドキュメント更新

### Phase 3 Goal

- 全ツールの動作確認とドキュメントの更新

### Phase 3 Tasks

- [ ] **動作確認**
  - [ ] 全てのツールが`./gradlew check`で実行されることを確認
  - [ ] レポート生成を確認（`build/reports/`配下）
  - [ ] 意図的なコード違反を作成し、各ツールが検出することを確認
- [ ] **ドキュメント更新**
  - [ ] `AGENTS.md`の「Completing Work」セクションを確認（既に記載済みか確認）
  - [ ] プロジェクトREADMEに開発者向けガイドを追加（必要に応じて）
  - [ ] 各設定ファイルにコメントを追加
- [ ] **最終確認**
  - [ ] `./gradlew clean check`で全ビルドプロセスを検証
  - [ ] `./gradlew test`で全テストをパス確認

### Phase 3 Deliverables

- 更新されたドキュメント
- 動作確認済みのコード品質ツール統合

### Phase 3 Verification

```bash
./gradlew clean
./gradlew check
./gradlew test
```

### Phase 3 Acceptance Criteria

- 全てのツールが正常に動作し、適切なレポートを生成
- ドキュメントが明確で、開発者が使用方法を理解できる
- 全てのテストがパス

---

## Definition of Done

- [ ] `./gradlew check`が成功
- [ ] `./gradlew test`が成功
- [ ] 各ツールの設定ファイルが適切に作成され、コメントが追加されている
- [ ] ドキュメント更新完了（AGENTS.md、READMEなど）
- [ ] レポート生成確認（`build/reports/`配下）
- [ ] 既存のビルドプロセスに影響がないことを確認

## Open Questions

- [ ] 既存コードが全てのルールを満たすか？ → Phase 1および2で確認し、必要に応じて除外設定を追加
- [ ] チーム全員が使用方法を理解できるドキュメントになっているか？ → Phase 3でドキュメントレビュー
