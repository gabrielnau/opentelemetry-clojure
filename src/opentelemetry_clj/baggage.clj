(ns opentelemetry-clj.baggage
  "Implements OpenTelemetry [Baggage](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#baggage-signal) (complete specification [here](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/baggage/api.md))."
  (:refer-clojure :exclude [get])
  (:import (io.opentelemetry.api.baggage Baggage BaggageBuilder BaggageEntryMetadata BaggageEntry)
           (io.opentelemetry.context Context)
           (java.util.function BiConsumer)))

(set! *warn-on-reflection* true)

(defn ^:no-doc metadata->BaggageEntryMetadata [metadata]
  (BaggageEntryMetadata/create ^String metadata))


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

(defn new-baggage [values]
  (-> (Baggage/builder)
    ^BaggageBuilder (put-values values)
    .build))

;; TODO: better naming
;; TODO: duplicate ?
(defn with-values
  "Given an already built `baggage`, returns a new Baggage instance with added (or overriden) values"
  [baggage kvs]
  (let [builder (.toBuilder ^Baggage baggage)]
    (-> ^BaggageBuilder (put-values builder kvs)
       .build)))

(defn is-empty
  "Returns wether the given `baggage` is empty or not."
  [baggage]
  (.isEmpty ^Baggage baggage))

(defn size
  "Returns the number of keys contained in the given `baggage`"
  [baggage]
  (.size ^Baggage baggage))

(defn from-implicit-context []
  (Baggage/current))

(defn from-explicit-context [context]
  (Baggage/fromContext ^Context context))

(defn ^Context set-in-context! [baggage context]
  (.storeInContext ^Baggage baggage ^Context context))

(defn ->map
  "Returns a map from given Baggage keys and values."
  [baggage]
  (let [result (transient {})]
    (.forEach ^Baggage baggage
      (reify
        BiConsumer
        (accept [_ k v]
          (assoc! result k {:value    (.getValue ^BaggageEntry v)
                            :metadata (-> (.getMetadata ^BaggageEntry v)
                                        .getValue)}))))
    (persistent! result)))



(defn get
  "Returns Baggage's value at key, or nil if it doesn't exist.

  Arguments:
  - `baggage`: instance of `Baggage`
  - `k`: the baggage key, a string"
  [baggage k]
  (.getEntryValue ^Baggage baggage ^String k))