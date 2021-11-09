(ns opentelemetry-clj.trace.span
  "Implements OpenTelemetry [Span](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#span)."
  (:require [opentelemetry-clj.attributes :as attributes])
  (:import (io.opentelemetry.api.trace SpanBuilder SpanContext SpanKind Tracer Span StatusCode TraceFlags TraceState)
           (io.opentelemetry.context Context)
           (io.opentelemetry.api.common Attributes)
           (java.util.concurrent TimeUnit)
           (java.time Instant)
           (java.util.function BiConsumer)))

(set! *warn-on-reflection* true)

(def kinds-mapping
  "See [Upstream spec](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#spankind)."
  ;; Defined statically since it won't change anytime soon
  {:internal SpanKind/INTERNAL
   :client   SpanKind/CLIENT
   :server   SpanKind/SERVER
   :producer SpanKind/PRODUCER
   :consumer SpanKind/CONSUMER})

(def ^:no-doc no-parent :none)

(defn ^SpanBuilder new-builder
  "Configure a new SpanBuilder. It doesn't start the span, which could be something done at another moment in code execution path.

  Arguments:
  - `tracer`: an instance of Tracer
  - `opts`: a map of key / values, with keys as strings and values as:
  | key         | description |
  | -------------|-------------|
  | `:name` | *Required*, name of the span
  | `:kind` | Optionnal, can be [`:internal` | `:client` | `:server` | `:producer` | `:consumer`], fallbacks to `:internal` if another value given
  | `:parent | Optionnal, if set to `:none`, define span as root span, else provide a `Context` to set as parent. If not set, the value of `Span/current` at the time span is started will be used as parent.
  | `:links | Optionnal, list of linked `SpanContext`, in the shape of `{:span-context SpanContext :attributes Attributes}`. see the [specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#links-between-spans)
  | `:start-ts | Optionnal, explicit start timestamp for the newly created Span. Can either be an `Instant` or a map of `{:ts (long ...) :unit TimeUnit}`.
  | `:attributes | Optionnal, see [[opentelemetry-clj.attributes/new]]
  "
  [^Tracer tracer opts]
  (let [builder (.spanBuilder ^Tracer tracer ^String (:name opts))]
    (doseq [opt opts]                                       ;; no need to dissoc :name and build another map, we can simply ignore it in the case statement
      (let [key   (nth opt 0)
            value (nth opt 1)]
        (case key
          :kind (.setSpanKind ^SpanBuilder builder (get kinds-mapping value SpanKind/INTERNAL)) ;; Fallback to SpanKind/INTERNAL if unknown kind provided, following the behavior of the Java implementation.
          :parent (if (= no-parent value)
                    (.setNoParent ^SpanBuilder builder)
                    (.setParent builder ^Context value))    ;; value must be a Context
          :links (doseq [link value]
                   (if (:attributes link)
                     (.addLink builder ^SpanContext (:span-context link) ^Attributes (attributes/new (:attributes link)))
                     (.addLink builder ^SpanContext (:span-context link) ^Attributes (attributes/new (:attributes link)))))
          :start-ts (if (instance? Instant value)
                      (.setStartTimestamp builder ^Instant value)
                      (.setStartTimestamp builder ^long (:ts value) ^TimeUnit (:unit value)))
          :attributes (.setAllAttributes builder ^Attributes (attributes/new value))
          :unknown-key-silently-ignored)))                  ;; Don't break if unknown arg given TODO: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/error-handling.md#basic-error-handling-principles
    builder))

(defn ^Span start
  "Returns a Span given a SpanBuilder, set the parent Context and starts the timer."
  [span-builder]
  (.startSpan ^SpanBuilder span-builder))

(defn ^Span new-started [tracer opts]
  (-> (new-builder tracer opts)
    start))

(defn end
  ([^Span span]
   (.end span))
  ([^Span span ^Instant instant]
   (.end span instant))
  ([^Span span ^long ts ^TimeUnit unit]
   (.end span ts unit)))

;; TODO: decide on implementation
(defn set-attribute [])
(defn set-all-attributes [])

(defn add-event
  "Return the given `Span` with an event added.

  Arguments:
  - `span`: Target Span of event
  - `opts`: a map of key / values, with keys as strings and values as:
  | key         | description |
  | -------------|-------------|
  | `:name` | *Required*, name of the event
  | `:attributes` | Optionnal, map of `Attributes`
  | `:at-instant` | Optionnal, event's timestamp with an instance of Java `Instant`. Can't be
  | `:at-timestamp` | Optionnal, event's timestamp with a map of `{:value Long :unit TimeUnit}`
  "
  [span opts]
  (let [event-name (:name opts)
        instant    (:at-instant opts)
        timestamp  (:at-timestamp opts)
        attributes (:attributes opts)]
    (cond
      ;; Order matter
      (and attributes timestamp)
      (.addEvent ^Span span ^String event-name ^Attributes (attributes/new attributes) ^long (:value timestamp) ^TimeUnit (:unit timestamp))
      (and attributes instant)
      (.addEvent ^Span span ^String event-name ^Attributes (attributes/new attributes) ^Instant instant)
      attributes
      (.addEvent ^Span span ^String event-name ^Attributes (attributes/new attributes))
      timestamp
      (.addEvent ^Span span ^String event-name ^long (:value timestamp) ^TimeUnit (:unit timestamp))
      instant
      (.addEvent ^Span span ^String event-name ^Instant instant)
      :else (.addEvent ^Span span ^String event-name))
    span))


(def status-unset :unset)
(def status-ok :ok)
(def status-error :error)
(def status-mapping {status-unset StatusCode/UNSET
                     status-ok    StatusCode/OK
                     status-error StatusCode/ERROR})
(def reverse-status-mapping (clojure.set/map-invert status-mapping))

(defn keyword->StatusCode [x]
  (get status-mapping x))

(defn StatusCode->keyword [status-code]
  (get reverse-status-mapping status-code))

(defn set-status
  "It will silently do nothing if status is unknown, following spec guideliens"
  ([span status-code]
   (when-let [s (keyword->StatusCode status-code)]
     (.setStatus ^Span span ^StatusCode s)))
  ([span status-code description]
   (when-let [s (keyword->StatusCode status-code)]
     (.setStatus ^Span span ^StatusCode s ^String description))))

(defn record-exception
  ([^Span span ^Throwable exception]
   (.recordException span exception))
  ([^Span span ^Throwable exception attributes]
   (.recordException span exception ^Attributes (attributes/new attributes))))

(defn update-name [^Span span ^String new-name]
  (.updateName span new-name))

(defn get-span-context
  "NOT Context, but SpanContext

  A class that represents a span context. A span context contains the state that must propagate to child Spans and across process boundaries. It contains the identifiers (a trace_id and span_id) associated with the Span and a set of options (currently only whether the context is sampled or not), as well as the traceState and the remote flag. \n    Implementations of this interface *must* be immutable and have well-defined value-based equals/hashCode implementations. If an implementation does not strictly conform to these requirements, behavior of the OpenTelemetry APIs and default SDK cannot be guaranteed. It is strongly suggested that you use the implementation that is provided here via create(String, String, TraceFlags, TraceState) or createFromRemoteParent(String, String, TraceFlags, TraceState).
  "
  [^Span span]
  (.getSpanContext span))

(defn trace-flags->map [^TraceFlags trace-flags]
  {:hex      (.asHex trace-flags)
   :sampled? (.isSampled trace-flags)})

(defn trace-state->map [^TraceState trace-state]
  (let [result (transient {})]
    (.forEach trace-state
      (reify
        BiConsumer
        (accept [_ k v]
          (assoc! result k v))))
    (persistent! result)))

(defn span-context->map
  [^SpanContext span-context]
  {:trace-id    (.getTraceId span-context)
   :span-id     (.getSpanId span-context)
   :sampled?    (.isSampled span-context)
   :valid?      (.isValid span-context)
   :remote?     (.isRemote span-context)
   :trace-flags (trace-flags->map (.getTraceFlags span-context))
   :trace-state (trace-state->map (.getTraceState span-context))})

;; storeInContext
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