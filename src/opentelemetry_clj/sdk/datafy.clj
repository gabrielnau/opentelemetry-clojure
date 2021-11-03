(ns opentelemetry-clj.sdk.datafy
  "Turn OpenTelementry Java Objects into data. Implement Clojure Datafiable protocol.

  Used in tests but can be useful in development to instrospect Spans etc.

  It is on purpose some classes from the SDK instead of the API are used: for example, API doesn't provide a way to read attributes from a Span, but it's specified in SDK since it's only needed for exporters. See [this discussion](https://cloud-native.slack.com/archives/C0150QF88FL/p1635258042025900).

  *Warning:* this namespace requires the dependency `io.opentelemetry/opentelemetry-sdk` that should be set manually or available in classpath if using the java agent.
  "
  (:require [clojure.core.protocols :as protocols]
            [clojure.datafy :refer [datafy]]
            [opentelemetry-clj.baggage :as baggage]
            [opentelemetry-clj.trace.span :as span]
            [opentelemetry-clj.attributes :as attribute])
  (:import
    (io.opentelemetry.api.baggage ImmutableBaggage)
    (io.opentelemetry.api.common Attributes)
    (io.opentelemetry.api.trace TraceFlags TraceState SpanContext)
    (io.opentelemetry.sdk.common InstrumentationLibraryInfo)
    (io.opentelemetry.sdk.resources AutoValue_Resource Resource)
    (io.opentelemetry.sdk.trace RecordEventsReadableSpan)
    (io.opentelemetry.sdk.trace.data EventData)
    (java.util.function BiConsumer)))

;; Primitive arrays, see https://clojure.atlassian.net/browse/CLJ-1381
(extend-protocol protocols/Datafiable
  (Class/forName "[Ljava.lang.String;")                     ;; String[]
  (datafy [x] (into [] x)))

(extend-protocol protocols/Datafiable
  (Class/forName "[Z")                                      ;; Boolean[]
  (datafy [x] (into [] x)))

(extend-protocol protocols/Datafiable
  (Class/forName "[J")                                      ;; Long[]
  (datafy [x] (into [] x)))

(extend-protocol protocols/Datafiable
  (Class/forName "[D")                                      ;; Double[]
  (datafy [x] (into [] x)))

(extend-protocol protocols/Datafiable

  InstrumentationLibraryInfo ;; in SDK
  (datafy [x]
    {:name       (.getName x)
     :version    (.getVersion x)
     :schema-url (.getSchemaUrl x)})

  EventData ;; in SDK
  (datafy [event]
    {:name                     (.getName event)
     :attributes               (map datafy (.getAttributes event))
     :epoch-nanos              (.getEpochNanos event)
     :attributes-count         (.getTotalAttributeCount event)
     :dropped-attributes-count (.getDroppedAttributesCount event)})

  Attributes
  (datafy [attrs] (attribute/->map attrs))

  TraceFlags
  (datafy [trace-flags] (span/trace-flags->map trace-flags))

  TraceState
  (datafy [trace-state] (span/trace-state->map trace-state))

  SpanContext
  (datafy [span-context] (span/span-context->map span-context))

  AutoValue_Resource ;; in SDK
  (datafy [resource]
    (resource/->map resource))

  Resource ;; in SDK
  (datafy [resource] (resource/->map resource))

  ImmutableBaggage
  (datafy [x] (baggage/->map x))

  RecordEventsReadableSpan ;; in SDK
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
