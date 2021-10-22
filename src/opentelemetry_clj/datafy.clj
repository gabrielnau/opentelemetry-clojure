(ns opentelemetry-clj.datafy
  "Separate namespace so if using an old clojure version, can still use this lib without using Datafy"
  (:require [clojure.core.protocols :as protocols]
            [clojure.datafy :refer [datafy]])
  (:import (io.opentelemetry.api.common Attributes ArrayBackedAttributes)
           (io.opentelemetry.sdk.common InstrumentationLibraryInfo)
           (io.opentelemetry.sdk.trace.data EventData)
           (io.opentelemetry.api.trace TraceFlags TraceState SpanContext)
           (io.opentelemetry.sdk.resources AutoValue_Resource Resource)
           (io.opentelemetry.sdk.trace RecordEventsReadableSpan)))


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
  Resource ;; not used ? see tests
  (datafy [r]
    {:valid?     (.isValid r)
     :schema-url (.getSchemaUrl r)
     :version    (.readVersion r)
     :attributes (datafy (.getAttributes r))})
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

