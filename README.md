# opentelemetry-clojure

Clojure wrapper of [opentelemetry-java](https://opentelemetry.io/docs/java/).

Goals:
- Thin wrapper around java classes
- minimal overhead over the java implementation, since performance is a [stated goal](https://github.com/open-telemetry/community/blob/main/mission-vision-values.md#we-value-performance) by OpenTelemetry.
- TODO find upstream OpenTelemetry wording, but basically: say this wrapper can be used by library authors as the upstream java lib expects it. it does so by not including the SDK deps, only the API ones. meaning a clojure lib that instruments its code with opentelemetry-clojure will not forces its users to use opentelemetry at all.
  - OpenTelemetry separates `API` lib from `SDK` lib. Goal is to be able to use `API` code anywhere, but without any `SDK` in classpath it's almost noop.  Thus, libraries can instrument themselves, without forcing their consumers to use OpenTelemetry.

Non-goals:
- provide opinionated solution concerning in-process context propagation between threads, instead provide several solutions and document their caveats.
- TODO

## Usage

Run examples in a REPL:

```
clj -A:example
```

Jetty example:

1. `cd examples; docker-compose up`
2. `clj -A:example`

Run tests:

```
bin/kaocha
```

## Documentation (WIP, unstructured)

### Thread safety

- say these APIs are thread safe: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#concurrency
- TODO: leverage Clojure native immutable implementation where possible instead of having the cost to convert a Clojure objects to a (immutable) OTEL ones ?
  - Candidates which are immutable: Bagage, Attribute, SpanContexts, TraceState, Resource, Context ?
    - TODO: evaluate what it would mean to implement completely Context and Storage provider in Clojure ? could we expect some simplicity and eventually perf gain ?

### Cross-cutting concern

- note about OTEL being a cross-cutting concern https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#opentelemetry-client-architecture
- Constraint on ensuring that `Context.current()` is always correct in every part of the code execution path, to ensure correlation data is available and correct.

### Artifacts etc

- opentelemetry-java provides the otel API and the default SDK implementation, along with various plugins like exporters, span processors, etc, but no actual instrumentation of anything useful
- opentelemetry-java-instrumentation has 2 things. One: some “library” instrumentation which you can put on your classpath and just use as-is. Two: A full-featured javaagent that does auto-instrumentation via bytecode manipulation. (edited)
  - So, for example, if you use the okhttp “library” instrumentation, there’s a class that will wrap your OkHttpClient and generate spans and metrics using the otel APIs.
  - example: https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/okhttp/okhttp-3.0/library
  - 

### To use or not the auto-instrumentation agent

When using Agent, you get the following constraints / caveats:

- "direct usage of the OpenTelemetry SDK is not supported when running agent" TODO: source
- Span implementation when using autoinstrumentation java agent differs from the one from the SDK
  - TODO: ask about this on cncf slack, Span doesn't implement Context interface in agent but it does in api
- tracer is built by the agent, thus need to use auto configuration or ?
- Javaagent version vs SDK version, see this slack message:
  > We support all OTel APIs up to the version of the javaagent, but not necessarily newer. The assumption is it’s generally at least as easy to update the javaagent version as an app. And while not impossible for all cases, it’s hard to guarantee compatibility with something that doesn’t exist yet
- https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1926

How to leverage [supported libraries, frameworks etc](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md) without running the agent ?
- example: https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/okhttp/okhttp-3.0/library

### How to get a Tracer

- > Note that there is no need to "set" a tracer by name before getting it. The getTracer method always returns a handle to the same tracing client. The name you provide is to help identify which component generated which spans, and to potentially disable tracing for individual components.
  We recommend calling getTracer once per component during initialization and retaining a handle to the tracer, rather than calling getTracer repeatedly.
  This should be configured as early as possible in the entry point of your application. Keep in mind, this builder is not required if the agent is in use.
- explain when using agent or not

### Understand which ContextStorage provider is used

java agent: 
- with -> see how this works
- without: default to ThreadLocalContextStorage

### Concurrency primitives

#### future

Solution 1: implicitely convey the Context to the Executor
Downsides:
- can be intrusive, and it wraps Agent executor as well

Solution 2: wrap in a lexical scope

#### core.async

We have to differentiate 2 use cases:

1. We use a `go` block or a `thread`: wrap in a lexical scope
  - warning about go block and parking/resume on another thread -> macro to help maintain the correct thread local storage
2. We don't have it, need to convey value in channel: todo example of code with aleph for example

## Roadmap :

1. Wrapper for OTEL Tracing API and with unopinionated solutions for `Context` conveyance accross threads in usual Clojure stack:
  - future, core.async go and thread, core.async channels, manifold, CompletableFuture etc
3. Provide extensive examples and documentation with usual Clojure stack:
  - jetty, netty, logback config
5. Load test example app with correctness validation (convey trace id in the request body to assert down the execution path correct spans) + Profiling, etc 

## TODO :

- Tracing:
  - implement Tracer interface
    - possible to use Component but why ? we don't do that for logging or metrics usually, that's a global singleton
    - can't make SDK dependency "provided"
    - option 1: include auto instrumentation artifact and use global singleton https://www.javadoc.io/static/io.opentelemetry/opentelemetry-api/1.0.1/io/opentelemetry/api/GlobalOpenTelemetry.html
    - option 2: manually build it with OpenTelemetrySdk.builder (in SDK then, not API).
    - About don't run twice instanciation: https://github.com/open-telemetry/opentelemetry-java/issues/3717 >> GlobalOpenTelemetry vs javaagent ?
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/sdk.md#forceflush-1
  - Leverage manual instrumented libraries without the agent:
    - example: https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/okhttp/okhttp-3.0/library
    - it could be a good solution to avoid the agent pitfalls while leveraging instrumented libs like jetty, jdbc etc
  - implement all SpanBuilder interface:
    - Events
    - Links https://javadoc.io/static/io.opentelemetry/opentelemetry-api/1.7.0/io/opentelemetry/api/trace/SpanBuilder.html
    - Attributes
      - see how to build a simple version that leverage reflection, and another additional version that is more performant and will create less churn, since it will be in the hot path 
      - idea: custom clojure implementation that implements the protocol ?
      - javadoc -> It is strongly recommended to use setAttribute(AttributeKey, Object), and pre-allocate your keys, if possible.
      - best practices: https://opentelemetry.lightstep.com/best-practices/using-attributes/
  - ~~implement printable protocols for span, attributes, bagage~~
    - instead see https://clojure.github.io/clojure/clojure.datafy-api.html
  - Namespace with Stuart Sierra's component of the tracer
    - see graceful shutdown https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/sdk.md#shutdown
  - Review naming conventions
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/semantic_conventions/README.md
  - Logger configuration
    - https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/logback-1.0/library
    - https://opentelemetry.io/docs/java/manual_instrumentation/#logging-and-error-handling
    - https://lambdaisland.com/blog/2020-06-12-logging-in-clojure-making-sense-of-the-mess
    - https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/logger-mdc-instrumentation.md
      - it may be an argument to always manage correct Context/current since it could be used to correlate logs ?
      - http://logback.qos.ch/manual/mdc.html
    - https://github.com/newrelic/newrelic-opentelemetry-examples/tree/main/java/logs-in-context-log4j2
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/error-handling.md#java
  - reducers not supported: add in documentation + explore if possible to change that
  - Testing :
    - use this: "We provide a debug mechanism for context propagation, which can be enabled by
    setting {@code -Dio.opentelemetry.context.enableStrictContext=true} in your JVM args. This will
    enable a strict checker that makes sure that {@link Scope}s are closed on the correct thread and
    that they are not garbage collected before being closed. This is done with some relatively
    expensive stack trace walking. It is highly recommended to enable this in unit tests and staging
    environments, and you may consider enabling it in production if you have the CPU budget or have
    very strict requirements on context being propagated correctly (i.e., because you use context in
    a multi-tenant system)."
    - integration testing
      - validate with a javaagent, for example: Netty (autoinstrumented) -> Aleph (manual) -> App code (manual) -> JDBC (auto)
      - https://github.com/BrunoBonacci/mulog/issues/52
  - Performance:
    ~~- why `Context.wrap` doesn't close the `Scope` ?~~ 
    - get a Yourkit licence for open source project: https://www.yourkit.com/java/profiler/purchase/#os_license
    - warn on reflection
    - for some functions, if I provide an easy-to-use but with reflection, then I must also provide a more Java-ish version but without reflection (when perf is required) -> Span attributes
    ~~- with-bindings vs binding -> perf difference ? TODO: check with criterium~~
    - profile the code and try in https://github.com/bsless/stress-server project maybe, to have a good stress test ?
      - at least find a way to run a long time a stress test to validate there is no leaks -> it might be possible to test it even after a triggered GC event ?
    - try to make decision on implementation:
      - https://github.com/hugoduncan/criterium
      - https://github.com/jgpc42/jmh-clojure
    - could be interesting to check https://github.com/jetty-project/jetty-load-generator
    - would be interesting to have an example implementation e2e with dummy db backends and being able to load test it, to identity churn etc
    - see https://github.com/bsless/stress-server
  - clj linter ? https://github.com/clj-kondo/clj-kondo
  - Implement Bagage ~= attributes shared by spans
    - idea: implement custom version where all immutability things are removed since we are in clojure land already
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#baggage-signal
    - https://docs.honeycomb.io/getting-data-in/java/opentelemetry-distro/#multi-span-attributes
    - BaggageSpanProcessor: ?
  - Attributes limits: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/common.md#attribute-limits
    - explore this API and how it behaves
  - Documentation
    - cljdoc https://github.com/cljdoc/cljdoc/blob/master/doc/userguide/for-library-authors.adoc
    - Docs: https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/docs
    - [Introduction to OpenTelemetry](https://www.youtube.com/watch?v=_OXYCzwFd1Y)
    - should use the BOM but can't: https://clojure.atlassian.net/browse/TDEPS-202 ```clj -Stree '{:deps {io.opentelemetry/opentelemetry-bom {:mvn/version "1.7.0" :extension "pom"}}}'```
    - Propagator W3C: async and sync Ring middleware implementation
      - check semantic conventions
        - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md
        - https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/semantic-conventions.md
        - https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/trace/semantic_conventions
      - Note about: span per retry in http query https://neskazu.medium.com/thoughts-on-http-instrumentation-with-opentelemetry-9fc22fa35bc7 + So for the sake of consistency and to give users better observability, I believe each redirect should be a separate HTTP span.
      - middleware will be in hot path, really needs to be as performant as possible
    - auto configuration https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
- Metrics
  - start to explore the Java doc, even if its still alpha
  - conventions: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/README.md
  - Interesting Datadog issue with cumulative vs delta: https://cloud-native.slack.com/archives/C0150QF88FL/p1633960889159200
- Release as a clojure lib:
  - don't depend on latest clojure ? 
  - check for build, CI for open source
  - how to publish to clojars
  - see clojurians announcements and tracing channels to gather feedback + https://clojureverse.org/c/showcase/your-projects-and-libraries/35
- Bookmars:
  - Programmatic injection of agent https://cloud-native.slack.com/archives/C0150QF88FL/p1632249888088300?thread_ts=1631980158.059400&cid=C0150QF88FL

To ask on Clojurians:
- is it risky to wrap the core.async executor ?

To ask on CNCF Slack:
- Span implementation when using autoinstrumentation java agent differs from the one from the SDK
  - ask why in a github issue
  - (with-open [_ (-> (Context/current) (.with span) .makeCurrent)] versus makeCurrent in docs
- is it true to say, we want to maintain a valid `Context.current()` accross the entire code execution path ?