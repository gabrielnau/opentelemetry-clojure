(ns opentelemetry-clj.trace.span
  (:require [opentelemetry-clj.attribute :as attribute])
  (:import (io.opentelemetry.api.trace SpanBuilder SpanContext SpanKind Tracer Span StatusCode)
           (io.opentelemetry.context Context)
           (io.opentelemetry.api.common Attributes AttributeKey)
           (java.util.concurrent TimeUnit)
           (java.time Instant)))

(set! *warn-on-reflection* true)

;; Span Builder interface

(defn new-builder [tracer span-name]
  (.spanBuilder ^Tracer tracer ^Span span-name))

(defn ^Span start [^SpanBuilder span-builder]
  (.startSpan span-builder))

(defn set-parent
  "Sets the parent to use from the specified Context. If not set, the value of Span.current() at startSpan() time will be used as parent.\n\nIf no Span is available in the specified Context, the resulting Span will become a root instance, as if setNoParent() had been called. "
  [^SpanBuilder span-builder ^Context context]
  (.setParent span-builder context))

(defn set-no-parent
  "Sets the option to become a root Span for a new trace. If not set, the value of Span.current() at startSpan() time will be used as parent.\n\nObserve that any previously set parent will be discarded."
  [^SpanBuilder span-builder]
  (.setNoParent span-builder))

;; SpanContext: https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/io/opentelemetry/api/trace/SpanContext.html
;; I don't understand if there is a case where we would be manually building one, I don't think so.
(defn add-link
  "Adds a link to the newly created Span.\n\nLinks are used to link Spans in different traces. Used (for example) in batching operations, where a single batch handler processes multiple requests from different traces or the same trace. "
  ([^SpanBuilder span-builder ^SpanContext span-context]
   (.addLink span-builder span-context))
  ([^SpanBuilder span-builder ^SpanContext span-context ^Attributes attributes]
   (.addLink span-builder span-context attributes)))

(def kinds-mapping
  "More doc about it can be found here: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#spankind
  Defined statically since it won't change anytime soon"
  {:internal SpanKind/INTERNAL
   :client   SpanKind/CLIENT
   :server   SpanKind/SERVER
   :producer SpanKind/PRODUCER
   :consumer SpanKind/CONSUMER})

(defn set-kind
  "kind must be off :internal, ..... TODO and fallback to INTERNAL and logs ?
  Fallback to SpanKind/INTERNAL if unknown kind provided, following the behavior of the original implementation which always fallbacks to INTERNAL.
  "
  [^SpanBuilder span-builder kind]
  (let [span-kind (get kinds-mapping kind)]
    ;; TODO: logs a warning if unknown kind ?
    (.setSpanKind span-builder (or span-kind SpanKind/INTERNAL))))

(defn set-start-timestamp
  "From javadoc: Sets an explicit start timestamp for the newly created Span.\n\nLIRInstruction.Use this method to specify an explicit start timestamp. If not called, the implementation will use the timestamp value at startSpan() time, which should be the default case.\n\nImportant this is NOT equivalent with System.nanoTime()."
  ([^SpanBuilder span-builder ^Instant start-ts] (.setStartTimestamp span-builder start-ts))
  ([^SpanBuilder span-builder ^long ts ^TimeUnit unit] (.setStartTimestamp span-builder ts unit)))

(defn set-all-attributes
  [span-builder attributes]
  (.setAllAttributes ^SpanBuilder span-builder
    (attribute/build attributes)))

;; Idea: option in map to validate options are consistent ? could be used during test
(defn ^SpanBuilder build
  "Required: :name
  Doesn't start the span, see fn start
  "
  [^Tracer tracer opts]
  (let [builder (new-builder tracer (:name opts))]
    (doseq [opt opts]                                       ;; no need to dissoc :name and build another map, we can simply ignore it in the case statement
      (let [key   (nth opt 0)
            value (nth opt 1)]
        (case key
          :kind (set-kind builder value)
          :parent (if (= :none value)
                    (set-no-parent builder)
                    (set-parent builder value))             ;; value must be a Context
          :links (doseq [link value] (apply add-link (cons builder link)))
          :start-ts (if (instance? Instant value)
                      (set-start-timestamp builder value)
                      (apply set-start-timestamp builder (:ts value) (:unit value)))
          :attributes (set-all-attributes builder value)
          :ignored-key)))                                   ;; TODO: if given ignored key, emit a log warn once ?
    builder))

;; Span interface

(defn end
  ([^Span span] (.end span))
  ([^Span span instant] (.end ^Span span ^Instant instant))
  ([^Span span ^long ts ^TimeUnit unit] (.end span ts unit))) ;

(defn span-set-attribute [])
(defn span-set-all-attributes [])


(defn add-event
  ([span event-name] (.addEvent ^Span span ^String event-name))
  ([span event-name instant] (.addEvent ^Span span ^String event-name ^Instant instant))
  ([span event-name ts unit] (.addEvent ^Span span ^String event-name ^long ts ^TimeUnit unit)))

(defn add-event-with-attributes
  ([span event-name attributes] (.addEvent ^Span span ^String event-name ^Attributes attributes))
  ([span event-name attributes instant] (.addEvent ^Span span ^String event-name ^Attributes attributes ^Instant instant))
  ([span event-name attributes ts unit] (.addEvent ^Span span ^String event-name ^Attributes attributes ^long ts ^TimeUnit unit)))

(def status-unset :unset)
(def status-ok :ok)
(def status-error :error)
(def statuses-mapping
  {status-unset StatusCode/UNSET
   status-ok    StatusCode/OK
   status-error StatusCode/ERROR})

(defn set-status
  ([span status-code]
   (let [s (status-code statuses-mapping)]
     (if s
       (.setStatus ^Span span ^StatusCode s))))
  ([span status-code description]
   (let [s (status-code statuses-mapping)]
     (if s
       (.setStatus ^Span span ^StatusCode s ^String description)))))
;;TODO: warn once wrong status code ?)))

;
(defn record-exception
  ([span exception] (.recordException ^Span span ^Throwable exception))
  ([span exception attributes] (.recordException ^Span span ^Throwable exception ^Attributes (attribute/build attributes))))

(defn update-name [span new-name]
  (.updateName ^Span span ^String new-name))

(defn get-context [span]
  (.getContext ^Span span))

(defn recording? [span]
  (.isRecording ^Span span))

;; storeInContext