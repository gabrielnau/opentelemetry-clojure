(ns opentelemetry-clj.trace.span
  "Implements OpenTelemetry [Span](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#span)."
  (:require [opentelemetry-clj.attributes :as attribute])
  (:import (io.opentelemetry.api.trace SpanBuilder SpanContext SpanKind Tracer Span StatusCode TraceFlags TraceState)
           (io.opentelemetry.context Context)
           (io.opentelemetry.api.common Attributes)
           (java.util.concurrent TimeUnit)
           (java.time Instant)
           (java.util.function BiConsumer)))

(set! *warn-on-reflection* true)

(defn ^:private ^:no-doc add-link
  ;; SpanContext: https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/io/opentelemetry/api/trace/SpanContext.html
  ;; -> I don't understand if there is a case where we would be manually building one, I don't think so.
  ;; Use cases from specification: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#links-between-spans
  ([span-builder span-context]
   (.addLink ^SpanBuilder span-builder ^SpanContext span-context))
  ([span-builder span-context attributes]
   (.addLink ^SpanBuilder span-builder ^SpanContext span-context ^Attributes (attribute/new attributes))))

(def kinds-mapping
  "See [Upstream spec](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#spankind)."
  ;; Defined statically since it won't change anytime soon
  {:internal SpanKind/INTERNAL
   :client   SpanKind/CLIENT
   :server   SpanKind/SERVER
   :producer SpanKind/PRODUCER
   :consumer SpanKind/CONSUMER})

(defn ^:private ^:no-doc set-kind
  "Fallback to SpanKind/INTERNAL if unknown kind provided, following the behavior of the Java implementation."
  ;; TODO: logs a warning if unknown kind ?
  [span-builder kind]
  (let [span-kind (get kinds-mapping kind)]
    (.setSpanKind ^SpanBuilder span-builder (or span-kind SpanKind/INTERNAL))))

(defn ^:private ^:no-doc set-start-timestamp
  ([^SpanBuilder span-builder ^Instant start-ts] (.setStartTimestamp span-builder start-ts))
  ([^SpanBuilder span-builder ^long ts ^TimeUnit unit] (.setStartTimestamp span-builder ts unit)))

(def ^:no-doc no-parent :none)

(defn new-builder
  "Configure a new SpanBuilder. It doesn't start the span, which could be something done at another moment in code execution path.

  Arguments:
  - `tracer`: an instance of Tracer
  - `opts`: a map of key / values, with keys as strings and values as:
  | key         | description |
  | -------------|-------------|
  | `:name` | *Required*, name of the span
  | `:kind` | Optionnal, see [[set-kind]] for possible values
  | `:parent | Optionnal, if set to `:none`, define span as root span, else provide a `Context` to set as parent. If not set, the value of `Span/current` at the time span is started will be used as parent.
  | `:links | Optionnal, list of Link ?? FIXME
  | `:start-ts | Optionnal, explicit start timestamp for the newly created Span. Can either be an `Instant` or a map of `{:ts (long ...) :unit TimeUnit}`.
  | `:attributes | Optionnal, see [[opentelemetry-clj.attribute/new]]
  "
  [^Tracer tracer opts]
  (let [builder (.spanBuilder ^Tracer tracer ^String (:name opts))]
    (doseq [opt opts]                                       ;; no need to dissoc :name and build another map, we can simply ignore it in the case statement
      (let [key   (nth opt 0)
            value (nth opt 1)]
        (case key
          :kind (set-kind builder value)
          :parent (if (= no-parent value)
                    (.setNoParent ^SpanBuilder builder)
                    (.setParent builder ^Context value))    ;; value must be a Context
          :links (doseq [link value] (apply add-link (cons builder link)))
          :start-ts (if (instance? Instant value)
                      (set-start-timestamp builder value)
                      (set-start-timestamp builder (:ts value) (:unit value)))
          :attributes (.setAllAttributes ^SpanBuilder builder ^Attributes (attribute/new value))
          ;; TODO: if given ignored key, emit a log warn once or remove this default and case will break ?
          :ignored-key)))
    builder))

(defn ^Span start
  "Returns a Span given a SpanBuilder, set the parent Context and starts the timer."
  [span-builder]
  (.startSpan ^SpanBuilder span-builder))

(defn new-span-started [tracer opts]
  (-> (new-builder tracer opts)
    start))

;; Span interface

(defn end!
  ([^Span span] (.end span))
  ([^Span span instant] (.end ^Span span ^Instant instant))
  ([^Span span ^long ts ^TimeUnit unit] (.end span ts unit)))

;; TODO
(defn span-set-attribute [])
(defn span-set-all-attributes [])

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
      (and attributes timestamp)
      (.addEvent ^Span span ^String event-name ^Attributes (attribute/new attributes) ^long (:value timestamp) ^TimeUnit (:unit timestamp))
      (and attributes instant)
      (.addEvent ^Span span ^String event-name ^Attributes (attribute/new attributes) ^Instant instant)
      attributes
      (.addEvent ^Span span ^String event-name ^Attributes (attribute/new attributes))
      timestamp
      (.addEvent ^Span span ^String event-name ^long (:value timestamp) ^TimeUnit (:unit timestamp))
      instant
      (.addEvent ^Span span ^String event-name ^Instant instant)
      :else (.addEvent ^Span span ^String event-name))
    span))


(def status-unset :unset)
(def status-ok :ok)
(def status-error :error)
(def statuses-mapping
  {status-unset StatusCode/UNSET
   status-ok    StatusCode/OK
   status-error StatusCode/ERROR})

(defn set-status
  ;;TODO: warn once wrong status code ?
  ([span status-code]
   (when-let [s (status-code statuses-mapping)]
     (.setStatus ^Span span ^StatusCode s)))
  ([span status-code description]
   (when-let [s (status-code statuses-mapping)]
     (.setStatus ^Span span ^StatusCode s ^String description))))

(defn record-exception
  ([span exception] (.recordException ^Span span ^Throwable exception))
  ([span exception attributes] (.recordException ^Span span ^Throwable exception ^Attributes (attribute/new attributes))))

(defn update-name [span new-name]
  (.updateName ^Span span ^String new-name))

;; NOT Context, but SpanContext
(defn get-span-context [span]
  (.getSpanContext ^Span span))

(defn recording? [span]
  (.isRecording ^Span span))

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