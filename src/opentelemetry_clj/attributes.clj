(ns opentelemetry-clj.attributes
  "Implements OpenTelemetry [Attributes](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/attribute-naming.md)."
  (:import (io.opentelemetry.api.common Attributes AttributeKey AttributeType)
           (java.util.function BiConsumer)))

(defn string-array
  "Utility function to create a Java String[] from a given list of strings.

  Example:
  ```clojure
  (string-array [\"foo\" \"bar\"])
  => #object[\"[Ljava.lang.String;\"
  ```"
  [items]
  (into-array String items))


; (set! *warn-on-reflection* true)

(defn ^Attributes new
  "Returns a new instance of `Attributes` from the given key-values pairs in `attributes-map`.

  It is recommended to pre-allocate your AttributeKey keys if possible. See [[new-key]].

  Arguments:
  - `attributes-map` keys: Can be an instance of `AttributeKey` or a string. If providing `AttributeKey` it will avoid runtime reflection.
  - `attributes-map` values: A instace of `string` | `boolean` | `long` | `double` | `string array` | `boolean array` | `long array` | `double array`. If the provided key is an `AttributeKey`, then the type must match.

  Example:
  ```clojure
  (opentelemetry-clj.attribute/new
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
  [attributes-map]
  (let [builder (Attributes/builder)]
    (doseq [a attributes-map]
      (let [k (nth a 0)]
        (if (instance? AttributeKey k)
          (.put builder ^AttributeKey k (nth a 1))
          (.put builder ^String k (nth a 1)))))
    (.build builder)))

(defn attribute-key
  "Return an AttributeKey instance typed to the expected matching attribute's value.

  It is recommended to pre-allocate your AttributeKey keys if possible.

  Arguments:
  - `key-name`: A string. See [conventions](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/attribute-naming.md)
  - `value-type`: It can be either: `:string` | `:boolean` | `:long` | `:double` | `:string-array` | `:boolean-array` | `:long-array` | `:double-array`
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

(def- AttributeType->clojurize-fn
  {AttributeType/STRING        identity
   AttributeType/BOOLEAN       identity
   AttributeType/LONG          identity
   AttributeType/DOUBLE        identity
   AttributeType/STRING_ARRAY  (fn [x] (into [] x))
   AttributeType/BOOLEAN_ARRAY (fn [x] (into [] x))
   AttributeType/LONG_ARRAY    (fn [x] (into [] x))
   AttributeType/DOUBLE_ARRAY  (fn [x] (into [] x))})

(defn ->map [attributes]
  (let [result (transient {})]
    (.forEach attributes
      (reify
        BiConsumer
        (accept [_ k v]
          (let [attribute-type     (.getType k)
                clojurize-value-fn (get AttributeType->clojurize-fn attribute-type)]
            (assoc! result (.getKey k) (clojurize-value-fn v))))))
    (persistent! result)))