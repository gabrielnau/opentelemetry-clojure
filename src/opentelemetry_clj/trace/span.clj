(ns opentelemetry-clj.trace.span
  (:import (io.opentelemetry.api.trace SpanBuilder SpanContext SpanKind Tracer Span)
           (io.opentelemetry.context Context)
           (io.opentelemetry.api.common Attributes)
           (java.util.concurrent TimeUnit)
           (java.time Instant)))

(set! *warn-on-reflection* true)

(defn new-builder [^Tracer tracer span-name]
  (.spanBuilder tracer span-name))

(defn ^Span start [^SpanBuilder span-builder]
  (.startSpan span-builder))

(defn end [^Span span]
  (.end span))

(defn set-parent
  "Sets the parent to use from the specified Context. If not set, the value of Span.current() at startSpan() time will be used as parent.\n\nIf no Span is available in the specified Context, the resulting Span will become a root instance, as if setNoParent() had been called. "
  [^SpanBuilder span-builder ^Context context]
  (.setParent span-builder context))

(defn set-no-parent
  "Sets the option to become a root Span for a new trace. If not set, the value of Span.current() at startSpan() time will be used as parent.\n\nObserve that any previously set parent will be discarded."
  [^SpanBuilder span-builder]
  (.setNoParent span-builder))

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

