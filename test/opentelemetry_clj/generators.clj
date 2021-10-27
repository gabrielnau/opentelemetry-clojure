(ns opentelemetry-clj.generators
  (:require
    [clojure.test.check.generators :as gen]
    [opentelemetry-clj.datafy]
    [clojure.datafy :refer [datafy]]
    [opentelemetry-clj.attribute :as attribute]
    [clojure.string :as str]
    [clojure.spec.alpha :as s]))

;; General purpose

(def string-generator
  (gen/fmap #(-> (str/join %)
               (str/replace #" " ""))
    ;(gen/resize gen/small-integer) ;; TODO: upper bound ?
    (gen/not-empty
      (gen/vector gen/char-ascii 40))))

(def simple-double (gen/double* {:infinite? false
                                 :NaN?      false}))

;; Attribute

(def type->generator
  {:string        string-generator
   :boolean       gen/boolean
   :long          gen/large-integer
   :double        simple-double
   :string-array  (gen/fmap attribute/string-array (gen/vector string-generator 1 10))
   :boolean-array (gen/fmap boolean-array (gen/vector gen/boolean 1 10))
   :long-array    (gen/fmap long-array (gen/vector gen/large-integer 1 50))
   :double-array  (gen/fmap long-array (gen/vector simple-double 1 50))})

(def attribute-types-list (keys type->generator))

(def attribute-type-generator (gen/elements attribute-types-list))

(defn attribute-key-generator [attribute-type]
  (gen/such-that
    #(not (empty? (datafy %)))
    (gen/one-of
      [string-generator
       (gen/fmap #(attribute/new-key % attribute-type) string-generator)])))

(defn attribute-value-generator [type]
  (get type->generator type))

(defn attributes-map-generator []
  (gen/fmap
    #(into {} %)
    (gen/vector
      (gen/let [type attribute-type-generator
                key (attribute-key-generator type)
                value (attribute-value-generator type)]
        [key value])
      1
      1)))                                                ;; TODO: see the upperbound limit

;; Baggage

(s/def ::non-blank-string (s/and string? (complement str/blank?)))
(s/def ::value ::non-blank-string)
(s/def ::metadata ::non-blank-string)

(s/def :baggage/arguments
  (s/map-of
    ::non-blank-string
    (s/keys :req-un [::value] :opt-un [::metadata])))
(s/def :baggage/arguments-without-metadata
  (s/map-of
    ::non-blank-string
    (s/keys :req-un [::value])))
(s/def :baggage/arguments-with-metadata
  (s/map-of
    ::non-blank-string
    (s/keys :req-un [::value ::metadata])))

;; Ressource

