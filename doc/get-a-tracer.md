
### Get a tracer instance

First, you need an instance of OpenTelemetrySdk:

- get GlobalOpenTelemetry or DefaultOpenTelemetry
- if you want to configure:
    - either include autoconfigure artifact
        - link to spec of autoconfiguration
        - and then GlobalOpenTelemetry/get ...
    - either build manually the 'OpenTelemetrySdk' instance:
        - just build -> then keep the value floating
        - if you register global, then `core/tracer`
            - shall we memoize tracer instance or not ?

TODO: flowchart to explain the decisions ? could be way simpler than words (agent vs no agent, auto instrumentation vs
no autoinstrumentation, clj library author)

- About don't run twice instanciation: https://github.com/open-telemetry/opentelemetry-java/issues/3717
- https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/sdk.md#forceflush-1
- > Note that there is no need to "set" a tracer by name before getting it. The getTracer method always returns a handle to the same tracing client. The name you provide is to help identify which component generated which spans, and to potentially disable tracing for individual components. We recommend calling getTracer once per component during initialization and retaining a handle to the tracer, rather than calling getTracer repeatedly. This should be configured as early as possible in the entry point of your application. Keep in mind, this builder is not required if the agent is in use.
