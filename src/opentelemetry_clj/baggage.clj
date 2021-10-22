(ns opentelemetry-clj.baggage
  (:import (io.opentelemetry.api.baggage Baggage BaggageBuilder BaggageEntryMetadata)))
;; spec: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/baggage/api.md

(set! *warn-on-reflection* true)

;{:key {:value "asdas" :metadata ""}}
(defn metadata->BaggageEntryMetadata [metadata]
  (BaggageEntryMetadata/create ^String metadata))

(defn build [values]
  (let [builder ^BaggageBuilder (Baggage/builder)]
    (doseq [v values]
      (let [key      (nth v 0)
            value    (:value (nth v 1))
            metadata (:metadata (nth v 1))]
        (if metadata
          (.put builder ^String key ^String value)
          (.put builder ^String key ^String value ^BaggageEntryMetadata (metadata->BaggageEntryMetadata metadata)))))
    (.build builder)))

(defn get-key [baggage key]
  (.getEntryValue ^Baggage baggage ^String key))

(defn empty? [baggage]
  (.isEmpty ^Baggage baggage))

(defn size [baggage]
  (.size ^Baggage baggage))

;;; datafy asMap
;;; clojure spec
;; basic bench