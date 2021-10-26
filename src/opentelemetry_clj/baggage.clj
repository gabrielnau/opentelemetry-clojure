(ns opentelemetry-clj.baggage
  "Implements OpenTelemetry [Baggage](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#baggage-signal) (complete specification [here](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/baggage/api.md))."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import (io.opentelemetry.api.baggage Baggage BaggageBuilder BaggageEntryMetadata)))

(set! *warn-on-reflection* true)

(defn ^:no-doc metadata->BaggageEntryMetadata [metadata]
  (BaggageEntryMetadata/create ^String metadata))

;; TODO: where to put these specs using in tests mainly ?
(s/def ::non-blank-string (s/and string? (complement str/blank?)))
(s/def ::value ::non-blank-string)
(s/def ::metadata ::non-blank-string)
(s/def ::arguments-without-metadata
  (s/map-of
    ::non-blank-string
    (s/keys :req-un [::value])))
(s/def ::arguments-with-metadata
  (s/map-of
    ::non-blank-string
    (s/keys :req-un [::value ::metadata])))
(s/def ::arguments
  (s/map-of
    ::non-blank-string
    (s/keys :req-un [::value] :opt-un [::metadata])))

(defn put-values
  "There shouldn't be the need to use this function directly. TODO: add 'see these fns'
  Given a BaggageBuilder instance and a values maps, returns the same BaggageBuilder instance with added values.

  Arguments:
  - `builder`: an instance of BaggageBuilder
  - `values`: a map of key / values, with keys as strings and values as:
  | key         | description |
  | -------------|-------------|
  | `:value` | Value of the given `key`
  | `:metadata` | Optionnal, This field is specified as having \"no semantic meaning. Left opaque to allow for future functionality.\" in the [specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/baggage/api.md).

  Example:
  ```clojure
  (put-values
    (Baggage/builder)
    {
      \"app.name\" {:value \"checkout\"}
      \"app.version\" {:value \"0.10.0\" :metadata \"some-metadata-string\"}
    })
  ```

  "
  [builder values]
  (doseq [v values]
    (let [key      (nth v 0)
          right    (nth v 1)
          value    (:value right)
          metadata (:metadata right)]
      (if (and metadata (not (empty? metadata)))
        (.put ^BaggageBuilder builder ^String key ^String value ^BaggageEntryMetadata (metadata->BaggageEntryMetadata metadata))
        (.put ^BaggageBuilder builder ^String key ^String value))))
  builder)

;; TODO: better naming
(defn with-values
  "Given an already built `baggage`, returns a new Baggage instance with added (or overriden) values"
  [baggage kvs]
  (let [builder (.toBuilder ^Baggage baggage)]
    (-> ^BaggageBuilder (put-values builder kvs)
       .build)))

(defn get-key
  "Returns the given Baggage's key, or nil if it doesn't exist.

  Arguments:
  - `baggage`: instance of `Baggage`
  - `key`: a string"
  [baggage key]
  (.getEntryValue ^Baggage baggage ^String key))

(defn is-empty
  "Returns wether the given `baggage` is empty or not."
  [baggage]
  (.isEmpty ^Baggage baggage))

(defn size
  "Returns the number of keys contained in the given `baggage`"
  [baggage]
  (.size ^Baggage baggage))
