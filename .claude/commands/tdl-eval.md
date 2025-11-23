# /tdl-eval

Evaluate TDL artifact quality and provide Kopi project pattern insights.

## Purpose

- **Quick Q&A**: Ask "How does Kopi handle [selected text]?"
- **Full Assessment**: Evaluate entire document (Granularity, Clarity, Completeness, Traceability)
- **Pattern Reference**: Answer "What makes a good ADR?" type questions

## Usage

### Mode 1: Document Evaluation
```
/tdl-eval docs/analysis/AN-f545x-claude-skills-agent.md
```
Full assessment across 4 dimensions with overall score and recommendations.

### Mode 2: Selection-Based Question
```
/tdl-eval "Your selected text here" "What's the Kopi pattern?"
```
When you select a section, ask about Kopi practices and get comparison.

**Examples**:
```
/tdl-eval "Executive Summary" "How does Kopi structure this?"
/tdl-eval "Risk Assessment" "Should we add Mitigation Strategy?"
/tdl-eval "Gap Analysis (6 items)" "Is this count OK?"
```

### Mode 3: Pattern/Benchmark Questions
```
/tdl-eval "What's a typical gap count?"
/tdl-eval "How many open questions should we have?"
/tdl-eval "What makes a good ADR?"
/tdl-eval "How does Kopi handle Stakeholder Analysis?"
```

## Kopi Benchmarks

| Metric | Kopi Typical | Good Target | ⚠️ Warning |
|---|---|---|---|
| **Analysis gaps** | 4-5 | ≤6 | >8 split into separate analyses |
| **Open questions** | 3-4 | ≤8 | >10 too many; move to recommendations |
| **Requirements/analysis** | 1-2 | ≤3 | >10 consider phasing |
| **ADR alternatives** | 3+ | 2-3+ | 1 only = insufficient |
| **Requirement acceptance criteria** | 100% | ≥80% | <50% incomplete |

## Evaluation Dimensions

### Granularity
- **Analysis**: Gap count (Kopi 4-5 optimal; ≤6 acceptable)
- **Requirements**: Total FR+NFR (Kopi 1-2 per analysis; ≤10 total typical)
- **ADR**: Single decision per document (multiple alternatives required)

### Clarity
- Multiple alternatives presented (3+ for ADR)
- Pros/Cons explicitly stated
- Recommendation rationale documented
- Open questions at completion (Kopi 3-4)

### Completeness
- **Analysis**: Problem Space, Stakeholder Analysis, Risk Assessment, Recommendations, Discovered Requirements
- **Requirement**: Acceptance Criteria, Rationale, Links
- **ADR**: Decision, Context, Alternatives, Pros/Cons, Consequences

### Traceability
- Analysis → Requirements (AN → FR-DRAFT)
- Requirements → ADR (FR → ADR-xxxx)
- ADR → Tasks (ADR → T-xxxx)
- Continuous chain: AN → FR → ADR → Task → Code

## Common Patterns

### When to Split Analysis
- Gap count > 6
- Open questions > 10
- Multiple independent decision domains
- **Kopi approach**: 1 analysis = 1 decision area

### When to Phase Requirements
- Total FR+NFR > 10
- Complex dependencies
- Staged implementation possible
- **Kopi approach**: Phase by priority/delivery

### When to Enhance ADR
- Only 1-2 alternatives shown
- Tradeoffs not explicit
- Consequences unclear
- **Kopi approach**: Always 3+ alternatives with explicit comparison

## Examples: Kopi Reference

**Analyses**: https://github.com/kopi-vm/kopi/tree/main/docs/analysis
- AN-i9cma (libc-to-nix): Dependency replacement evaluation
- AN-l19pi (fs2-retire): Successful elimination (~4 gaps, 2 open questions)
- AN-m9efc (proc-locking): Cross-process synchronization (~5 gaps)

**Requirements**: https://github.com/kopi-vm/kopi/tree/main/docs/requirements
- Concrete FR/NFR examples with full acceptance criteria

**ADRs**: https://github.com/kopi-vm/kopi/tree/main/docs/adr
- Decision patterns with 3+ alternatives evaluated each

## Quick Workflow

```
1. Select section or open document
2. Ask /tdl-eval "question"
3. Get Kopi pattern + assessment
4. Iterate until satisfied
```

## Tips

- **Selection queries**: Select text → ask specific question = best results
- **Document evaluation**: Use when draft complete (assess overall quality)
- **Pattern learning**: Ask "How does Kopi...?" for reference examples
- **Iterate**: Expect 2-3 improvement cycles before final approval

## Response Format

When responding to `/tdl-eval` queries:

1. **Always include reference URLs** to Kopi project examples (with GitHub line anchor links where applicable)
2. **Provide specific line numbers** or section anchors for easy navigation
3. **Show before/after examples** from Kopi for comparison
4. **Include direct GitHub URLs** formatted as:
   ```
   https://github.com/kopi-vm/kopi/blob/main/docs/analysis/AN-xxxx.md#section-name
   https://github.com/kopi-vm/kopi/blob/main/docs/analysis/AN-xxxx.md#L42-L65
   ```

**Example Response Structure**:
```
Your text: [assessment]

Kopi pattern (Reference):
- Document: https://github.com/kopi-vm/kopi/blob/main/docs/analysis/AN-l19pi-fs2-dependency-retirement.md
- Section: User Feedback (#L50-L65)
- Pattern: [specific example with line references]

Your comparison:
- Gap: [what's missing/different]
- Recommendation: [concrete action]

Example code/section:
https://github.com/kopi-vm/kopi/blob/main/docs/analysis/AN-m9efc-concurrent-process-locking.md#L120-L145
```

## See Also

- Templates: `docs/templates/`
- Kopi project: https://github.com/kopi-vm/kopi
- TDL overview: `docs/tdl.md`

