- complete example
- links to Otel doc


### Cross-cutting concern

- note about OTEL being a cross-cutting
  concern https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#opentelemetry-client-architecture
- Constraint on ensuring that `Context.current()` is always correct in every part of the code execution path, to ensure
  correlation data is available and correct.


- [Introduction to OpenTelemetry](https://www.youtube.com/watch?v=_OXYCzwFd1Y)

semantic conventions: https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/trace/semantic_conventions

- https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/docs
- say these APIs are thread safe: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#concurrency

### Understand which ContextStorage provider is used

java agent:

- with -> see how this works
- without: default to ThreadLocalContextStorage
