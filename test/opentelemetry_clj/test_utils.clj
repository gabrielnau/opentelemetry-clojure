(ns opentelemetry-clj.test-utils
  (:require [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]

            [clojure.datafy :refer [datafy]]
            [opentelemetry-clj.datafy]
            [opentelemetry-clj.trace.span :as span])
  (:import (io.opentelemetry.sdk.trace SdkTracerProvider)
           (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)))


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
  (-> (span/new *tracer* options)
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