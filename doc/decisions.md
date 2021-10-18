# Decisions

## Context propagation

Definitions:

> A **Context** is a propagation mechanism which carries execution-scoped values across API boundaries and between logically associated execution units. Cross-cutting concerns access their data in-process using the same shared Context object. ([src](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/context/context.md))

Citing [Lightstep documentation](https://opentelemetry.lightstep.com/core-concepts/context-propagation/):

> There is in-process context propagation, and inter-process context propagation. In-process propagation can be done explicitly, or implicitly.
All languages will have an option to manage context explicitly and it is required to some extent for edge case scenarios.
Many languages will have implicit in-process context propagation, which frees programmers from having to explicitly pass context around.
It is typically implemented using a mechanism such as thread local storage. Explicit context propagation, however, requires a programmer to take a context parameter and explicitly pass the context to an API.

For *inter-process* propagation, we can leverage [OpenTelemetry text-based](https://opentelemetry.io/docs/java/manual_instrumentation/#context-propagation) approach in Ring middleware, etc. There is no identified blocker here. 
For *in-process* context propagation, as stated in [opentelemetry-java doc](https://github.com/open-telemetry/opentelemetry-java/blob/main/context/src/main/java/io/opentelemetry/context/Context.java#L67):
> it is not trivial, and when done incorrectly can lead to broken traces or even mixed traces    

Default behavior works well when code path is executed on a single thread, but it is up to the library's user to propagate `Context` across threads: 

> The automatic context propagation is done using io.opentelemetry.context.Context which is a gRPC independent implementation for in-process Context propagation mechanism which can carry scoped-values across API boundaries and between threads. Users of the library must propagate the io.opentelemetry.context.Context between different threads.

**Problem statement:**

How to wrap opentelemetry-java `Context` propagation across threads in Clojure in order to support its concurrency primitives, like `core/future` or `core.async` ?

### Chosen solutions

#### Solution 1: Clojure ["binding conveyance"](https://clojure.org/reference/vars#conveyance)

Documentation is outdated, some concurrency functions (futures, agents) provide this, but there is also `pmap`, as well as `core.async` macros:
- `core.async/go`
- `core.async/go-loop`
- `core.async/thread`
- `core.async/thread-call`

So we can use Clojure's dynamic binding to "propagate" the `Context` value from one thread to another destination thread (in which the async computation occurs).
To do so, we have to:

1. Declare a Var: `(def ^:dynamic *context*)`
2. Bind it to the current `Context` we want to propagate: `(with-bindings {#'*context* (Context/current)})`
3. Access it from the another thread: `*context*`

Example:

```clojure
(def ^:dynamic *context*)
(println (Context/current))

(with-bindings {#'*context* (Context/current)}
   (go
     (println *current-context*)))
;; => both printed contexts are the same 
```

Once we obtain the `Context` instance in the destination thread, we have to *make it current* in `Context` API term, that will store it on a thread local and it will returns an instance of [`Scope`](https://javadoc.io/doc/io.opentelemetry/opentelemetry-context/latest/io/opentelemetry/context/Scope.html), which is "a mounted context for a block of code".
From there, we can instantiate tracing spans that will have correct parent spans and will belong to the correct trace.

Example:

```clojure
(def ^:dynamic *context*)

(let [parent-span (-> (.spanBuilder tracer "parent") .start)]
  (.makeCurrent parent-span)
  (with-bindings {#'*context* (Context/current)} ;; store on thread local current context
    (go
      (.makeCurrent *context*)
      (let [child-span (-> (.spanBuilder tracer "child") .start)]
        ;; do some work
        (.end child-span))))
  (.end parent-span))
```

So overral it could sound wasteful: 

1. Thread A: take a value (Context) stored in thread local
2. Thread A: store it in another thread local (Clojure dynamic `Var`)
3. Thread A convey binding `(clojure.lang.Var/getThreadBindingFrame)` to Thread B `(clojure.lang.Var/resetThreadBindingFrame binds)`
4. Thread B: take the value stored in thread local (Clojure dynamic `Var`)
5. Thread B: store in another thread local (`Context.makeItCurrent`)

And since from a high level, we mimic with Clojure bindings what is already done with `Context`, we could wonder if we need `Context` at all.
And if we wonder how about not using this `Context` mechanism all together, and just generate Spans and manage the spans parent/child tracking, this video explains well why we can't avoid it: [Context Propagation makes OpenTelemetry awesome](https://www.youtube.com/watch?v=gviWKCXwyvY). The value proposition of OpenTelemetry is to correlate traces with metrics and logs. `Context` object is at the center of this, if we bypass it only to have tracing working, we will be blocked when wrapping OpenTelemetry metrics and logs. Another way to see it:

> Context is an object that is a global concern in OpenTelemetry, while the "current span" is a concept of tracing specifically. ([src](https://github.com/open-telemetry/opentelemetry-java/issues/1807))

Back to our implementation, when we call `.makeCurrent`, the `Context` object is set to the `ContextStorage`, which effectively forms a scope for the context.
The `scope` is bound to the current thread. Within a scope, its `Context` is accessible even across API boundaries, through current. The scope is later exited by `Scope.close()`.
Every makeCurrent() must be followed by a Scope.close(). Breaking these rules may lead to memory leaks and incorrect scoping.

Javadoc:
> Makes this the current context and returns a Scope which corresponds to the scope of execution this context is current for. current() will return this Context until Scope.close() is called. Scope.close() must be called to properly restore the previous context from before this scope of execution or context will not work correctly. It is recommended to use try-with-resources to call Scope.close() automatically.

In clojure, that means to wrap in a `with-open` macro. The complete example is :

```clojure
(def ^:dynamic *context*)

(let [parent-span (-> (.spanBuilder tracer "parent") .start)]
  (with-open [_ (.makeCurrent parent-span)] ;; add this span to the current context
    (with-bindings {#'*context* (Context/current)} ;; store current context in a dynamic clojure Var
      (go ;; go macro will get calling thread bindings
        (with-open [_ (.makeCurrent *context*)] ;; set current context in this thread
          (let [child-span (-> (.spanBuilder tracer "child") .start)]
            ;; do some work
            (.end child-span))))))
  (.end parent-span))
```

The last detail to validate, is inside a go block, work on thread can park and then resume on another thread, which means our current context in thread locals would be not set. But we are guaranteed by Clojure implementation to always have `*context*` Var bound to the running thread, so it could mean to call `.makeCurrent` each time we build a span. TODO: write a spec to validate this (compare Context stored values).

EDIT october 18:

- There is an obvious duality between thread local ContextStorage and Clojure dynamic var and with-bindings
    - both use "frames" to push / pop current Context in the scope of execution
        - in opentelemetry-java, `ThreadLocalContextStorage` [implementation](https://github.com/open-telemetry/opentelemetry-java/blob/main/context/src/main/java/io/opentelemetry/context/ThreadLocalContextStorage.java#L20) does it and return an AutoCloseable `Scope`
        - in Clojure, that's `with-binding` macro, which calls `clojure.lang.Var/pushThreadBindings` and `clojure.lang.Var/popThreadBindings` to restore previous frame
- if we use a clojure dynamic Var as the context storage backend, we don't need to duplicate the code (`with-open`, `with-bindings`), simply calling opentelemetry API would set Clojure bindings that would then be conveyed accross thread boundaries.
    - solution 1: custom `ContextStorage` implementation, that leverages a dynamic clojure.lang.Var as a storage (instead of direct calls to direct calls to ThreadLocal)
    - solution 2: less "intrusive", simply add a wraper that hooks and call the clojure Var push and pop functions [example](https://github.com/open-telemetry/opentelemetry-java/blob/50408d499f85d5761d0a5ed9bf9d77d5ff01fff5/context/src/storageWrappersTest/java/io/opentelemetry/context/StorageWrappersTest.java#L22)

  potential problems to explore:
    - compatibility with opentelemetry javaagent auto instrumentation ?
    - clojure.lang.Var might create more churn since it creates and store all stored "frames"

    
#### Solution 2: wrap ExecutorServices

When we have access to the underlying `java.util.concurrent.Executor`, then we can simply use [Context/taskWrapping](https://github.com/open-telemetry/opentelemetry-java/blob/main/context/src/main/java/io/opentelemetry/context/Context.java#L114):

```clojure
;; future body is sent on Agent soloExecutor 
(set-agent-send-off-executor! (Context/taskWrapping clojure.lang.Agent/soloExecutor))

(let [parent-span (-> (.spanBuilder tracer "parent-span") .start)
      _scope (.makeCurrent span)]
  (future
    (let [child-span (-> (.spanBuilder tracer "child-span") .start)]
      ;; ...
      (.end child-span)))
  (.end parent-span))
;; => child-span is nested in parent-span 
```

What is great in this solution is the lack of boilerplate needed to convey `Context`.

Caveat: it may seems "invasive", meaning changing the way a whole Executor is set might have consequences I didn't identity yet.

TODO: It looks possible to swap `core.async` Executor as well:
- https://github.com/clojure/core.async/blob/master/src/main/clojure/clojure/core/async/impl/exec/threadpool.clj#L26
- it can be so sensitive I need to ask on Clojurians Slack some feedback about it

### Dismissed solutions

#### Custom `ContextStorage`

The `Context` API is quite imperative and may seem painful to use, it is related to how `ContextStorage` stores `Context`: in thread locals.  
So why not replace the default `ContextStorage` implementation with a custom one, in clojure, using `ContextStorageProvider` SPI ?

Problems: 
- could work for Clojure code, but what about Java libraries often used in a project ?
- would probably break auto-instrumentation 

#### Explicit value passing

Meaning we could pass as function argument the current `Context` down all of the code execution path. This would be way more "pure" but there is a caveat to this approach, there are explained in this video: [The painful simplicity of context propagation in Go](https://www.youtube.com/watch?v=g4ShnfmHTs4).
