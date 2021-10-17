(ns gabrielnau.opentelemetry-clojure
  (:require
    [gabrielnau.test-utils :as test-utils]
    [clojure.core.async
     :as a
     :refer [>! <! >!! <!! go chan buffer close! thread
             alts! alts!! timeout]])
  (:import (io.opentelemetry.exporter.logging LoggingSpanExporter)
           (io.opentelemetry.sdk.trace SdkTracerProvider)
           (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)
           (io.opentelemetry.context.propagation ContextPropagators)
           (io.opentelemetry.api.trace.propagation W3CTraceContextPropagator)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.context Context)))

;; draft

(defn sdk-tracer-provider []
  (.build
    (doto (SdkTracerProvider/builder)
      (.addSpanProcessor (SimpleSpanProcessor/create (LoggingSpanExporter.))) ;; we can visually check parent / child proper nesting given ids in logs
      (.addSpanProcessor (SimpleSpanProcessor/create test-utils/memory-exporter))))) ;; wip to see how we can properly test it

(defn otel-builder []
  (->
    (doto (OpenTelemetrySdk/builder)
      (.setTracerProvider (sdk-tracer-provider)))
    ;;(.setPropagators (ContextPropagators/create (W3CTraceContextPropagator/getInstance))))
    .build))

(defn build-span [tracer span-name]
  (.spanBuilder tracer span-name))

;; in production, it would live in a component
(def otel (otel-builder))
(def tracer (.getTracer otel "foo" "1.0.0"))

(defn wrap-agent-executors []
  (set-agent-send-off-executor! (Context/taskWrapping clojure.lang.Agent/soloExecutor))
  (set-agent-send-executor! (Context/taskWrapping clojure.lang.Agent/pooledExecutor)))

;; dynamic var used
(def ^:dynamic *current-context*)

(comment
  ;; example with agent executors wrapped
  (wrap-agent-executors)
  (let [span   (-> (.spanBuilder tracer "parent") .start)
        _scope (.makeCurrent span)]
    (let [f @(future
               (let [span-c (-> (build-span tracer "inside root span !") start)]
                 (end span-c)))]
      (println "f:" f))
    (end span)
    :ok)

  ;; macro to be sure to end span
  ;; with-span-open that does the try finally span.end()

  ;; core async example
  (let [hi-chan (chan 10)
        span    (-> (.spanBuilder tracer "parent") .start)]
    (with-open [_ (.makeCurrent span)]                      ;; Context set current value
      (with-bindings {#'*current-context* (Context/current)} ;; store on thread local current context

        (thread
          (with-open [_ (.makeCurrent *current-context*)]
            (let [span-c (-> (build-span tracer "CHILD IN THREAD") start)]
              (.end span-c))))


        (doseq [n (range 2)]
          (go
            (with-open [_ (.makeCurrent *current-context*)]
              (let [span-c (-> (build-span tracer "CHILD") start)]
                (>! hi-chan (str "hi " n))
                (.end span-c)
                (println "child ended")))))))
    (end span))

  (let [span          (-> (build-span tracer "root span !") start)
        dynamic-scope (.makeCurrent span)
        current-span  span]
    (with-open [_ (.makeCurrent span)]
      (with-bindings {#'*current-context* (Context/current)}
        @(future
           (with-open [_ (.makeCurrent *current-context*)]
             (let [span-c (-> (build-span tracer "inside root span !") ;; no need to call setParent
                            start)]
               (end span-c))))))
    (end span)))

