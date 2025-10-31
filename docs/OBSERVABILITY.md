# LangFuse Observability Integration

This project integrates LangFuse observability using LangChain4j's official OpenTelemetry support and OTLP (OpenTelemetry Protocol) gRPC exporter.

## Overview

The observability integration provides comprehensive tracing for:

1. **LLM Calls** - Automatic instrumentation of OpenAI model calls with:
   - Prompts (complete message content)
   - Responses (complete LLM output)
   - Token usage (input/output/total)
   - Execution time
   - Errors and exceptions

2. **Workflow Stages** - Manual tracing for Plan → Act → Reflect workflow:
   - **Plan Stage**: Goal, execution mode, selected skill IDs, skill count, attempt number
   - **Act Stage**: Executed skills, generated artifacts, invoked skills
   - **Reflect Stage**: Evaluation summary, retry advice, attempt number

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
- LangFuse OTLP endpoint configured

### Environment Variables

Configure observability using environment variables:

```bash
# Required: LangFuse OTLP endpoint
export LANGFUSE_OTLP_ENDPOINT=http://localhost:4317

# Optional: Service name for tracing (default: langchain4j-skills-agent)
export LANGFUSE_SERVICE_NAME=my-custom-service-name

# Required: OpenAI API key for LLM calls
export OPENAI_API_KEY=your-api-key
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

The default OTLP endpoint will be `http://localhost:4317`.

#### Access LangFuse UI

Once LangFuse is running, access the web UI at `http://localhost:3000` to view traces.

## Architecture

### Components

1. **ObservabilityConfig** (`io.github.hide212131.langchain4j.claude.skills.infra.observability.ObservabilityConfig`)
   - Configures OpenTelemetry SDK
   - Sets up OTLP gRPC exporter
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
├── workflow.plan (attempt 1)
│   ├── plan.completed (event)
│   └── [LLM call spans - auto-instrumented]
├── workflow.act (attempt 1)
│   ├── skill.execute (for each skill)
│   │   ├── tool.readSkillMd
│   │   ├── tool.readRef
│   │   ├── tool.runScript
│   │   └── tool.writeArtifact
│   └── act.completed (event)
├── workflow.reflect (attempt 1)
│   ├── reflect.completed (event)
│   └── [LLM call spans - auto-instrumented]
└── execution.completed (event)
    ├── execution.artifacts (event)
    └── [metrics summary]
```

## Trace Attributes

### Workflow Level

- `goal`: The user's goal/objective
- `dryRun`: Whether this is a dry-run execution
- `forcedSkillIds`: Comma-separated list of forced skill IDs
- `attempt`: Attempt number (for retries)
- `mode`: Execution mode (dry-run or live)

### Plan Stage

- `selectedSkills`: Comma-separated list of selected skill IDs
- `skillCount`: Number of skills in the plan
- `attempt`: Attempt number

### Act Stage

- `invokedSkills`: Comma-separated list of executed skills
- `hasArtifact`: Whether artifacts were generated
- `attempt`: Attempt number

### Reflect Stage

- `needsRetry`: Whether retry is needed
- `summary`: Evaluation summary
- `attempt`: Attempt number

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
export LANGFUSE_OTLP_ENDPOINT=http://localhost:4317
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
