(ns opentelemetry-clj.baggage
  "Implements OpenTelemetry [Baggage](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/overview.md#baggage-signal) (complete specification [here](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/baggage/api.md))."
  (:refer-clojure :exclude [get count merge empty?])
  (:import (io.opentelemetry.api.baggage Baggage BaggageBuilder BaggageEntryMetadata BaggageEntry)
           (io.opentelemetry.context Context)
           (java.util.function BiConsumer)))

(set! *warn-on-reflection* true)

(defn ^:private ^:no-doc set-values
  "There shouldn't be the need to use this function directly. TODO: add 'see these fns'"
  [^BaggageBuilder builder values]
  (doseq [v values]
    (let [key      ^String (nth v 0)
          right    (nth v 1)
          value    ^String (:value right)
          metadata ^String (:metadata right)]
      (if (and metadata (not (empty? metadata)))
        (.put builder key value ^BaggageEntryMetadata (BaggageEntryMetadata/create metadata))
        (.put builder key value))))
  builder)

(defn new
  "
  FIXME: Given a BaggageBuilder instance and a values maps, returns the same BaggageBuilder instance with added values.

  Arguments:
  - `builder`: an instance of BaggageBuilder
  - `values`: a map, with keys as strings and values as:
  | key         | description |
  | -------------|-------------|
  | `:value` | Value of the given `key`, must be a String
  | `:metadata` | Optionnal, This field is specified as a String having \"no semantic meaning. Left opaque to allow for future functionality.\" in the [specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/baggage/api.md).

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
  [values]
  (-> (Baggage/builder)
    ^BaggageBuilder (set-values values)
    .build))

(defn empty?
  "Returns wether the given `baggage` is empty or not."
  [^Baggage baggage]
  (.isEmpty baggage))

(defn count
  "Returns the number of keys contained in the given `baggage`"
  [^Baggage baggage]
  (.size baggage))

(defn from-implicit-context []
  (Baggage/current))

(defn from-explicit-context [^Context context]
  (Baggage/fromContext context))

(defn ^Context set-in-context! [^Baggage baggage ^Context context]
  (.storeInContext baggage context))

(defn ->map
  "Returns a map from given Baggage keys and values."
  [^Baggage baggage]
  (let [result (transient {})]
    (.forEach baggage
      (reify
        BiConsumer
        (accept [_ k v]
          (assoc!
            result
            k
            {:value    (.getValue ^BaggageEntry v)
             :metadata (-> (.getMetadata ^BaggageEntry v)
                         .getValue)}))))
    (persistent! result)))


(defn get
  "Returns Baggage's value at key, or nil if it doesn't exist.

  Arguments:
  - `baggage`: instance of `Baggage`
  - `k`: the baggage key, a string"
  [^Baggage baggage ^String k]
  (.getEntryValue baggage k))