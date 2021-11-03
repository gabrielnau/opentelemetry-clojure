(ns opentelemetry-clj.test-generators
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.test.check.generators :as gen]
    [opentelemetry-clj.attributes :as attributes]
    [opentelemetry-clj.context :as context]
    [opentelemetry-clj.sdk.datafy]
    [opentelemetry-clj.trace.span :as span])
  (:import (io.opentelemetry.api.trace TraceFlags TraceState SpanContext)))

;; General purpose

(def string-gen
  (gen/fmap
    #(str %)
    gen/uuid))
;(gen/fmap #(-> (str/join %)
;             (str/replace #" " ""))
;  ;(gen/resize gen/small-integer) ;; TODO: upper bound ?
;  (gen/not-empty
;    (gen/vector gen/char-ascii 40))))

(def simple-double (gen/double* {:infinite? false
                                 :NaN?      false}))

;; Attribute

(def type->generator
  {:string        string-gen
   :boolean       gen/boolean
   :long          gen/large-integer
   :double        simple-double
   :string-array  (gen/fmap attributes/string-array (gen/vector string-gen 1 10))
   :boolean-array (gen/fmap boolean-array (gen/vector gen/boolean 1 10))
   :long-array    (gen/fmap long-array (gen/vector gen/large-integer 1 50))
   :double-array  (gen/fmap long-array (gen/vector simple-double 1 50))})

(def attribute-types-list (keys type->generator))

(def attribute-type-gen (gen/elements attribute-types-list))

(defn attribute-key-gen [attribute-type]
  (gen/such-that
    #(not (empty? (datafy %)))
    (gen/one-of
      [(gen/not-empty
         string-gen)
       (gen/fmap #(attributes/attribute-key % attribute-type)
         (gen/not-empty
           string-gen))])))

(defn attribute-value-gen [type]
  (gen/such-that
    #(not (str/blank? (str %)))
    (get type->generator type)))

(def attributes-gen
  (gen/fmap
    ;; FIXME: we shall use gen/map such as there is no key conflicts, and we loose proper shrink
    ;; But we have to define dynamic keys, this is not supported by gen/hash-map
    #(into {} %)
    (gen/vector
      (gen/let [type attribute-type-gen
                key (attribute-key-gen type)
                value (attribute-value-gen type)]
        [key value])
      1 1)))                                                ;; FIXME: see the upperbound limit

;; Baggage
;; TODO: move to test.check gen ?

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

;; Context

(defn context-gen []
  (gen/return (context/root)))

;; Span

(def span-kind-gen
  (gen/elements (keys span/kinds-mapping)))

(def span-name-gen
  (gen/not-empty
    string-gen))

(def span-parent-gen
  (gen/one-of
    [(gen/return span/no-parent)
     (context-gen)]))

(def span-links-gen
  (gen/return []))

(def span-gen
  (gen/hash-map
    :name span-name-gen
    :kind span-kind-gen
    :parent span-parent-gen
    :links span-links-gen
    :attributes attributes-gen))

(def trace-flags-gen
  (gen/return (TraceFlags/getDefault)))                     ;; no need for now to produce anything else

(def trace-state-gen
  (gen/return (TraceState/getDefault)))                     ;; no need for now to produce anything else

(defn span-context-from-map [{:keys [trace-id span-id trace-flags trace-state]}]
  (SpanContext/create trace-id span-id trace-flags trace-state))

(def span-context-gen
  (gen/hash-map
    :trace-id string-gen                                    ;; FIXME: get more close to the specification, see IdGenerator
    :span-id string-gen
    :trace-flags trace-flags-gen
    :trace-state trace-state-gen))



