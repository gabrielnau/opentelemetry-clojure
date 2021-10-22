(ns opentelemetry-clj.attribute
  (:import (io.opentelemetry.api.common Attributes AttributeKey)
           (clojure.lang Keyword)))

(defn string-array
  "Helper to build a Java string array"
  [items]
  (into-array String items))

(defn key->StringOrAttribute [key]
  (if (keyword? key)
    ^String (-> ^Keyword key .-sym .getName)                ;; Keyword -> Symbol -> name
    ^String (str key)))


;(set! *warn-on-reflection* true)

;; Notes for next batch of microbenchmark:
;; - with clojure spec generator, generate once a more complex and rich state
;; - see if there is anything better than doseq and nth
;; - see if inlining key->StringOrAttribute make any sense
(defn ^Attributes build
  "TODO: list cases:

  - key: AttributeKey<V> instance | string | keyword
  - value: of type (or coerciable to type): string, boolean, long, double, string array, boolean array, long array, double array
  - value MUST be correctly typed when key is string or keyword, or reflection can't work.

  NB: if we must type the value, then we always now that type it is ?
  Is there a better way to get the current type and dispatch manually ?

  TODO:
  - note about advise to preallocate
  -
  "
  [attributes-map]
  (let [builder (Attributes/builder)]
    (doseq [a attributes-map]
      (let [k (nth a 0)]
        (if (instance? AttributeKey k)
          (.put builder ^AttributeKey k (nth a 1))
          (.put builder ^String (key->StringOrAttribute k) (nth a 1)))))
    (.build builder)))
