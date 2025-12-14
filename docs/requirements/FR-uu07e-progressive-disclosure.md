# FR-uu07e Progressive Disclosure 実装

## Metadata

- Type: Functional Requirement
- Status: Draft
  <!-- Draft: Under discussion | Approved: Ready for implementation | Rejected: Decision made not to pursue this requirement -->

## Links

- Prerequisite Requirements:
  - [FR-hjz63 プロンプト・エージェント可視化フレームワーク](../requirements/FR-hjz63-prompt-visibility-framework.md)
  - [FR-mcncb 単一スキルの簡易実行](../requirements/FR-mcncb-single-skill-basic-execution.md)
  - [FR-cccz4 単一スキルの複雑手続き実行](../requirements/FR-cccz4-single-skill-complex-execution.md)
  - [FR-2ff4z 複数スキル連鎖実行](../requirements/FR-2ff4z-multi-skill-composition.md)
- Dependent Requirements:
  - [NFR-kc6k1 Context Engineering 最適化](../requirements/NFR-kc6k1-context-engineering-optimization.md)
- Related Tasks:
  - N/A – タスクは要件承認後に作成

## Requirement Statement

> Focus the requirement on the problem to solve and the desired outcome, remaining independent of any specific implementation approach.

Claude Skills の 3 段階ロード（メタデータ常時、本文必要時、リソース実行時のみ）の Progressive Disclosure を LangChain4j エージェントで実装し、トークン消費を抑えつつ精度を維持すること。

## Rationale

複数スキル運用時に全情報を常時コンテキストに含めるとトークンコストが膨らみスケールしないため、段階的ロードで効率と精度のバランスを取る必要がある。

## User Story (if applicable)

As a サーバサイド開発者, I want スキル情報を必要なタイミングだけロードしたい, so that トークン予算を抑えつつ精度とレスポンスを両立できる。

## Acceptance Criteria

- [ ] Level1: frontmatter メタデータをシステムプロンプトに常時含める実装があること（目標 \~100 トークン）。
- [ ] Level2: スキル本文をスキル選択時にのみ動的ロードし、コンテキストに追加できること（<500 トークン目標）。
- [ ] Level3: 参照リソースを実行時のみアクセスし、結果だけをコンテキストへ反映できること。
- [ ] Progressive Disclosure 適用時と非適用時のトークン消費を計測し、削減効果を示せること。
- [ ] 複数スキル運用時のコンテキスト効率（トークン消費/精度）が FR-hjz63 で追跡できること。

## Technical Details (if applicable)

### Functional Requirement Details

- AgenticScope で各レベルのロード状態を管理し、Plan/Act/Reflect から参照可能にする。
- フェーズ 2 以降で動的圧縮などの拡張が可能なインターフェースを設計する。

### Non-Functional Requirement Details

`N/A – 機能要件の詳細に含める`

## Platform Considerations

### Unix

`N/A – Platform agnostic`

### Windows

`N/A – Platform agnostic`

### Cross-Platform

`N/A – Platform agnostic`

## Risks & Mitigation

| Risk                     | Impact | Likelihood | Mitigation                                 | Validation                       |
| ------------------------ | ------ | ---------- | ------------------------------------------ | -------------------------------- |
| 遅延ロードによる情報不足 | Medium | Medium     | レベル間のフォールバックと再実行パスを用意 | 可視化ログとテストで精度を確認   |
| 実装複雑度の増加         | Medium | Medium     | レイヤー分離し、インターフェースを明確化   | 単体・統合テストで責務分離を検証 |

## Implementation Notes

- AN-f545x の FR-DRAFT-4 を正式化し、ADR-mpiub の決定に従って段階的ロードを実装する。
- Observability（FR-hjz63, NFR-30zem）でトークン計測を行い、削減率を評価する。

## External References

- N/A – No external references

---

## Template Usage

For detailed instructions, see [Template Usage Instructions](../templates/README.md#individual-requirement-template-requirementsmd) in the templates README.
