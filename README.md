# opentelemetry-clojure WIP

Status: abandonned unfinished, use [steffan-westcott/clj-otel](https://github.com/steffan-westcott/clj-otel) instead.

Clojure wrapper of [opentelemetry-java](https://opentelemetry.io/docs/java/).

Goals:
- Thin wrapper around java classes
- minimal overhead over the java implementation, since performance is a [stated goal](https://github.com/open-telemetry/community/blob/main/mission-vision-values.md#we-value-performance) by OpenTelemetry.
- TODO find upstream OpenTelemetry wording, but basically: say this wrapper can be used by library authors as the upstream java lib expects it. it does so by not including the SDK deps, only the API ones. meaning a clojure lib that instruments its code with opentelemetry-clojure will not forces its users to use opentelemetry at all.
  - OpenTelemetry separates `API` lib from `SDK` lib. Goal is to be able to use `API` code anywhere, but without any `SDK` in classpath it's almost noop.  Thus, libraries can instrument themselves, without forcing their consumers to use OpenTelemetry.
- Keep the original naming of things, like Context, Resource, Baggage, Span, etc.

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

### Span

- kind: list of possible kinds, if not good one fallback on internal, following Java implementation
- usage to build a spanbuilder and start it elsewhere



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

TODO: flowchart to explain the decisions ? could be way simpler than words (agent vs no agent, auto instrumentation vs no autoinstrumentation, clj library author) 

- About don't run twice instanciation: https://github.com/open-telemetry/opentelemetry-java/issues/3717
- https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/sdk.md#forceflush-1
- > Note that there is no need to "set" a tracer by name before getting it. The getTracer method always returns a handle to the same tracing client. The name you provide is to help identify which component generated which spans, and to potentially disable tracing for individual components.
  We recommend calling getTracer once per component during initialization and retaining a handle to the tracer, rather than calling getTracer repeatedly.
  This should be configured as early as possible in the entry point of your application. Keep in mind, this builder is not required if the agent is in use.

### Thread safety

- say these APIs are thread safe: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#concurrency
- TODO: leverage Clojure native immutable implementation where possible instead of having the cost to convert a Clojure objects to a (immutable) OTEL ones ?
  - Candidates which are immutable: Bagage, Attribute, SpanContexts, TraceState, Resource, Context ?
    - TODO: evaluate what it would mean to implement completely Context and Storage provider in Clojure ? could we expect some simplicity and eventually perf gain ?
    - Discarded solution: clojure dynamic scope with a ^dynamic Var + with-binding macro because it's [way slower than Java ThreadLocal API](https://tech.redplanetlabs.com/2020/09/02/clojure-faster/#dynamic-vars)

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
~~- Span implementation when using autoinstrumentation java agent differs from the one from the SDK
  - TODO: ask about this on cncf slack, Span doesn't implement Context interface in agent but it does in api~~
- tracer is built by the agent, thus need to use auto configuration or ?
- Javaagent version vs SDK version, see this slack message:
  > We support all OTel APIs up to the version of the javaagent, but not necessarily newer. The assumption is it’s generally at least as easy to update the javaagent version as an app. And while not impossible for all cases, it’s hard to guarantee compatibility with something that doesn’t exist yet
- https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1926

How to leverage [supported libraries, frameworks etc](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md) without running the agent ?
- example: https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/okhttp/okhttp-3.0/library

### Understand which ContextStorage provider is used

java agent: 
- with -> see how this works
- without: default to ThreadLocalContextStorage

### Attributes

OTEL documentation:
- best practices: https://opentelemetry.lightstep.com/best-practices/using-attributes/
- Recommendations for app devs: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/attribute-naming.md#recommendations-for-application-developers
- Naming: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/attribute-naming.md
- Spec: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/common.md#attributes
- Limits behavior + configuration: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/common.md#attribute-limits-configuration


- semconv artifact -> https://javadoc.io/doc/io.opentelemetry/opentelemetry-semconv/latest/io/opentelemetry/semconv/trace/attributes/SemanticAttributes.html
 -> resources attributes: https://javadoc.io/doc/io.opentelemetry/opentelemetry-semconv/latest/io/opentelemetry/semconv/resource/attributes/ResourceAttributes.html

- They can be attached to a Resource, Span, span Event, span Exception,  
- why typed ?
  > this type-safety is specifically to support tracing backends that have typed attributes/tags (like Jaeger). OTLP also has this built in to the model.
  source: https://cloud-native.slack.com/archives/C014L2KCTE3/p1634928183008800?thread_ts=1634889110.007100&cid=C014L2KCTE3

Implementation choice:
- Because of the Java class we have to implement, we have to declare the attribute's value type
- I evaluated the path of implementing in Clojure the interface, but I don't see the point since downstream (in exporters) we HAVE TO provide the `.getType` method which either will required either reflection or manual hint
- Valid types are: string, boolean, long, double, string array, boolean array, long array, double array
- I tested several hypothesis here that led to the first implementation: https://github.com/gabrielnau/opentelemetry-clojure/blob/722263bdea07bcce0e4ce3e46e97e7a5a574f4e2/src/opentelemetry_clj/attribute.clj
- TODO: refine the benchmark with a spec generator to see the use case where we hit [attributes limits](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/common.md#attribute-limits)  

### Resource

- https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/common/src/main/java/io/opentelemetry/sdk/resources/Resource.java
  - if service.name not set, "unknown_service:java"
  - doc and advise: https://opentelemetry.lightstep.com/go/tracing/
  - spec: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/sdk.md#detecting-resource-information-from-the-environment

### Baggage

- link to spec https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/baggage/api.md
- metadata field:
  > Metadata Optional metadata associated with the name-value pair. This should be an opaque wrapper for a string with no semantic meaning. Left opaque to allow for future functionality.

- choices:
  - there is fromContext which returns an empty baggage if none existing
  - there is current which  returns an empty baggage if none existing
  - there is fromcontextornull which returns nil or a baggage
  - -> we are more used to this, nil if value doesn't exist
  - -> so propose only this: from-context [] -> current or nul [context] -> fromcontextornull

Operations:
- Extract the Baggage from a Context instance
- Insert the Baggage to a Context instance


### Datafy 

- see `opentelemetry.datafy`
- needs to be required if you want to use it
- rely on opentelemetry-sdk as well, some API facades don't allow to introspect data

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
  - Review naming conventions
    - decide on how to name the builder functions which have a side effect (mutate span builder state)
  - Leverage manual instrumented libraries without the agent:
    - example: https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/okhttp/okhttp-3.0/library
    - it could be a good solution to avoid the agent pitfalls while leveraging instrumented libs like jetty, jdbc etc
  - Logger configuration
    - https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/logback-1.0/library
    - https://opentelemetry.io/docs/java/manual_instrumentation/#logging-and-error-handling
    - https://lambdaisland.com/blog/2020-06-12-logging-in-clojure-making-sense-of-the-mess
    - https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/logger-mdc-instrumentation.md
      - it may be an argument to always manage correct Context/current since it could be used to correlate logs ?
      - http://logback.qos.ch/manual/mdc.html
    - https://github.com/newrelic/newrelic-opentelemetry-examples/tree/main/java/logs-in-context-log4j2
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/error-handling.md#java
    - https://cloud-native.slack.com/archives/C0150QF88FL/p1635258042025900
  - Testing :
    - Span 
    - property based testing for e2e tests
    - integration testing
      - validate with a javaagent, for example: Netty (autoinstrumented) -> Aleph (manual) -> App code (manual) -> JDBC (auto)
      - https://github.com/javahippie/clj-test-containers
      - -Dio.opentelemetry.context.enableStrictContext=true
  - Performance:
    - https://clojure.org/reference/java_interop#optimization
    - profile the code and try in https://github.com/bsless/stress-server project maybe, to have a good stress test ?
      - at least find a way to run a long time a stress test to validate there is no leaks -> it might be possible to test it even after a triggered GC event ?
    - micro bench:
      - https://github.com/hugoduncan/criterium
      - https://github.com/jgpc42/jmh-clojure
    - could be interesting to check https://github.com/jetty-project/jetty-load-generator
    - would be interesting to have an example implementation e2e with dummy db backends and being able to load test it, to identity churn etc
    - see https://github.com/bsless/stress-server
    - might not be useful at all, but it can be interesting to use [no.disassemble](https://tech.redplanetlabs.com/2020/09/02/clojure-faster/#dramatic-gains-with-type-hinting)
    - Load testing:
      - configure reitit properly (see stress server results)
      - use BatchSpanProcessor !
  - clj linter https://github.com/clj-kondo/clj-kondo
  - Documentation
    - https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/semantic_conventions/README.md
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
    - supported underlying lib
    - document each version requiring which Sdk
    - code 2 gif is needed to explain context propagation: https://github.com/phronmophobic/clj-media
    - "context object follows execution path of your code"
    - clojure min version => 1.9.0 for clojure spec ? TODO: test with this version
    - reducers not supported: add in documentation + explore if possible to change that
  - review library guidelines https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/library-guidelines.md
  - Review all TODO
  - page 454 clojure cookbook: verifiying java interop with core.typed ? can it bring anything else than the warn on reflection ?
  - sort requires
- Baggage: implement a merge like function using `Baggage#toBuilder`.
- opentelemetry-clojure-component
  - see graceful shutdown https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/sdk.md#shutdown
- opentelemetry-clojure-integrant
- is semconv package easy to use ? if not, wrapper
- Metrics
  - start to explore the Java doc, even if its still alpha
  - conventions: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/README.md
  - Interesting Datadog issue with cumulative vs delta: https://cloud-native.slack.com/archives/C0150QF88FL/p1633960889159200
- Logs
  - https://cloud-native.slack.com/archives/C014L2KCTE3/p1635793311036800
- Release as a clojure lib:
  - review docstrings
  - don't depend on latest clojure ? 
  - check for build, CI for open source
  - how to publish to clojars
  - how to publish to maven central
  - https://keepachangelog.com/en/1.0.0/
  - see clojurians announcements and tracing channels to gather feedback + https://clojureverse.org/c/showcase/your-projects-and-libraries/35
  - versionning: clojuresque but see if we incr 0.X.0 each time we bump sdk dependency in order to allow for patches for older versions ?
  - get a Yourkit licence for open source project: https://www.yourkit.com/java/profiler/purchase/#os_license
- Bookmarks:
  - Programmatic injection of agent https://cloud-native.slack.com/archives/C0150QF88FL/p1632249888088300?thread_ts=1631980158.059400&cid=C0150QF88FL
- Ideas:
  - Span: option to validate options are consistent ? could be used during test / staging
  - Baggage: implement some Clojure interfaces (count, empty, merge), semantics are close to a map
  - Attributes: same
  - Implement a clojure Tap Exporter ?
