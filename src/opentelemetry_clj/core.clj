(ns opentelemetry-clj.core
  ;; Don't require the following namespaces or it will break users who don't have the opentelemetry-sdk on the classpath
  ;; (i.e. lib authors)
  ;; - opentelemetry-clj.sdk.*
  (:import
    (io.opentelemetry.api GlobalOpenTelemetry)))

(set! *warn-on-reflection* true)

(defn tracer
  ([instrumentation-name]
   (GlobalOpenTelemetry/getTracer ^String instrumentation-name))
  ([instrumentation-name instrumentation-version]
   (GlobalOpenTelemetry/getTracer ^String instrumentation-name ^String instrumentation-version)))
