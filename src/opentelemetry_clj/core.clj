(ns opentelemetry-clj.core
  ;; Don't require the following namespaces or it will break users who don't have the opentelemetry-sdk on the classpath
  ;; (i.e. lib authors)
  ;; - opentelemetry-clj.sdk.*
  ;; - opentelemetry-clj.datafy
  (:require [opentelemetry-clj.trace.span]
            [opentelemetry-clj.context]
            [opentelemetry-clj.attribute])
  (:import (io.opentelemetry.api GlobalOpenTelemetry)
           (io.opentelemetry.api.baggage Baggage)
           (io.opentelemetry.context Context)))

;; requirements:
;; - concise API for general use cases
;; WARNING: keep in mind this will contain api for metrics and eventually logging as well

(set! *warn-on-reflection* true)

;; Shouldn't be used ?
(defn global-open-telemetry []
  (GlobalOpenTelemetry/get))

(defn tracer
  ([instrumentation-name]
   (GlobalOpenTelemetry/getTracer ^String instrumentation-name))
  ([instrumentation-name instrumentation-version]
   (GlobalOpenTelemetry/getTracer ^String instrumentation-name ^String instrumentation-version)))

(defn baggage-from-implicit-context []
  (Baggage/current))

(defn baggage-from-explicit-context [context]
  (Baggage/fromContext ^Context context))

(defn ^Context store-baggage-in-context [baggage context]
  (.storeInContext ^Baggage baggage ^Context context))

(comment
  ;; Ideas of context conveyance API + span creation

  (defn span-with-implicit-context [tracer ops])
  (defn span-with-context-value [tracer ops context])

  (defn set-context-as-current! [context & body]
    (with-open [_scope (.makeCurrent context)]))
  ;; body))

  (defmacro go-with-context [context]
    (let [c context]
      (go
        (set-context-as-current!
          context
          &body))))

  (defn with-conveyed-context [context & body])
  (let [conveyed-context context]
    (thread
      (set-context-as-current! conveyed-context))))
;; body))