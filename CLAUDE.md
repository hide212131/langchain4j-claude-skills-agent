# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**langchain4j-claude-skills-agent** is a LangChain4j-based runtime for executing Claude Skills with a Plan → Act → Reflect workflow. It reads SKILL.md manifests from the `skills/` directory, automatically selects and orchestrates skills through a multi-agent system, and generates artifacts using an autonomous act loop with budgets (tokens, time, tool calls).

The implementation uses LangChain4j's Agentic framework with OpenTelemetry for observability via LangFuse.

## Build & Run Commands

### Build
```bash
./gradlew build                 # Build the project
./gradlew :app:build           # Build only the app module
```

### Run
```bash
./gradlew setupSkillRuntimes   # Initialize Python/Node runtime environments
./sk run --goal "your goal" --skills-dir skills  # Run with real LLM
./sk run --goal "your goal" --dry-run             # Run with fake LLM (no API calls)
./sk run --goal "your goal" --debug-skill-ids brand-guidelines,document-skills/pptx  # Fixed skill execution order
```

### Tests
```bash
./gradlew test                 # Run all unit tests
./gradlew :app:test           # Run app module tests only
./gradlew test --tests "*Test" # Run tests matching pattern
```

### Utility Tasks
```bash
./gradlew updateSkills        # Download and extract skills from anthropics/skills repository
./gradlew :app:langfusePrompt -PtraceId=<id>  # Extract LLM prompts from Langfuse trace
```

## Architecture & Key Components

### High-Level Workflow
```
SkillsCliApp (entry point)
  ↓
RuntimeConfig (environment variables, budgets, model selection)
  ↓
AgentService (orchestrates Plan → Act → Reflect)
  ├─ PlannerAgent (Plan phase: analyzes goal, selects skills, creates constraints)
  ├─ InvokerAgent (Act phase: executes selected skills via SkillRuntime)
  └─ ReflectEvaluator (Reflect phase: validates outputs against criteria)
```

### Core Components

#### 1. **Workflow Orchestration** (`runtime.workflow.*`)
- **AgentService** ([AgentService.java](app/src/main/java/io/github/hide212131/langchain4j/claude/skills/runtime/workflow/AgentService.java)): Main orchestrator using LangChain4j's Agentic framework. Manages the three-phase workflow and maintains AgenticScope state.
- **PlannerAgent** (`plan/`): Analyzes user goal and generates skill candidates with expected outputs and evaluation criteria.
- **InvokerAgent** (`act/`): Executes skills through SkillRuntime and manages act-level budgets.
- **ReflectEvaluator** (`reflect/`): Validates final outputs against quality criteria.

#### 2. **Skill Runtime** (`runtime.skill.*`)
- **SkillRuntime** ([SkillRuntime.java](app/src/main/java/io/github/hide212131/langchain4j/claude/skills/runtime/skill/SkillRuntime.java)): Pure Act implementation for a single skill. Implements autonomous loop: Acquire → Decide → Apply → Record → Progress/Exit.
  - Uses Progressive Disclosure (L1/L2/L3) for SKILL.md content
  - Manages sub-agents (ReadSkillMdAgent, ReadRefAgent, RunScriptAgent, WriteArtifactAgent)
  - Enforces budgets: `max_tool_calls=24`, `token_budget=60000`, `time_budget=120000ms`, `script_timeout=20s`
- **SkillIndex** / **SkillIndexLoader**: Parses and indexes SKILL.md files from skills directory.
- **DryRunSkillRuntimeOrchestrator**: Provides fake execution for `--dry-run` mode.

#### 3. **LLM Integration** (`runtime.provider.*`)
- **LangChain4jLlmClient** ([LangChain4jLlmClient.java](app/src/main/java/io/github/hide212131/langchain4j/claude/skills/runtime/provider/LangChain4jLlmClient.java)): Adapter for OpenAI. Uses function calling for `invokeSkill` tool.
  - `OPENAI_MODEL_NAME`: Standard model for planning and tools
  - `OPENAI_HIGH_PERFORMANCE_MODEL_NAME`: Premium model for SkillActSupervisor and SemanticOutputsValidatorAgent
- **ProviderAdapter**: Unified interface for LLM selection.

#### 4. **State Management** (`runtime.blackboard.*`)
- **AgenticScope Bridge**: Uses LangChain4j's built-in AgenticScope for state sharing across workflow phases.
- **Shared State Classes**: PlanState, ActState, ReflectState with structured containers for goal, candidates, outputs, metrics.

#### 5. **Observability & Monitoring**
- **WorkflowTracer** ([WorkflowTracer.java](app/src/main/java/io/github/hide212131/langchain4j/claude/skills/infra/observability/WorkflowTracer.java)): OpenTelemetry integration for LangFuse.
- **ObservabilityChatModelListener**: Automatic instrumentation of LLM calls (prompts, responses, tokens, timing).
- **ExecutionTelemetryReporter**: Collects workflow metrics (tokens in/out, tool calls, timing).
- Configure via `LANGFUSE_*` environment variables (see OBSERVABILITY.md).

#### 6. **CLI & Configuration** (`app.cli.*`, `infra.config.*`)
- **SkillsCliApp** ([SkillsCliApp.java](app/src/main/java/io/github/hide212131/langchain4j/claude/skills/app/cli/SkillsCliApp.java)): Entry point using PicoCLI.
  - `--goal`: User request (required)
  - `--skills-dir`: Path to skills directory (default: `skills/`)
  - `--dry-run`: Use fake LLM
  - `--debug-skill-ids`: Force specific skill execution order
- **RuntimeConfig** ([RuntimeConfig.java](app/src/main/java/io/github/hide212131/langchain4j/claude/skills/infra/config/RuntimeConfig.java)): Loads and validates environment variables and budgets.

### Directory Structure
```
app/src/main/java/io/github/hide212131/langchain4j/claude/skills/
├── app/cli/                      # CLI entry point (SkillsCliApp)
├── runtime/
│   ├── workflow/                # Plan/Act/Reflect phases and AgentService
│   ├── skill/                   # SkillRuntime and skill orchestration
│   ├── provider/                # LLM adapters (OpenAI, etc.)
│   ├── blackboard/              # State containers for workflow phases
│   ├── context/                 # Context management and packing
│   ├── guard/                   # Skill invocation guards and validators
│   ├── human/                   # Human review agents
│   ├── observability/           # OpenTelemetry instrumentation
│   └── skill/agent/             # Sub-agents for SkillRuntime
├── infra/
│   ├── config/                  # RuntimeConfig and environment loading
│   ├── logging/                 # WorkflowLogger
│   └── observability/           # WorkflowTracer and telemetry
└── app/langfuse/                # Langfuse integration utilities
```

## Environment Variables

### Required
- `OPENAI_API_KEY`: OpenAI API key for real execution (not needed for `--dry-run`)
- `OPENAI_MODEL_NAME` (default: `gpt-5-mini`): Standard model for planning and tool calls
- `OPENAI_HIGH_PERFORMANCE_MODEL_NAME` (default: `OPENAI_MODEL_NAME`): Premium model for SkillActSupervisor and validators

### Optional
- `OPENAI_TIMEOUT_SECONDS` (default: 120): Timeout for OpenAI requests
- `LANGFUSE_*`: Observability endpoints and credentials (see OBSERVABILITY.md)

### Setup
1. Local development: Set in `.envrc` or `~/.zshrc`
2. CI: Use GitHub Secrets
3. Shared terminals: Use OS keychain (macOS Keychain, Windows Credential Manager)

## Development Workflow

### Initial Setup
```bash
./gradlew setupSkillRuntimes   # Create Python/Node virtual environments
source .envrc                  # Load environment variables (or use direnv)
./gradlew test                 # Verify tests pass
```

### Adding New Features
1. Follow package structure in `spec.md` (section 2.1)
2. Tests use JUnit 5 + AssertJ
3. For workflow phases, use LangChain4j's Agentic framework (`@Agent`, `@Tool`)
4. For observability, wrap in WorkflowTracer spans

### Testing
- Unit tests: `./gradlew test`
- Integration tests: Use `--dry-run` to test without API calls
- Observability debugging: Extract prompts via `./gradlew :app:langfusePrompt -PtraceId=<id>`

### Debugging
- VS Code: F5 with preconfigured launch configs (edit `args` for different goals/skills)
- .env file: Add `OPENAI_API_KEY=...` for debug environment
- Logs: WorkflowLogger provides structured logging with trace context

## Key Design Decisions

### Progressive Disclosure (L1/L2/L3)
- **L1 (always in context)**: SKILL.md frontmatter only (name, description)
- **L2 (on relevance)**: Full SKILL.md content (≤5k tokens)
- **L3 (on demand)**: Referenced files (scripts, templates) - only summaries/stdout in prompts

### Budget Management
- Act phase enforces strict budgets per skill: token_budget, time_budget, max_tool_calls, disk_write_limit
- SupervisorAgent tracks remaining budget across sub-agents
- Micro-Reflect triggers on stalled progress (2 consecutive no-op iterations)

### State Sharing via AgenticScope
- LangChain4j's AgenticScope passes state between workflow phases
- Reduces need for intermediate files or side channels
- Structured state objects (PlanState, ActState, etc.) ensure type safety

### Skills Directory (.gitignore)
- `skills/` is not tracked; populated via `./gradlew updateSkills`
- Pinned to specific commit (`defaultSkillsCommit` in root build.gradle.kts)
- `--skills-dir` flag allows testing with custom skill directories

## Common Development Tasks

### Run a Single Test
```bash
./gradlew test --tests "io.github.hide212131.langchain4j.claude.skills.runtime.workflow.AgentServiceTest"
```

### Extract LLM Prompts from Recent Trace
```bash
./gradlew :app:langfusePrompt  # Uses latest trace
./gradlew :app:langfusePrompt -PtraceId=<id>  # Specific trace
./gradlew :app:langfusePrompt -PtraceId=<id> -Pall=true  # All prompts
```

### Update Skills to New Commit
```bash
./gradlew updateSkills -PskillsCommit=<sha>
```

### Debug Specific Skill Execution
```bash
./sk run --goal "test goal" --debug-skill-ids brand-guidelines --dry-run
```

## Important References

- **Specification**: [docs/spec.md](docs/spec.md) - Full architecture and data models
- **SkillRuntime Spec**: [docs/spec_skillruntime.md](docs/spec_skillruntime.md) - Pure Act autonomous loop details
- **Setup Guide**: [docs/setup.md](docs/setup.md) - Development environment and model configuration
- **Observability**: [docs/OBSERVABILITY.md](docs/OBSERVABILITY.md) - LangFuse integration and telemetry
- **Testing**: [docs/testing.md](docs/testing.md) - Langfuse prompt extraction for validation
- **Claude Skills API**: https://docs.claude.com/en/api/skills-guide

## Known Constraints & Considerations

1. **Python/Node Runtimes**: Isolated in `env/python` and `env/node` directories. Use `./gradlew setupSkillRuntimes` to initialize.
2. **File Locks (Gradle)**: Tests may require elevated permissions in some environments (see docs/setup.md section 10).
3. **Skills Repository**: Must contain at least `brand-guidelines/` and `document-skills/pptx/` SKILL.md files for core demos.
4. **Agent Invocation Limits**: Configurable via `maxAgentsInvocations` in RuntimeConfig to prevent infinite loops.
5. **Dry Run Limitations**: Uses FakeLlmClient; outputs are synthetic and not suitable for production evaluation.
