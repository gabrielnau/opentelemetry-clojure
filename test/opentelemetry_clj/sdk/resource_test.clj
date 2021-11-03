(ns opentelemetry-clj.sdk.resource-test
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.spec.gen.alpha :as gen]
    [clojure.test :refer :all]
    [matcher-combinators.matchers :as m]
    [matcher-combinators.test]
    [opentelemetry-clj.sdk.datafy]
    [opentelemetry-clj.sdk.resource :as subject]
    [opentelemetry-clj.test-generators :as generators]
    [opentelemetry-clj.test-utils :as test-utils])
  (:import (io.opentelemetry.sdk.resources Resource)
           (io.opentelemetry.semconv.resource.attributes ResourceAttributes)))

(deftest new
  (testing "builds a Resource with attributes only"
    (let [attributes        (gen/generate generators/attributes-gen)
          result-as-map     (-> (subject/new {:attributes attributes}) datafy)
          attributes-as-map (test-utils/attribute-arguments->map attributes)]
      (is (nil? (:schema-url result-as-map)))
      (is (match?
            (m/match-with [map? m/equals] (:attributes result-as-map))
            attributes-as-map))))
  (testing "builds a Resource with attributes and schema url"
    (let [some-schema-url   (gen/generate generators/string-gen)
          attributes        (gen/generate generators/attributes-gen)
          result-as-map     (-> (subject/new {:attributes attributes :schema-url some-schema-url}) datafy)
          attributes-as-map (test-utils/attribute-arguments->map attributes)]
      (is (= (:schema-url result-as-map) some-schema-url))
      (is (match?
            (m/match-with [map? m/equals] (:attributes result-as-map))
            attributes-as-map)))))

(deftest default-fn
  (let [subject-resource   (subject/default)
        reference-resource (Resource/getDefault)]
    (is (match?
          (m/match-with [map? m/equals] (subject/->map subject-resource))
          (subject/->map reference-resource)))))

(deftest schema-url
  (let [schema-url ResourceAttributes/SCHEMA_URL
        resource   (subject/new {:attributes {} :schema-url schema-url})]
    (is (match?
          schema-url
          (subject/schema-url resource)))))

(deftest merge-fn
  ;; Don't test schema-url behavior, simply exercice code path with a simple case.
  (let [resource-1 (subject/new {:attributes {"foo" "initial-value"}})
        resource-2 (subject/new {:attributes {"foo" "overriden-value"}})
        result     (subject/merge resource-1 resource-2)]
    (is (match?
          (m/match-with [map? m/equals] {"foo" "overriden-value"})
          (:attributes (subject/->map result))))))