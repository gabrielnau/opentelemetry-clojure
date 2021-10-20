# In-process `Context` propagation

**Definitions and scope of the problem :**

This short [introduction](https://opentelemetry.lightstep.com/core-concepts/context-propagation/) on Lightstep provides a clear explanation of what is `Context`.

Here, we are only discussing **in-process context propagation**. As stated in [opentelemetry-java doc](https://github.com/open-telemetry/opentelemetry-java/blob/main/context/src/main/java/io/opentelemetry/context/Context.java#L67):
> it is not trivial, and when done incorrectly can lead to broken traces or even mixed traces

It is also stated that it's up to the instrumentation author to carry the `Context` between threads:
> The automatic context propagation is done using io.opentelemetry.context.Context which is a gRPC independent implementation for in-process Context propagation mechanism which can carry scoped-values across API boundaries and between threads. Users of the library must propagate the io.opentelemetry.context.Context between different threads.

**Problem statement :**

How to wrap opentelemetry-java `Context` propagation across threads in Clojure in order to support its concurrency primitives, like `core/future` or `core.async` ?

Constraints:
- Since we want to support OpenTelemetry traces, but also metrics and logs, we have to ensure `Span.current()` and `Context.current()` return correct value everywhere down the code execution path.
- We would like to limit the boilerplate needed to manage this, interleaving OpenTelemetry code with app code should add as few as possible complexity.
- While wrapping the Java lib, leave all decisions to instrumentation author, meaning the Clojure wrapper isn't opinionated as to how do things ideally.
- There is an auto instrumentation java agent available, which override some default behaviors from the SDK. Solutions should work both with this agent injected or not. 

## Solutions

### Implicit context passing to ExecutorServices

When we have access to the underlying `java.util.concurrent.Executor`, then we can simply use [Context/taskWrapping](https://github.com/open-telemetry/opentelemetry-java/blob/main/context/src/main/java/io/opentelemetry/context/Context.java#L114):

```clojure
;; We can set the executor that runs future : 
(set-agent-send-off-executor! (Context/taskWrapping clojure.lang.Agent/soloExecutor))

(let [parent-span (-> (.spanBuilder tracer "parent-span") .start)]
  (.makeCurrent parent-span)
  ;; => (Context/current) returns the parent span context
  (future
    ;; thread boundary
    ;; => (Context/current) still returns the parent span context
    (let [child-span (-> (.spanBuilder tracer "child-span") .start)]
    ;; => child-span is nested in parent-span 
```

Advantages:
- complete lack of boilerplate needed to convey `Context`: it's transparent

Caveats:
- it may seems "invasive", meaning changing the way a whole Executor is set might have consequences I didn't identity yet.

### Implicit context passing with Use `let` to create a [lexical scope](https://clojure.org/guides/learn/functions#_locals_and_closures)

To convey the current at a thread boundary, simply create a lexical scope with a `let`: 
```clojure
(let [span (new-span "parent")]
  (.makeCurrent span)
  (let [context (Context/current)] ;; define before the thread boundarie a lexical binding to the context
    (thread
      (.makeCurrent context) ;; set the current context in the scope of the new thread
      (let [span-c (new-span "child 1")])))
```
For this example to be correct, we should add the `with-open` around `.makeCurrent` calls:

```clojure
(let [span (new-span "parent")]
  (with-open [_ (.makeCurrent span)] ;; store span in current Context in a thread-local
    (let [context (Context/current)] ;; define before the thread boundarie a lexical binding to the context
      (thread
        (with-open [_ (.makeCurrent context)] ;; set the context in the scope of the new thread, that will be cleaned-up after the with-open
          (let [span-c (new-span "child 1")]))))))
```

Caveats:
- lots of boilerplate, but that may be simplified with a macro

**What about core.async ?**

It works well when we are in the lexical scope, with `go` and `thread`. When outside of lexical scope, ie code execution path having core.async channels in the middle, then we will have to convey the Context alongside the data. See next section. 

Caveat:
- Inside a `go` block, work on thread may park and then resume on another thread, which means our current context in thread locals would be not set when resuming.

Example:
```clojure
(with-open [_ (.makeCurrent parent-span)]                      
  (let [context (Context/current)]
    (go
      (with-open [_ (.makeCurrent context)]
        (let [span-child (new-span "child-span")]
          ;; will park and after resume on potentially another thread
          (<! (async/timeout 10))
          ;; at this point (Current/context) can be anything (null or the one from another code execution)
          ;; in order to restore the Context/current, we would need to call again :
          ;; (with-open [_ (.makeCurrent context)]
          )))))
```

Solutions to explore:
- macros to hide the boilerplate needed + clear documentation about this caveat
- inside a go block, don't try to have the correct `Context/current` value, knowing that metrics and logs generated from within the go block will lack correct correlation data.

### Explicit context passing

Meaning we could pass as function argument the current `Context` down all of the code execution path. This would be way more "pure" but there is a caveat to this approach, there are explained in this video: [The painful simplicity of context propagation in Go](https://www.youtube.com/watch?v=g4ShnfmHTs4).

```clojure
(defn child-fn [parent-span]
  (future ;; thread boundary
    (let [parent-context (-> (Context/current) (.with parent-span))
          child-span (-> (.spanBuilder tracer "child-span")
                       (.setParent parent-context)
                       .start)])))
          ;; => child-span is nested in parent-span 

(defn parent-fn []
  (let [parent-span (-> (.spanBuilder tracer "parent-span") .start)]
    (.makeCurrent parent-span) ;; => (Context/current) returns the parent span context
    (child-fn parent-span)))
```

Advantages:
- In Clojure, we are more used to values flowing in our programs, instead of some brittle shared state

Caveats:
- it requires to change all functions to take a `Context` as a param, that's often show stopper.
- it works only for tracing, but not for metrics or logs, because these cross-cutting concerns have to way to get the current `Context` value.

**What about core.async ?**

When needed, `Context` could also be conveyed through core.async channels, either as an explicit tuple [msg context] or implicitly using `with-meta`.
TODO: see if there is any caveat to use `with-meta` instead of a tuple or anything else, how is it stored in memory ? We don't want to misuse the clojure meta api.