(ns opentelemetry-clj.attribute-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test]
            [clojure.test.check :as test-check]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [opentelemetry-clj.generators :as generators]
            [opentelemetry-clj.datafy]
            [clojure.datafy :refer [datafy]]
            [opentelemetry-clj.attribute :as subject]
            [matcher-combinators.matchers :as m])
  (:import (io.opentelemetry.api.common AttributeKey Attributes)))

(defn AttributeType->keyword [a]
  (-> a
    str
    str/lower-case
    (str/replace #"_" "-")
    keyword))

(deftest new-key
  (testing "builds a typed AttributeKey instance"
    (test-check/quick-check
      100
      (prop/for-all [key generators/string-generator
                     type generators/attribute-type-generator]
        (let [result (subject/new-key key type)]
          (is (instance? AttributeKey result))
          (is (= key (.getKey result)))
          (is (= type (AttributeType->keyword (.getType result)))))))))

(defn attribute-args->map [args]
  (reduce
    (fn [acc [k v]] (assoc acc (datafy k) (datafy v)))
    {}
    args))

(deftest new
  (testing "build Attributes"
    (test-check/quick-check
      10
      (prop/for-all [args (generators/attributes-map-generator)]
        (let [result        (subject/new args)
              result-as-map (datafy result)
              args-as-map   (attribute-args->map args)]
          (is (instance? Attributes result))
          ;(when-not (= (count (keys args-as-map))
          ;            (count (keys result-as-map)))
          ;  (println (clojure.data/diff (set (keys args-as-map)) (set (keys result-as-map)))))
          (is (match?
                (m/match-with [map? m/equals] result-as-map)
                args-as-map))
          (is (match?
                (count (keys args-as-map))
                (.size result))))))))


(comment
  ;; FIXME: can't iterate on all static values
  (import '(io.opentelemetry.semconv.trace.attributes SemanticAttributes))
  (require 'clojure.reflect)
  (->> (clojure.reflect/reflect SemanticAttributes)
    :members
    (filter #(= 'io.opentelemetry.api.common.AttributeKey (:type %)))
    (map #(symbol (:name %)))
    (map #(try
            (. SemanticAttributes %)
            (catch Exception e
              (println %)))))
  (.getKey SemanticAttributes/AWS_LAMBDA_INVOKED_ARN))
