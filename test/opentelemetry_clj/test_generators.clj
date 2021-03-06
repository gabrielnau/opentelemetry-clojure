(ns opentelemetry-clj.test-generators
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.string :as str]
    [clojure.test.check.generators :as gen]
    [opentelemetry-clj.attributes :as attributes]
    [opentelemetry-clj.context :as context]
    [opentelemetry-clj.sdk.datafy]
    [opentelemetry-clj.trace.span :as span])
  (:import (io.opentelemetry.api.trace TraceFlags TraceState SpanContext)
           (io.opentelemetry.api.internal StringUtils)
           (io.opentelemetry.api.common AttributeType)))

;; General purpose

(def non-empty-printable-string
  (gen/such-that
    #(and
       (not (.isEmpty %))
       (not (str/blank? %))
       (StringUtils/isPrintableString %))
    (gen/not-empty gen/string-ascii)))

(def simple-double (gen/double* {:infinite? false
                                 :NaN?      false}))

;; Attribute

(def attribute-string-value
  "non empty ascii string, size max 254, may be blank"
  (gen/fmap
    str/join
    (gen/vector gen/char-ascii 1 254)))

(def attribute-type->generator
  {AttributeType/STRING        attribute-string-value
   AttributeType/BOOLEAN       gen/boolean
   AttributeType/LONG          gen/large-integer
   AttributeType/DOUBLE        simple-double
   AttributeType/STRING_ARRAY  (gen/fmap attributes/string-array (gen/vector non-empty-printable-string 1 10))
   AttributeType/BOOLEAN_ARRAY (gen/fmap boolean-array (gen/vector gen/boolean 1 10))
   AttributeType/LONG_ARRAY    (gen/fmap long-array (gen/vector gen/large-integer 1 50))
   AttributeType/DOUBLE_ARRAY  (gen/fmap long-array (gen/vector simple-double 1 50))})

(def attribute-types-list (keys attribute-type->generator))
(def attribute-type-gen (gen/elements attribute-types-list))

(def attribute-key-name-gen
  (gen/fmap
    str/join
    (gen/vector gen/char-ascii 1 254)))

(defn AttributeKey-gen [attribute-type]
  (gen/fmap
    #(attributes/new-key % (attributes/AttributeType->keyword attribute-type))
    attribute-key-name-gen))

(defn attribute-key-gen [attribute-type]
  (gen/one-of
    [attribute-key-name-gen
     (AttributeKey-gen attribute-type)]))

(defn attribute-value-gen [type]
  (get attribute-type->generator type))

(def attribute-gen
  (gen/let [type attribute-type-gen
            key (attribute-key-gen type)
            value (attribute-value-gen type)]
    {:key key :value value :type type}))

(def attributes-gen
  (gen/fmap
    #(reduce
       (fn [acc x] (assoc acc (:key x) (:value x))) {} %)
    (gen/vector-distinct-by
      :key
      attribute-gen
      {:min-elements 1 :max-elements 8})))                  ;; TODO: find where this limit of 8 is documented

;; Baggage

(def baggage-key-gen non-empty-printable-string)

(def baggage-with-metadata-gen
  (gen/not-empty
    (gen/map
      baggage-key-gen
      (gen/let [value non-empty-printable-string
                metadata non-empty-printable-string]
        {:value value :metadata metadata}))))

(def baggage-without-metadata-gen
  (gen/not-empty
    (gen/map
      baggage-key-gen
      (gen/not-empty
        (gen/map
          (gen/return :value)
          non-empty-printable-string)))))

;; Context

(defn context-gen []
  (gen/return (context/get-root)))

;; Span

(def span-kind-gen
  (gen/elements (keys span/kinds-mapping)))

(def span-status-gen
  (gen/elements (keys span/status-mapping)))

(def span-name-gen
  non-empty-printable-string)

(def span-parent-gen
  (gen/one-of
    [(gen/return span/no-parent)
     (context-gen)]))                                       ;; fixme ?

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

(def uuid-str-gen
  (gen/fmap
    #(str %)
    gen/uuid))

(comment
  (defn span-context-from-map [{:keys [trace-id span-id trace-flags trace-state]}]
    (SpanContext/create trace-id span-id trace-flags trace-state)))

(def span-context-gen
  (gen/hash-map
    :trace-id uuid-str-gen                                  ;; FIXME: get more close to the specification, see IdGenerator
    :span-id uuid-str-gen
    :trace-flags trace-flags-gen
    :trace-state trace-state-gen))
