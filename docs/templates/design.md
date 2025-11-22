# T-<id> Component | Feature Title

## Metadata

- Type: Design
- Status: Draft | Approved | Rejected
  <!-- Draft: Work in progress | Approved: Ready for implementation | Rejected: Not moving forward with this design -->

## Links

<!-- Internal project artifacts only. Replace or remove bullets as appropriate. -->

- Associated Plan Document:
  - [T-<id>-<task>-plan](../tasks/T-<id>-<task>/plan.md)

## Overview

`[One-paragraph summary of the problem, motivation, and expected outcome.]`

## Success Metrics

- [ ] `[Measurable product/engineering impact]`
- [ ] `[Performance target (e.g., <X ms, <Y MB)]`
- [ ] `[Reliability target (e.g., zero regressions)]`

## Background and Current State

- Context: `[Where this fits in the agent; user workflows it affects]`
- Current behavior: `[What exists today; relevant modules/paths]`
- Pain points: `[Current issues/limitations]`
- Constraints: `[Time/tech/platform/compliance]`
- Related ADRs: `[/docs/adr/ADR-<id>-...md]`

## Proposed Design

### High-Level Architecture

```text
[ASCII diagram of components and data flows]
```

### Components

- `[Packages/classes/methods and responsibilities]`

### Data Flow

- `[Sequence of operations from input to output]`

### Storage Layout and Paths (if applicable)

- `[Config/data/cache locations relevant to this change]`
- `[File naming/rotation/versioning expectations]`

### API/Interface Design (if applicable)

Usage

```bash
[Endpoint or CLI] <command>|<method> `[options/body]`
```

- `[Describe key options/parameters/headers]`
- `[Show example requests/responses or CLI invocations]`

Implementation Notes

- `[Interfaces or abstractions to reuse]`
- `[Naming and configuration conventions to follow]`

### Data Models and Types

- `[Classes/enums/fields; serialization formats; version formats]`

### Error Handling

- Use descriptive exceptions or error responses with actionable, Japanese messages.
- Map exceptions to clear outcomes (HTTP status/return codes/log levels) and document them.
- `[Exit codes or error surfaces relevant to this change]`

### Security Considerations

- `[Authentication/authorization, secrets handling, validation, permissions]`

### Performance Considerations

- `[Hot paths; caching strategy; concurrency model; I/O considerations]`
- `[Baseline/target throughput or latency and how it will be measured]`

### Platform Considerations

#### Unix

- `[Paths/permissions/behavior]`

#### Windows

- `[Filesystem/path separators/permissions]`

#### Filesystem

- `[Case sensitivity; long paths; temp files]`

## Alternatives Considered

1. Alternative A
   - Pros: `[List]`
   - Cons: `[List]`
2. Alternative B
   - Pros: `[List]`
   - Cons: `[List]`

Decision Rationale

- `[Why chosen approach; trade-offs]`. Link/update ADR as needed.

## Migration and Compatibility

- Backward/forward compatibility: `[Behavior changes, flags, formats]`
- Rollout plan: `[Phased enablement, feature flags]`
- Deprecation plan: `[Old commands/flags removal timeline]`

## Testing Strategy

### Unit Tests

- Place tests under `src/test/java/`; cover happy paths and edge cases with JUnit (or the project's chosen test framework).

### Integration Tests

- Add scenarios that exercise boundaries (I/O, network, database) without over-mocking; use Testcontainers or similar when appropriate.

### External API Parsing (if applicable)

- Include at least one test with captured JSON (curl or equivalent) as an inline string or fixture and assert key fields using the project parser (e.g., Jackson).

### Performance & Benchmarks (if applicable)

- `[How to measure: JMH harness, representative load tests, or targeted benchmarks]`
- `[Targets/thresholds and where results are recorded]`

## Documentation Impact

- Update `docs/reference.md` or feature-specific docs for behavior changes.
- Update README/user docs for user-facing impacts.
- Add or update `/docs/adr/` entries for design decisions (rationale and alternatives).

## External References (optional)

<!-- External standards, specifications, articles, or documentation -->

- [External resource title](https://example.com) - Brief description

## Open Questions

- [ ] `[Question that needs investigation]`
- [ ] `[Decision that needs to be made]` → Next step: `[Where to resolve (e.g., update plan docs/tasks/T-<id>-<task>/plan.md per TDL)]`
- [ ] `[Information that needs gathering]` → Method: `[How to obtain insight]`

<!-- Complex investigations should spin out into their own ADR or analysis document -->

## Appendix

### Diagrams

```text
[Additional diagrams]
```

### Examples

```bash
# End-to-end example flows
```

### Glossary

- Term: `[Definition]`

---

## Template Usage

For detailed instructions on using this template, see [Template Usage Instructions](README.md#design-template-designmd) in the templates README.
