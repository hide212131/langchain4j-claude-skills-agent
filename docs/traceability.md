# TDL Traceability Overview

Generated on 2025-11-30T13:27:16.538Z

## Summary

| Metric | Count |
| --- | ---: |
| Analyses | 1 |
| Requirements | 11 |
| ADRs | 9 |
| Tasks | 0 |
| Requirements with tasks | 0 (0%) |

## Traceability Matrix

| Analyses | ADRs | Requirement | Status | Tasks |
| --- | --- | --- | --- | --- |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-ae6nw](adr/ADR-ae6nw-agenticscope-scenarios.md)<br>[ADR-q333d](adr/ADR-q333d-agentic-pattern-selection.md)<br>[ADR-38940](adr/ADR-38940-security-resource-management.md)<br>[ADR-lsart](adr/ADR-lsart-langchain4j-agentic-verification.md)<br>[ADR-ckr1p](adr/ADR-ckr1p-skill-implementation-leveling.md)<br>[ADR-ehfcj](adr/ADR-ehfcj-skill-execution-engine.md) | [FR-2ff4z](requirements/FR-2ff4z-multi-skill-composition.md) - FR-2ff4z 複数スキル連鎖実行 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-ae6nw](adr/ADR-ae6nw-agenticscope-scenarios.md)<br>[ADR-q333d](adr/ADR-q333d-agentic-pattern-selection.md)<br>[ADR-lsart](adr/ADR-lsart-langchain4j-agentic-verification.md)<br>[ADR-ckr1p](adr/ADR-ckr1p-skill-implementation-leveling.md)<br>[ADR-ehfcj](adr/ADR-ehfcj-skill-execution-engine.md) | [FR-cccz4](requirements/FR-cccz4-single-skill-complex-execution.md) - FR-cccz4 単一スキルの複雑手続き実行 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-q333d](adr/ADR-q333d-agentic-pattern-selection.md) | [FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md) - FR-hjz63 プロンプト・エージェント可視化フレームワーク | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-ae6nw](adr/ADR-ae6nw-agenticscope-scenarios.md)<br>[ADR-q333d](adr/ADR-q333d-agentic-pattern-selection.md)<br>[ADR-lsart](adr/ADR-lsart-langchain4j-agentic-verification.md)<br>[ADR-ckr1p](adr/ADR-ckr1p-skill-implementation-leveling.md)<br>[ADR-ehfcj](adr/ADR-ehfcj-skill-execution-engine.md) | [FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md) - FR-mcncb 単一スキルの簡易実行 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-ae6nw](adr/ADR-ae6nw-agenticscope-scenarios.md)<br>[ADR-q333d](adr/ADR-q333d-agentic-pattern-selection.md)<br>[ADR-ckr1p](adr/ADR-ckr1p-skill-implementation-leveling.md)<br>[ADR-mpiub](adr/ADR-mpiub-context-engineering-strategy.md) | [FR-uu07e](requirements/FR-uu07e-progressive-disclosure.md) - FR-uu07e Progressive Disclosure 実装 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-ij1ew](adr/ADR-ij1ew-observability-integration.md)<br>[ADR-ae6nw](adr/ADR-ae6nw-agenticscope-scenarios.md)<br>[ADR-ckr1p](adr/ADR-ckr1p-skill-implementation-leveling.md) | [NFR-30zem](requirements/NFR-30zem-observability-integration.md) - NFR-30zem Observability 統合 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-38940](adr/ADR-38940-security-resource-management.md)<br>[ADR-ckr1p](adr/ADR-ckr1p-skill-implementation-leveling.md) | [NFR-3gjla](requirements/NFR-3gjla-security-resource-governance.md) - NFR-3gjla セキュリティとリソース管理 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-ae6nw](adr/ADR-ae6nw-agenticscope-scenarios.md)<br>[ADR-mpiub](adr/ADR-mpiub-context-engineering-strategy.md) | [NFR-kc6k1](requirements/NFR-kc6k1-context-engineering-optimization.md) - NFR-kc6k1 Context Engineering 最適化 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-ij1ew](adr/ADR-ij1ew-observability-integration.md)<br>[ADR-lq67e](adr/ADR-lq67e-prompt-metrics-definition.md) | [NFR-mck7v](requirements/NFR-mck7v-iterative-metrics-evaluation.md) - NFR-mck7v 漸進的開発・評価サイクル支援 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | — | [NFR-mt1ve](requirements/NFR-mt1ve-error-handling-resilience.md) - NFR-mt1ve エラーハンドリングと堅牢性 | Draft | — |
| [AN-f545x](analysis/AN-f545x-claude-skills-agent.md) | [ADR-38940](adr/ADR-38940-security-resource-management.md) | [NFR-yiown](requirements/NFR-yiown-skill-verification-auditability.md) - NFR-yiown スキル検証・監査 | Draft | — |

### Requirement Dependencies

| Requirement | Depends On | Blocks | Blocked By |
| --- | --- | --- | --- |
| [FR-2ff4z](requirements/FR-2ff4z-multi-skill-composition.md) - FR-2ff4z 複数スキル連鎖実行 | [FR-cccz4](requirements/FR-cccz4-single-skill-complex-execution.md)<br>[FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md)<br>[FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md) | [FR-uu07e](requirements/FR-uu07e-progressive-disclosure.md) | [FR-cccz4](requirements/FR-cccz4-single-skill-complex-execution.md)<br>[FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md)<br>[FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md) |
| [FR-cccz4](requirements/FR-cccz4-single-skill-complex-execution.md) - FR-cccz4 単一スキルの複雑手続き実行 | [FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md)<br>[FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md) | [FR-2ff4z](requirements/FR-2ff4z-multi-skill-composition.md)<br>[FR-uu07e](requirements/FR-uu07e-progressive-disclosure.md) | [FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md)<br>[FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md) |
| [FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md) - FR-hjz63 プロンプト・エージェント可視化フレームワーク | — | [FR-2ff4z](requirements/FR-2ff4z-multi-skill-composition.md)<br>[FR-cccz4](requirements/FR-cccz4-single-skill-complex-execution.md)<br>[FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md)<br>[FR-uu07e](requirements/FR-uu07e-progressive-disclosure.md)<br>[NFR-30zem](requirements/NFR-30zem-observability-integration.md) | — |
| [FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md) - FR-mcncb 単一スキルの簡易実行 | [FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md) | [FR-2ff4z](requirements/FR-2ff4z-multi-skill-composition.md)<br>[FR-cccz4](requirements/FR-cccz4-single-skill-complex-execution.md)<br>[FR-uu07e](requirements/FR-uu07e-progressive-disclosure.md) | [FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md) |
| [FR-uu07e](requirements/FR-uu07e-progressive-disclosure.md) - FR-uu07e Progressive Disclosure 実装 | [FR-2ff4z](requirements/FR-2ff4z-multi-skill-composition.md)<br>[FR-cccz4](requirements/FR-cccz4-single-skill-complex-execution.md)<br>[FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md)<br>[FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md) | [NFR-kc6k1](requirements/NFR-kc6k1-context-engineering-optimization.md) | [FR-2ff4z](requirements/FR-2ff4z-multi-skill-composition.md)<br>[FR-cccz4](requirements/FR-cccz4-single-skill-complex-execution.md)<br>[FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md)<br>[FR-mcncb](requirements/FR-mcncb-single-skill-basic-execution.md) |
| [NFR-30zem](requirements/NFR-30zem-observability-integration.md) - NFR-30zem Observability 統合 | [FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md) | [NFR-mck7v](requirements/NFR-mck7v-iterative-metrics-evaluation.md) | [FR-hjz63](requirements/FR-hjz63-prompt-visibility-framework.md) |
| [NFR-3gjla](requirements/NFR-3gjla-security-resource-governance.md) - NFR-3gjla セキュリティとリソース管理 | — | [NFR-yiown](requirements/NFR-yiown-skill-verification-auditability.md) | — |
| [NFR-kc6k1](requirements/NFR-kc6k1-context-engineering-optimization.md) - NFR-kc6k1 Context Engineering 最適化 | [FR-uu07e](requirements/FR-uu07e-progressive-disclosure.md) | — | [FR-uu07e](requirements/FR-uu07e-progressive-disclosure.md) |
| [NFR-mck7v](requirements/NFR-mck7v-iterative-metrics-evaluation.md) - NFR-mck7v 漸進的開発・評価サイクル支援 | [NFR-30zem](requirements/NFR-30zem-observability-integration.md) | — | [NFR-30zem](requirements/NFR-30zem-observability-integration.md) |
| [NFR-mt1ve](requirements/NFR-mt1ve-error-handling-resilience.md) - NFR-mt1ve エラーハンドリングと堅牢性 | — | — | — |
| [NFR-yiown](requirements/NFR-yiown-skill-verification-auditability.md) - NFR-yiown スキル検証・監査 | [NFR-3gjla](requirements/NFR-3gjla-security-resource-governance.md) | — | [NFR-3gjla](requirements/NFR-3gjla-security-resource-governance.md) |

### Dependency Consistency

All prerequisite and dependent relationships are reciprocal with no contradictions or cycles detected.

## Traceability Gaps

No gaps detected.

_This file is generated by `scripts/trace-status.ts`. Do not commit generated outputs to avoid merge conflicts._