# LangFuse Observability Integration

This project integrates LangFuse observability using OpenTelemetry OTLP HTTP exporter with Basic authentication.

## Overview

The observability integration provides comprehensive tracing for:

1. **LLM Calls** - Automatic instrumentation of OpenAI model calls with:
   - Prompts (complete message content)
   - Responses (complete LLM output)
   - Token usage (input/output/total)
   - Execution time
   - Errors and exceptions

2. **Workflow Stages** - Manual tracing for Plan → Act workflow:
   - **Plan Stage**: Goal, execution mode, selected skill IDs, skill count
   - **Act Stage**: Executed skills, generated artifacts, invoked skills

3. **Skill Runtime** - Internal processing traces:
   - Skill execution details (ID, name, description)
   - Artifact information
   - Referenced file count
   - Tool invocation count
   - Child skill calls
   - Validation results

4. **AgentScope State** - Visualization of agent state changes throughout execution

## Setup

### Prerequisites

- Java 21
- Access to a LangFuse instance (local or cloud)
- LangFuse OTLP HTTP endpoint configured

### Environment Variables

Configure observability using environment variables:

```bash
# Required: LangFuse OTLP HTTP endpoint (e.g., http://localhost:3000/api/public/otel)
export LANGFUSE_OTLP_ENDPOINT=http://localhost:3000/api/public/otel

# Required: LangFuse public key for Basic authentication
export LANGFUSE_OTLP_USERNAME=pk-lf-...

# Required: LangFuse secret key for Basic authentication
export LANGFUSE_OTLP_PASSWORD=sk-lf-...

# Optional: Service name for tracing (default: langchain4j-skills-agent)
export LANGFUSE_SERVICE_NAME=my-custom-service-name

# Required: OpenAI API key for LLM calls
export OPENAI_API_KEY=your-api-key

# Optional: Override model names
export OPENAI_MODEL_NAME=gpt-5-mini
export OPENAI_HIGH_PERFORMANCE_MODEL_NAME=gpt-5.1
```

### LangFuse Setup

#### Using Docker Compose

```bash
# Clone LangFuse repository
git clone https://github.com/langfuse/langfuse.git
cd langfuse

# Start LangFuse with Docker Compose
docker-compose up -d
```

The OTLP HTTP endpoint will be available at `http://localhost:3000/api/public/otel`.

#### Access LangFuse UI

Once LangFuse is running, access the web UI at `http://localhost:3000` to view traces.

## Architecture

### Components

1. **ObservabilityConfig** (`io.github.hide212131.langchain4j.claude.skills.infra.observability.ObservabilityConfig`)
   - Configures OpenTelemetry SDK
   - Sets up OTLP HTTP exporter with Basic authentication
   - Enables GenAI semantic conventions
   - Manages tracer lifecycle
   - Provides global OpenTelemetry instance

2. **WorkflowTracer** (`io.github.hide212131.langchain4j.claude.skills.infra.observability.WorkflowTracer`)
   - Helper for manual span creation
   - Supports workflow-level tracing
   - Adds events and attributes to spans
   - Handles enabled/disabled state gracefully

3. **LangChain4j Integration**
   - Uses LangChain4j's built-in OpenTelemetry support
   - Automatic instrumentation of LLM calls
   - Propagates trace context across async operations

### Trace Hierarchy

```
agent.execution (root span)
├── workflow.plan
│   ├── plan.completed (event)
│   └── [LLM call spans - auto-instrumented]
├── workflow.act
│   ├── skill.execute (for each skill)
│   │   ├── tool.readSkillMd
│   │   ├── tool.readRef
│   │   ├── tool.runScript
│   │   └── tool.writeArtifact
│   └── act.completed (event)
└── execution.completed (event)
    ├── execution.artifacts (event)
    └── [metrics summary]
```

## Trace Attributes

### Workflow Level

- `goal`: The user's goal/objective
- `dryRun`: Whether this is a dry-run execution
- `forcedSkillIds`: Comma-separated list of forced skill IDs
- `mode`: Execution mode (dry-run or live)

### Plan Stage

- `selectedSkills`: Comma-separated list of selected skill IDs
- `skillCount`: Number of skills in the plan

### Act Stage

- `invokedSkills`: Comma-separated list of executed skills
- `hasArtifact`: Whether artifacts were generated

### AgentScope Snapshots

- `agentic.scope.input` / `agentic.scope.output`: Emitted on every Plan → Act span with a JSON dump (max ~16 KB) of the LangChain4j `AgenticScope` immediately before and after the stage executes. Attributes include `stage` and `phase` so LangFuse timelines show the full before/after contract state.
- `agentic.scope.error`: Emitted when a stage fails and captures the last known scope snapshot to help debug missing keys or malformed data.
- `skill.agentic.scope`: Published by the Pure Act `SkillRuntime` supervisor to expose the complete supervisor scope for standalone skill executions.
- `llm.chat` spans also include `agentic.scope.input` / `agentic.scope.output` / `agentic.scope.error` attributes so you can correlate every prompt/response with the exact AgenticScope payload that was in effect at call time.

Each snapshot truncates very long strings/collections so OTLP payloads stay bounded while still surfacing every key stored in `AgenticScope`.

### Execution Summary

- `stageVisits`: Total number of stage visits
- `llmCalls`: Total LLM API calls
- `totalTokens`: Total tokens used
- `inputTokens`: Input tokens
- `outputTokens`: Output tokens
- `totalDurationMs`: Total execution time in milliseconds
- `artifactPath`: Path to generated artifact (if any)

## Usage

### Basic Usage

The observability integration is automatic when environment variables are set:

```bash
# Set environment variables
export LANGFUSE_OTLP_ENDPOINT=http://localhost:3000/api/public/otel
export LANGFUSE_OTLP_USERNAME=pk-lf-your-public-key
export LANGFUSE_OTLP_PASSWORD=sk-lf-your-secret-key
export OPENAI_API_KEY=your-api-key

# Run the application
./gradlew run --args="--goal 'Create a presentation about AI'"
```

### Viewing Traces in LangFuse

1. Open LangFuse UI at `http://localhost:3000`
2. Navigate to the "Traces" section
3. Find traces with service name `langchain4j-skills-agent`
4. Click on a trace to view detailed span hierarchy
5. Inspect span attributes, events, and timing information

### Programmatic Usage

```java
// Observability is configured automatically via environment variables
ObservabilityConfig observability = ObservabilityConfig.fromEnvironment();

// Create LLM client with observability
LangChain4jLlmClient llmClient = LangChain4jLlmClient.forOpenAi(
    System::getenv
);

// Create workflow tracer
WorkflowTracer tracer = new WorkflowTracer(
    observability.tracer(), 
    observability.isEnabled()
);

// Use tracer in your code
tracer.trace("custom.operation", Map.of(
    "key", "value"
), () -> {
    // Your code here
});
```

## Disabling Observability

To disable observability, simply don't set the `LANGFUSE_OTLP_ENDPOINT` environment variable:

```bash
unset LANGFUSE_OTLP_ENDPOINT
./gradlew run --args="--goal 'Create a presentation'"
```

When disabled, the observability infrastructure uses no-op implementations with zero overhead.

## Troubleshooting

### Traces not appearing in LangFuse

1. Verify LangFuse is running: `docker ps | grep langfuse`
2. Check endpoint connectivity: `curl http://localhost:4317`
3. Verify environment variable: `echo $LANGFUSE_OTLP_ENDPOINT`
4. Check application logs for OpenTelemetry errors

### Performance Impact

- When disabled (no endpoint configured): Zero overhead, uses no-op implementations
- When enabled: Minimal overhead (~1-5% typical workloads)
- Batch span processing minimizes export impact
- 30-second timeout for span exports

### Common Issues

**Issue**: Spans are delayed or missing
- **Solution**: Check batch span processor settings, flush on shutdown

**Issue**: Connection refused to OTLP endpoint
- **Solution**: Verify LangFuse is running and accessible on port 4317

**Issue**: No LLM call traces
- **Solution**: LangChain4j automatic instrumentation requires global OpenTelemetry setup, which is done in ObservabilityConfig

## Dependencies

The integration uses these dependencies:

```kotlin
implementation(platform("io.opentelemetry:opentelemetry-bom:1.44.1"))
implementation("io.opentelemetry:opentelemetry-api")
implementation("io.opentelemetry:opentelemetry-sdk")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.28.0-alpha")
```

LangChain4j 1.7.1 is used with its built-in observability support.

## Best Practices

1. **Use descriptive span names**: Name spans clearly to make traces easy to understand
2. **Add relevant attributes**: Include context that helps debug issues
3. **Don't over-trace**: Balance detail with performance
4. **Use events for milestones**: Add events at key points in execution
5. **Test with observability disabled**: Ensure no-op mode works correctly
6. **Monitor trace volume**: Large traces can impact LangFuse performance

## References

- [LangChain4j Observability Documentation](https://docs.langchain4j.dev/tutorials/observability/)
- [LangFuse Documentation](https://langfuse.com/docs)
- [OpenTelemetry Java SDK](https://opentelemetry.io/docs/instrumentation/java/)
- [OTLP Specification](https://opentelemetry.io/docs/specs/otlp/)
