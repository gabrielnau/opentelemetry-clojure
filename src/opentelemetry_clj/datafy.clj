;; FIXME: should be moved into /sdk directory, since it uses some SDK classes ?

(ns opentelemetry-clj.datafy
  "Turn OpenTelementry Java Objects into data.

  Used in tests but can be useful in development to instrospect Spans etc.

  *Warning:* this namespace isn't required by opentelemetry-clj.core in order to support older Clojure versions, so it has to be manually required.
  "
  (:require [clojure.core.protocols :as protocols]
            [clojure.datafy :refer [datafy]])
  (:import
    (io.opentelemetry.api.baggage ImmutableBaggage AutoValue_ImmutableEntry)
    (io.opentelemetry.api.common Attributes ArrayBackedAttributes)
    (io.opentelemetry.api.trace TraceFlags TraceState SpanContext)
    (io.opentelemetry.sdk.common InstrumentationLibraryInfo)
    (io.opentelemetry.sdk.resources AutoValue_Resource Resource)
    (io.opentelemetry.sdk.trace RecordEventsReadableSpan)
    (io.opentelemetry.sdk.trace.data EventData)))

(extend-protocol protocols/Datafiable
  InstrumentationLibraryInfo
  (datafy [x]
    {:name       (.getName x)
     :version    (.getVersion x)
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
  AutoValue_Resource
  (datafy [resource]
    {:schema-url (.getSchemaUrl resource)
     :attributes (datafy (.getAttributes resource))})       ;; FIXME
  Resource                                                  ;; not used ? see tests
  (datafy [r]
    {:valid?     (.isValid r)
     :schema-url (.getSchemaUrl r)
     :version    (.readVersion r)
     :attributes (datafy (.getAttributes r))})
  ImmutableBaggage
  (datafy [x]
    (let [as-map (into {} (.asMap x))]
      (reduce-kv
        (fn [acc k v] (assoc acc (str k) (datafy v)))
        {}
        as-map)))
  AutoValue_ImmutableEntry
  (datafy [x]
    {:value (.getValue x) :metadata (.getValue (.getMetadata x))})
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

