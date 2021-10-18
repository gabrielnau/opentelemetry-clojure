(ns gabrielnau.examples
  (:require
    [gabrielnau.context-propagation :as context-propagation]
    [clojure.core.async :as async :refer [>! <! >!! <!! go chan buffer close! thread
                                          alts! alts!! timeout]])
  (:import (io.opentelemetry.exporter.logging LoggingSpanExporter)
           (io.opentelemetry.sdk.trace SdkTracerProvider)
           (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.context Context)))

;; draft
;; in production, it would live in a component
(def otel (->
            (doto (OpenTelemetrySdk/builder)
              (.setTracerProvider (.build
                                    (doto (SdkTracerProvider/builder)
                                      (.addSpanProcessor (SimpleSpanProcessor/create (LoggingSpanExporter.))))))) ;; we can visually check parent / child proper nesting given ids in logs)))) ;; wip to see how we can properly test it
            .build))
(def tracer (.getTracer otel "foo" "1.0.0"))

(defn new-span
  "dummy implementation to lighten examples"
  [name]
  (-> (.spanBuilder tracer name) .startSpan))


(comment

  ;; core/future example without agent executor wrapped
  (let [span (new-span "parent")]
    (with-open [_ (.makeCurrent span)]
      (with-bindings {#'context-propagation/*current-context* (Context/current)}
        @(future
           (with-open [_ (.makeCurrent context-propagation/*current-context*)]
             (let [span-c (new-span "child")]
               (.end span-c))))))
    (.end span))
  ;; LoggingSpanExporter logs:
  ;Oct 18, 2021 12:10:23 AM io.opentelemetry.exporter.logging.LoggingSpanExporter export
  ;INFO: 'child' : 328ba3ad90ca919608d26d153ec074a5 a97e3e84114f597d INTERNAL [tracer: foo:1.0.0] {}
  ;Oct 18, 2021 12:10:23 AM io.opentelemetry.exporter.logging.LoggingSpanExporter export
  ;INFO: 'parent' : 328ba3ad90ca919608d26d153ec074a5 425fc3ec9bc489e0 INTERNAL [tracer: foo:1.0.0] {}
  ;; => same trace id 328ba3ad90ca919608d26d153ec074a5


  (def ^:dynamic d)
  (go
    (binding [d :bound]
      (println d (.getName (Thread/currentThread)))
      (<! (async/timeout 10))
      (println d (.getName (Thread/currentThread)))))


  ;; core/future example with agent executors wrapped
  (context-propagation/wrap-agent-executors)
  (let [span (new-span "parent")]
    (with-open [_ (.makeCurrent span)]
      @(future
         (let [span-child (new-span "child")]
           (.end span-child))))
    (.end span)
    :ok)
  ;; LoggingSpanExporter logs:
  ;Oct 18, 2021 12:11:40 AM io.opentelemetry.exporter.logging.LoggingSpanExporter export
  ;INFO: 'child' : 8313186813827acbbcea28ece32510a1 e04a99adaf9db21e INTERNAL [tracer: foo:1.0.0] {}
  ;Oct 18, 2021 12:11:40 AM io.opentelemetry.exporter.logging.LoggingSpanExporter export
  ;INFO: 'parent' : 8313186813827acbbcea28ece32510a1 35fa261cdc591285 INTERNAL [tracer: foo:1.0.0] {}
  ;; => same trace id 8313186813827acbbcea28ece32510a1


  ;;TODO: macro to be sure to end a span for common cases

  ;; core async example
  (let [hi-chan (chan 10)
        span    (new-span "parent")]
    ;; ---
    ;; this boilerplate could be done in a macro:
    (with-open [_ (.makeCurrent span)]                      ;; Context set current value
      (with-bindings {#'context-propagation/*current-context* (Context/current)} ;; store on thread local current context
        ;; ----

        ;; 1. validate core.async/thread binding conveyance
        (thread
          (with-open [_ (.makeCurrent context-propagation/*current-context*)] ;; TODO: macro with a more meaningful name than this
            (let [span-c (new-span "child 1")]
              (.end span-c))))

        ;; 2. validate core.async/go binding conveyance
        (go
          (with-open [_ (.makeCurrent context-propagation/*current-context*)]
            (let [span-c (new-span "child 2")
                  context-1 (Context/current)]
              (println "context hashCode before:" (.toString context-1) "on thread:" (.getName (Thread/currentThread)))
              (<! (async/timeout 10))
              (println "context hashCode after:" (.toString (Context/current)) "on thread:" (.getName (Thread/currentThread)))
              (>! hi-chan (str "hi " 1))
              (.end span-c))))
        (.end span)))))
  ;; LoggingSpanExporter logs:
  ;Oct 18, 2021 12:12:49 AM io.opentelemetry.exporter.logging.LoggingSpanExporter export
  ;INFO: 'child 1' : 6d2aee93b5eb61795b2ba9ad38b7cc55 621bf75dadfd64a8 INTERNAL [tracer: foo:1.0.0] {}
  ;Oct 18, 2021 12:12:49 AM io.opentelemetry.exporter.logging.LoggingSpanExporter export
  ;INFO: 'parent' : 6d2aee93b5eb61795b2ba9ad38b7cc55 0a25de4c7eeb47b0 INTERNAL [tracer: foo:1.0.0] {}

  ;Oct 18, 2021 12:12:49 AM io.opentelemetry.exporter.logging.LoggingSpanExporter export
  ;INFO: 'child 2' : 6d2aee93b5eb61795b2ba9ad38b7cc55 c696af268745d0ed INTERNAL [tracer: foo:1.0.0] {}
  ;Oct 18, 2021 12:12:49 AM io.opentelemetry.exporter.logging.LoggingSpanExporter export
  ;INFO: 'child 2' : 6d2aee93b5eb61795b2ba9ad38b7cc55 bcee8987f40c0dcf INTERNAL [tracer: foo:1.0.0] {}

  ;; => same trace id 6d2aee93b5eb61795b2ba9ad38b7cc55