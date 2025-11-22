# T-<id> Feature | Task Name

## Metadata

- Type: Implementation Plan
- Status: Draft | Phase X In Progress | Cancelled | Complete
  <!-- Draft: Planning complete, awaiting start | Phase X In Progress: Actively working | Cancelled: Work intentionally halted before completion | Complete: All phases done and verified -->

## Links

<!-- Internal project artifacts only. Replace or remove bullets as appropriate. -->

- Associated Design Document:
  - [T-<id>-<task>-design](../tasks/T-<id>-<task>/design.md)

## Overview

`[Brief description of the feature/task and its purpose]`

## Success Metrics

- [ ] `[Measurable success criterion]`
- [ ] `[Performance target if applicable]`
- [ ] `[User experience improvement]`
- [ ] All existing tests pass; no regressions in `[area]`

## Scope

- Goal: `[Outcome to achieve]`
- Non-Goals: `[Explicitly out of scope]`
- Assumptions: `[Operational/technical assumptions]`
- Constraints: `[Time/tech/platform/compliance]`

## ADR & Legacy Alignment

- [ ] Confirm the latest ADRs/design documents that govern this work are referenced above (update `Related ADRs` if needed).
- [ ] Note any known gaps between existing code/dependencies and the approved approach; add explicit subtasks in the phase checklists to retire or migrate those legacy patterns.

## Plan Summary

- Phase 1 – `[Short label describing the primary focus (e.g., Foundation scaffolding, API surface, CLI polish)]`
- Phase 2 – `[Short label describing the next major milestone]`
- Phase 3 – `[Short label describing the final hardening/cleanup]`
  - `[Add additional phases or remove unused lines as appropriate]`

### Phase Status Tracking

Mark checkboxes (`[x]`) immediately after completing each task or subtask. If an item is intentionally skipped or deferred, annotate it (e.g., strike-through with a brief note) instead of leaving it unchecked.

---

## Phase 1: `[Core Component/Foundation]`

### Goal

- `[What this phase aims to achieve]`

### Inputs

- Documentation:
  - `/docs/...` – `[Purpose]`
- Source Code to Modify:
  - `/src/...` – `[Purpose]`
  - `/src/...` – `[Purpose]`
- Dependencies:
  - Internal: `src/[module]/` – `[Description]`
  - External: `[dependency]` – `[Purpose]`

### Tasks

- [ ] **`[Task group]`**
  - [ ] `[Specific subtask]`
  - [ ] `[Specific subtask]`
- [ ] **`[Task group]`**
  - [ ] `[Specific subtask]`
  - [ ] `[Specific subtask]`

### Deliverables

- `[Artifacts/changes produced]`

### Verification

```bash
# Build and checks
./gradlew check
# Focused unit tests (module-specific)
./gradlew test --tests "<suite or package>"
```

### Acceptance Criteria (Phase Gate)

- `[Observable, testable criteria required to exit this phase]`

### Rollback/Fallback

- `[How to revert; alternative approach if needed]`

---

## Phase 2: `[Next Component]`

### Phase 2 Goal

- `[What this phase aims to achieve]`

### Phase 2 Inputs

- Dependencies:
  - Phase 1: `[Dependency description]`
  - `[Other dependencies]`
- Source Code to Modify:
  - `/src/...` – `[Purpose]`

### Phase 2 Tasks

- [ ] **`[Task group]`**
  - [ ] `[Specific subtask]`
  - [ ] `[Specific subtask]`

### Phase 2 Deliverables

- `[Artifacts/changes produced]`

### Phase 2 Verification

```bash
./gradlew check
./gradlew test --tests "<suite or package>"
# Optional: broader runs
./gradlew test
```

### Phase 2 Acceptance Criteria

- `[Observable, testable criteria required to exit this phase]`

### Phase 2 Rollback/Fallback

- `[How to revert; alternative approach if needed]`

---

## Phase 3: Testing & Integration

### Phase 3 Goal

- Create comprehensive tests and validate integration boundaries.

### Phase 3 Tasks

- [ ] Test utilities
  - [ ] `[Helper functions]`
  - [ ] `[Fixtures/mocks as needed]`
- [ ] Scenarios
  - [ ] Happy path
  - [ ] Error handling
  - [ ] Edge cases
- [ ] Concurrency & cleanup
  - [ ] Boundary conditions
  - [ ] Concurrent operations
  - [ ] Resource cleanup

### Phase 3 Deliverables

- Comprehensive automated tests for new behavior
- Documented known limitations and follow-ups (if any)

### Phase 3 Verification

```bash
./gradlew check
# Full test suite (consider runtime)
./gradlew test
```

### Phase 3 Acceptance Criteria

- `[Coverage of critical paths; green on unit + integration runs]`

---

## Definition of Done

- [ ] `./gradlew check` (or module equivalent)
- [ ] `./gradlew test`
- [ ] Integration/perf/bench (as applicable): `[document the command]`
- [ ] `docs/reference.md` updated; user docs updated if user-facing
- [ ] ADRs added/updated for design decisions
- [ ] Error messages actionable and in Japanese
- [ ] Platform verification completed (if platform-touching)
- [ ] Avoid vague naming (no "manager"/"util"); avoid unchecked reflection shortcuts

## Open Questions

- [ ] `[Question that needs investigation]`
- [ ] `[Decision that needs to be made]` → Next step: `[Where to resolve (e.g., coordinate downstream task docs/tasks/T-<id>-<task>/README.md, update requirements docs/requirements/FR-<id>-<capability>.md per TDL)]`
- [ ] `[Information that needs gathering]` → Method: `[How to obtain insight]`

<!-- Complex investigations should spin out into their own ADR or analysis document -->

---

## Template Usage

For detailed instructions on using this template, see [Template Usage Instructions](README.md#plan-template-planmd) in the templates README.
