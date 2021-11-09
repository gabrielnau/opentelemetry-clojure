(ns opentelemetry-clj.test-utils
  (:require
    [clojure.datafy :refer [datafy]]
    [portal.api :as portal]
    [opentelemetry-clj.sdk.datafy]
    [opentelemetry-clj.trace.span :as span]
    [matcher-combinators.matchers :as m])
  (:import (io.opentelemetry.sdk.trace SdkTracerProvider)
           (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)
           (io.opentelemetry.semconv.trace.attributes SemanticAttributes)
           (java.time Instant)))


(def ^:dynamic *tracer*)
(def ^:dynamic *memory-exporter*)

(comment
  (defmacro with-exporter-reset
    [& body]
    `(try
       ~@body
       (finally
         (.reset *memory-exporter*))))

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


(defn exception-span-event? [{:keys [name]}]
  (= name SemanticAttributes/EXCEPTION_EVENT_NAME))

(defn span-event->exception-info [{:keys [attributes]}]
  {:message    (get attributes (str SemanticAttributes/EXCEPTION_MESSAGE))
   :type       (get attributes (str SemanticAttributes/EXCEPTION_TYPE))
   :stacktrace (get attributes (str SemanticAttributes/EXCEPTION_STACKTRACE))})

(defn span->recorded-exceptions [span]
  ;; exceptions are stored as events with ex-info stored as attributes"
  (->> span
    datafy
    :events
    (filter exception-span-event?)
    (map span-event->exception-info)))


(defn spans []
  (.getFinishedSpanItems *memory-exporter*))

(defn built-span [options]
  (-> (span/new-builder *tracer* options)
    span/start))

(defn closed-span [options]
  (let [span (built-span options)]
    (span/end span)
    span))

;; Attributes

(defn attribute-arguments->map [args]
  (reduce
    (fn [acc [k v]] (assoc acc (datafy k) (datafy v)))
    {}
    args))

(comment
  ;; todo: integrate portal with kaocha hooks
  (add-tap #'portal/submit))

(defn start-portal! []
  (portal/open)
  (add-tap #'portal/submit))

(defn instant->epoch-nanos [instant]
  (+
    (* 1000000000 (.getEpochSecond (Instant/now)))
    (.getNano instant)))

(defn matcher-nano-max-100ms-ago []
  (m/within-delta
    100000000                                             ;; 1ms = 1_000_000 nanoseconds
    (instant->epoch-nanos (Instant/now))))