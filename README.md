# opentelemetry-clojure

Clojure wrapper of [opentelemetry-java](https://opentelemetry.io/docs/java/).

Goals:
- thin wrapper around java classes
- minimal overhead over the java implementation, since performance is a [stated goal](https://github.com/open-telemetry/community/blob/main/mission-vision-values.md#we-value-performance) by OpenTelemetry.

Non-goals:
- TODO
- Provide an alternative in-process Context propagation mechanism than the thread-local one from opentelemetry-java 

## TODO:

- Tracing:
  - implement Tracer interface
    - About don't run twice instanciation: https://github.com/open-telemetry/opentelemetry-java/issues/3717 >> GlobalOpenTelemetry vs javaagent ? 
  - implement all SpanBuilder interface:
    - Events
    - Attributes
    - Links https://javadoc.io/static/io.opentelemetry/opentelemetry-api/1.7.0/io/opentelemetry/api/trace/SpanBuilder.html
  - Propagator W3C: async and sync Ring middleware implementation
    - check semantic conventions
      - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md
      - https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/semantic-conventions.md
      - https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/trace/semantic_conventions
    - Note about: span per retry in http query https://neskazu.medium.com/thoughts-on-http-instrumentation-with-opentelemetry-9fc22fa35bc7 + So for the sake of consistency and to give users better observability, I believe each redirect should be a separate HTTP span.
  - Namespace with Stuart Sierra's component of the tracer
    - see graceful shutdown https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/sdk.md#shutdown
  - Support auto configuration https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
  - See naming conventions
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/semantic_conventions/README.md
  - Logger configuration
    - https://opentelemetry.io/docs/java/manual_instrumentation/#logging-and-error-handling
    - https://github.com/newrelic/newrelic-opentelemetry-examples/tree/main/java/logs-in-context-log4j2
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/error-handling.md#java
  - Debug issue when instrumentation javaagent is running alonside
    - this simple code doesn't work with javaagent: https://gist.github.com/gabrielnau/2c0f8eb09d801c8e911bfa5a9334db0a
    - context storage is in the agent not in the thread local
    - https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1926
    - javaagent preempting the context storage, it might break our thread local implementation ?
  - reducers not supported: add in documentation + explore if possible to change that
  - Test this debug mechanism:
    > We provide a debug mechanism for context propagation, which can be enabled by
    setting {@code -Dio.opentelemetry.context.enableStrictContext=true} in your JVM args. This will
    enable a strict checker that makes sure that {@link Scope}s are closed on the correct thread and
    that they are not garbage collected before being closed. This is done with some relatively
    expensive stack trace walking. It is highly recommended to enable this in unit tests and staging
    environments, and you may consider enabling it in production if you have the CPU budget or have
    very strict requirements on context being propagated correctly (i.e., because you use context in
    a multi-tenant system).
  - Performance:
    - get a Yourkit licence for open source project ?
    - warn on reflection
    - for some functions, if I provide an easy-to-use but with reflection, then I must also provide a more Java-ish version but without reflection (when perf is required) -> Span attributes
    - with-bindings vs binding -> perf difference ? TODO: check with criterium
    - profile the code and try in https://github.com/bsless/stress-server project maybe, to have a good stress test ?
      - at least find a way to run a long time a stress test to validate there is no leaks -> it might be possible to test it even after a triggered GC event ?
  - core.async to re-read:
    - https://cognitect.com/blog/2016/9/15/works-on-my-machine-understanding-var-bindings-and-roots
    - https://clojureverse.org/t/binding-in-the-context-of-future-vs-thread/3718
    - https://stuartsierra.com/2013/03/29/perils-of-dynamic-scope
    - https://archive.clojure.org/design-wiki/display/design/Improve%2BBinding.html
    - About using binding inside a go block:
      - https://github.com/twosigma/waiter/issues/204
        - related JIRA ticket: https://clojure.atlassian.net/browse/ASYNC-170 -> seems resolved
      - https://ask.clojure.org/index.php/307/dynamic-binding-parking-removes-dynamic-bindings-outside
      - TODO test nested bindings inside a go block
      - https://clojure.atlassian.net/browse/ASYNC-94
  - deps:
    - remove sdk ? `Libraries that want to export telemetry data using OpenTelemetry MUST only depend on the opentelemetry-api package and should never configure or depend on the OpenTelemetry SDK. The SDK configuration must be provided by Applications which should also depend on the opentelemetry-sdk package, or any other implementation of the OpenTelemetry API. This way, libraries will obtain a real implementation only if the user application is configured for it. For more details, check out the Library Guidelines.`
    - should use the BOM but can't: https://clojure.atlassian.net/browse/TDEPS-202 ```clj -Stree '{:deps {io.opentelemetry/opentelemetry-bom {:mvn/version "1.7.0" :extension "pom"}}}'```
  - Implement Bagage ~= attributes shared by spans
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#baggage-signal
    - https://docs.honeycomb.io/getting-data-in/java/opentelemetry-distro/#multi-span-attributes
    - BaggageSpanProcessor: ?
  - Documentation
    - Docs: https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/docs
    - [Introduction to OpenTelemetry](https://www.youtube.com/watch?v=_OXYCzwFd1Y)
    - > Note that there is no need to "set" a tracer by name before getting it. The getTracer method always returns a handle to the same tracing client. The name you provide is to help identify which component generated which spans, and to potentially disable tracing for individual components.
      We recommend calling getTracer once per component during initialization and retaining a handle to the tracer, rather than calling getTracer repeatedly.
      This should be configured as early as possible in the entry point of your application. Keep in mind, this builder is not required if the agent is in use.
- Metrics
  - start to explore the Java doc, even if its still alpha
  - conventions: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/README.md
- Release as a clojure lib:
  - check for build, CI for open source
  - how to publish to clojars
  - see clojurians announcements and tracing channels to gather feedback

To ask on Clojurians:
- what is the cost of reading / writing a thread local ?
- is it risky to wrap the core.async executor ?
