(ns opentelemetry-clj.attribute-test
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [clojure.test.check :as test-check]
    [clojure.test.check.properties :as prop]
    [matcher-combinators.matchers :as m]
    [matcher-combinators.test]
    [opentelemetry-clj.attribute :as subject]
    [opentelemetry-clj.test-generators :as generators]
    [opentelemetry-clj.sdk.datafy]
    [opentelemetry-clj.test-utils :as test-utils])

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
      (prop/for-all [key generators/string-gen
                     type generators/attribute-type-gen]
        (let [result (subject/key key type)]
          (is (instance? AttributeKey result))
          (is (= key (.getKey result)))
          (is (= type (AttributeType->keyword (.getType result)))))))))



(deftest new
  (testing "build Attributes"
    (test-check/quick-check
      10
      (prop/for-all [args generators/attributes-gen]
        (let [result        (subject/attributes args)
              result-as-map (datafy result)
              args-as-map   (test-utils/attribute-arguments->map args)]
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
