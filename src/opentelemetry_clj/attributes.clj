(ns opentelemetry-clj.attributes
  "Implements OpenTelemetry [Attributes](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/attribute-naming.md)."
  (:refer-clojure :exclude [get])
  (:require [clojure.string :as str])
  (:import (io.opentelemetry.api.common Attributes AttributeKey AttributeType)
           (java.util.function BiConsumer)))

(defn string-array
  "Utility function to create a Java String[] from a given list of strings. Used to build an Attribute value.

  Example:
  ```clojure
  (string-array [\"foo\" \"bar\"])
  => #object[\"[Ljava.lang.String;\"
  ```"
  [items]
  (into-array String items))


(defn ^Attributes new
  "Returns a new instance of `Attributes` from the given key-values pairs in `attributes-map`.

  TODO: document the behavior if given Attributes instance
  It is recommended to pre-allocate your AttributeKey keys if possible. See [[new-key]].
  FIXME:

   - `attributes` keys: Can be an instance of `AttributeKey` or a string. If providing `AttributeKey` it will avoid runtime reflection.
   - `attributes` values: A instace of `string` | `boolean` | `long` | `double` | `string array` | `boolean array` | `long array` | `double array`. If the provided key is an `AttributeKey`, then the type must match.

  Arguments:
  - `attributes`: it can be an instance of `Attributes` or a Clojure map. If it's an instance of `Attributes`, this function is a no-op and returns it. If

  Example:
  ```clojure
  (opentelemetry-clj.attributes/new
    {
      ;; with some keys as string
      \"org.acme.ab_testing_a\" \"some-string\"
      \"org.acme.ab_testing_b\" (boolean false)
      \"org.acme.ab_testing_c\" (long 123)
      \"org.acme.ab_testing_d\" (double 123)
      \"org.acme.ab_testing_e\" (opentelemetry-clj.attributes/string-array [\"foo\" \"bar\"])
      \"org.acme.ab_testing_f\" (boolean-array [true false])
      \"org.acme.ab_testing_g\" (long-array [123 456])
      \"org.acme.ab_testing_h\" (double-array [123 456])

      ;; with some keys as AttributeKey
      (attribute-key \"org.acme.ab_testing_i\" :string) \"some-string\"
      (attribute-key \"org.acme.ab_testing_j\" :boolean) (boolean false)
      (attribute-key \"org.acme.ab_testing_k\" :long) (long 123)
      (attribute-key \"org.acme.ab_testing_l\" :double) (double 123)
      (attribute-key \"org.acme.ab_testing_m\" :string-array) (opentelemetry-clj.attributes/string-array [\"foo\" \"bar\"])
      (attribute-key \"org.acme.ab_testing_n\" :boolean-array) (boolean-array [true false])
      (attribute-key \"org.acme.ab_testing_o\" :long-array) (long-array [123 456])
      (attribute-key \"org.acme.ab_testing_p\" :double-array) (double-array [123 456])
    })
  ```
  "
  [attributes]
  (if (instance? Attributes attributes)
    attributes
    (let [builder (Attributes/builder)]
      (doseq [a attributes]
        (let [k (nth a 0)]
          (if (instance? AttributeKey k)
            (.put builder ^AttributeKey k (nth a 1))
            (.put builder ^String k (nth a 1)))))
      (.build builder))))

(defn new-key
  "Return an AttributeKey instance typed to the expected matching attribute's value.

  It is recommended to pre-allocate AttributeKey if possible.

  Arguments:
  - `key-name`: Required, a string. See [conventions](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/attribute-naming.md)
  - `value-type`: Required, must be one of: `:string` | `:boolean` | `:long` | `:double` | `:string-array` | `:boolean-array` | `:long-array` | `:double-array`
  "
  [key-name value-type]
  (case value-type
    :string (AttributeKey/stringKey ^String key-name)
    :boolean (AttributeKey/booleanKey ^String key-name)
    :long (AttributeKey/longKey ^String key-name)
    :double (AttributeKey/doubleKey ^String key-name)
    :string-array (AttributeKey/stringArrayKey ^String key-name)
    :boolean-array (AttributeKey/booleanArrayKey ^String key-name)
    :long-array (AttributeKey/longArrayKey ^String key-name)
    :double-array (AttributeKey/doubleArrayKey ^String key-name)))


(defn AttributeType->keyword [x]
  (-> x str str/lower-case (str/replace #"_" "-") keyword))

(defn clojurize-attribute-value [attribute-type value]
  (condp = attribute-type
    AttributeType/STRING        value
    AttributeType/BOOLEAN       value
    AttributeType/LONG          value
    AttributeType/DOUBLE        value
    AttributeType/STRING_ARRAY  (into [] value)
    AttributeType/BOOLEAN_ARRAY (into [] value)
    AttributeType/LONG_ARRAY    (into [] value)
    AttributeType/DOUBLE_ARRAY  (into [] value)))

(defn ->map
  "Returns given `attributes` as data."
  [^Attributes attributes]
  (let [result (transient {})]
    (.forEach attributes
      (reify
        BiConsumer
        (accept [_ k v]
          (assoc! result (.getKey k) (clojurize-attribute-value (.getType k) v)))))
    (persistent! result)))


(defn get
  "Returns the value for the given `AttributeKey`, or null if not found.

  If you don't have the `AttributeKey` instance, but a string, you could go through the map representation this:
  ```clojure
  (let [attribute-key \"foo\"]
    (get
      (attributes/->map attributes)
      attribute-key))
  ```"
  [^Attributes attributes ^AttributeKey attribute-key]
  (.get attributes attribute-key))
