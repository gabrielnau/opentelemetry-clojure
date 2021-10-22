(ns opentelemetry-clj.context
  (:import (io.opentelemetry.context Context ImplicitContextKeyed)
           (java.util.concurrent ExecutorService Executor ScheduledExecutorService)))

(set! *warn-on-reflection* true)

;; Explicit value passing:
;; downsides: child function can't set parent span status
;; - macro for the thread boundary without .makeCurrent

;; Implicit value passing:
;- wrap executor service

(defn wrap-executor
  "Returns an Executor which delegates to the provided executor, wrapping all invocations of Executor.execute(Runnable) with the current context at the time of invocation."
  [^Executor executor]
  (Context/taskWrapping executor))

(defn wrap-executor-service
  "Returns an ExecutorService which delegates to the provided executorService, wrapping all invocations of ExecutorService methods such as ExecutorService.execute(Runnable) or ExecutorService.submit(Runnable) with the current context at the time of invocation.\nThis is generally used to create an ExecutorService which will forward the Context during an invocation to another thread.
  "
  [^ExecutorService executor-service]
  (Context/taskWrapping executor-service))

(defn wrap-scheduled-executor-service
  [^Context context ^ScheduledExecutorService scheduled-executor-service]
  (.wrap context scheduled-executor-service))

(defn wrap-future-executor
  "that's actually the Agent send-off executor
  Context/current will be correct inside a future body
  TODO: example code
  "
  []
  ;; clojure.lang.Agent/soloExecutor
  (set-agent-send-off-executor! (wrap-executor clojure.lang.Agent/soloExecutor)))

(comment
  (wrap-future-executors)
  (let [span (new-span "parent")]
    (with-open [_ (.makeCurrent span)]
      @(future
         (let [span-child (new-span "child")]
           (.end span-child))))
    (.end span)))

(defn wrap-agent-executors []
  (set-agent-send-off-executor! (wrap-executor clojure.lang.Agent/soloExecutor))
  (set-agent-send-executor! (wrap-executor clojure.lang.Agent/pooledExecutor)))

;- macro for the thread boundary with .makeCurrent
;- keep Context/current in sync, except maybe in go blocks

;; create span from implicit parent
;; create span from explicit parent

(defn current []
  (Context/current))

(defn root []
  (Context/root))

(defn with
  "Both Span and Context implement ImplicitContextKeyed interface, so this can be called with a Span or a Context instance"
  [^Context context ^ImplicitContextKeyed value]
  (.with context value))

(defn make-current!
  "Side effectful"
  [^Context context]
  (.makeCurrent context))

;; TODO: wrap callabla and runnable, .with, and .get -> see if we can datafy and to-map

;; Conveyance:

(comment

  ;; Simpler solution with lexical binding:
  (let [span (new-span "parent")]
    (with-open [_ (.makeCurrent span)]
      (let [context (Context/current)]                      ;; define before the thread boundarie a ref to the context
        (thread
          (with-open [_ (.makeCurrent context)]             ;; set the context in the scope of the new thread
            (let [span-c (new-span "child 1")]
              (.end span-c)
              (println span-c))))))
    (.end span))
  ;; Notes:
  ;; it works for futures, core.async
  (let [span (new-span "parent")]
    (with-open [_ (.makeCurrent span)]
      (let [context (Context/current)]                      ;; define before the thread boundarie a ref to the context
        (go
          (with-open [_ (.makeCurrent context)]             ;; set the context in the scope of the new thread
            (let [span-c (new-span "child 1")]
              (.end span-c)
              (println "child 1 on thread" (.getName (Thread/currentThread)))))
          (<! (async/timeout 10))
          (with-open [_ (.makeCurrent context)]             ;; set the context in the scope of the new thread
            (let [span-c (new-span "child 2")]
              (.end span-c)
              (println "child 1 on thread" (.getName (Thread/currentThread)))))))
      (.end span))))

