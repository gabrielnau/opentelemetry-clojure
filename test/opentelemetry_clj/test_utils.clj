(ns opentelemetry-clj.test-utils
  (:require [clojure.spec.gen.alpha :as gen]
            [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :as protocols]
            [clojure.spec.alpha :as s]
            [opentelemetry-clj.trace.span :as span])
  (:import (io.opentelemetry.sdk.trace SdkTracerProvider RecordEventsReadableSpan)
           (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)
           (io.opentelemetry.sdk.trace.data ImmutableStatusData EventData)
           (io.opentelemetry.sdk.common InstrumentationLibraryInfo AutoValue_InstrumentationLibraryInfo)
           (io.opentelemetry.api.common Attributes ArrayBackedAttributes)
           (io.opentelemetry.context Context)
           (io.opentelemetry.api.trace SpanContext TraceFlags ImmutableTraceFlags TraceState)
           (io.opentelemetry.sdk.resources Resource AutoValue_Resource)))


(def ^:dynamic *tracer*)
(def ^:dynamic *memory-exporter*)

(defmacro with-exporter-reset
  [& body]
  `(try
     ~@body
     (finally
       (.reset *memory-exporter*))))

(comment
  (macroexpand '(with-exporter-reset (do :bar))))



(defn get-tracer-and-exporter []
  (let [memory-exporter (InMemorySpanExporter/create)
        tracer          (-> (doto (OpenTelemetrySdk/builder)
                              (.setTracerProvider
                                ;; SimpleSpanProcessor to avoid batching
                                ;; InMemorySpanExporter to unit test what comes out
                                (-> (doto (SdkTracerProvider/builder)
                                      (.addSpanProcessor
                                        (SimpleSpanProcessor/create memory-exporter)))
                                  .build)))
                          .build
                          (.getTracer "test"))]
    [tracer memory-exporter]))

(comment
  ;;FIXME
  (defmacro with-tracer-and-exporter
    [& body]
    `(let ['tracer `(get-tracer-and-exporter)]
       `(with-bindings {#'*tracer*          :bar
                        #'*memory-exporter* `:foo}
          `(with-exporter-reset
             ~@body))))

  (macroexpand '(with-tracer-and-exporter (deftest foo (testing "foobar")))))

;; generators

(def span-name #(gen/generate (s/gen string?)))



(defn spans []
  (.getFinishedSpanItems *memory-exporter*))

;; Span

(defn built-span [options]
  (-> (span/build *tracer* options)
    span/start))

(defn closed-span [options]
  (let [span (built-span options)]
    (span/end span)
    span))


;; Datafy

(extend-protocol protocols/Datafiable
  InstrumentationLibraryInfo
  (datafy [x]
    {:name (.getName x)
     :version (.getVersion x)
     :schema-url (.getSchemaUrl x)})
  EventData
  (datafy [event]
    {:name                     (.getName event)
     :attributes               (->> (.getAttributes event)
                                 (map datafy))
     :epoch-nanos              (.getEpochNanos event)
     :attributes-count         (.getTotalAttributeCount event)
     :dropped-attributes-count (.getDroppedAttributesCount event)})
  ArrayBackedAttributes
  (datafy [attrs]
    (let [a (.asMap attrs)]
      (if (.isEmpty a)
        []
        (datafy a))))
  Attributes
  (datafy [attrs]
    (let [a (.asMap attrs)]
      (if (.isEmpty a)
        []
        (datafy a))))
  TraceFlags
  (datafy [trace-flags]
    {:hex      (.asHex trace-flags)
     :sampled? (.isSampled trace-flags)})
  TraceState
  (datafy [trace-state]
    (let [a (.asMap trace-state)]
      (if (.isEmpty a)
        {}
        (datafy a))))
  SpanContext
  (datafy [context]
    {:trace-id    (.getTraceId context)
     :span-id     (.getSpanId context)
     :sampled?    (.isSampled context)
     :trace-flags (datafy (.getTraceFlags context))
     :trace-state (datafy (.getTraceState context))
     :valid?      (.isValid context)
     :remote?     (.isRemote context)})
  ;Resource
  ;(datafy [resource]
  ;  {:schema-url (.getSchemaUrl resource)
  ;   :attribut (datafy (.getAttributes resource))})
  AutoValue_Resource
  (datafy [resource]
    {:schema-url (.getSchemaUrl resource)
     :attributes (datafy (.getAttributes resource))}) ;; FIXME
  RecordEventsReadableSpan
  (datafy [span]
    (let [span-data      (.toSpanData span)
          context        (.getSpanContext span-data)
          parent-context (.getParentSpanContext span-data)]
      ;; todo: display parent-context if present, else display none

      {:name                    (.getName span-data)
       :trace-id                (.getTraceId context)
       :span-id                 (.getSpanId context)
       :kind                    (str (.getKind span-data))
       :parent-context          (datafy parent-context)
       :context                 (datafy context)
       :attributes              (datafy (.getAttributes span-data))

       :resource                (datafy (.getResource span-data))
       :status                  (-> (.getStatus span-data) .getStatusCode str)
       :recording?              (.isRecording span)
       :ended?                  (.hasEnded span-data)

       :links                   (->> (.getLinks span-data) (into []))
       :events                  (->> (.getEvents span-data)
                                  (map datafy))
       :stats                   {:attributes-count (.getTotalAttributeCount span-data)
                                 :links-count      (.getTotalRecordedLinks span-data)
                                 :events-count     (.getTotalRecordedEvents span-data)}
       :timestamps              {:start-epoch-nanos (.getStartEpochNanos span-data)
                                 :end-epoch-nanos   (.getEndEpochNanos span-data)
                                 :latency           (.getLatencyNanos span)}
       :instrumentation-library (datafy (.getInstrumentationLibraryInfo span-data))})))