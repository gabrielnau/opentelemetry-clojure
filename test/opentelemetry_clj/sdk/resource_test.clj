(ns opentelemetry-clj.sdk.resource-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [clojure.test.check :as test-check]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.datafy :refer [datafy]]
            [opentelemetry-clj.datafy]

            [opentelemetry-clj.generators :as generators]
            [opentelemetry-clj.sdk.resource :as subject]
            [opentelemetry-clj.test-utils :as test-utils]))

(deftest new
  (testing "builds a Resource with attributes only"
    (let [attributes        (gen/generate (generators/attributes-map-generator))
          result-as-map     (-> (subject/new attributes) datafy)
          attributes-as-map (test-utils/attribute-arguments->map attributes)]
      (is (nil? (:schema-url result-as-map)))
      (is (match?
            (m/match-with [map? m/equals] (:attributes result-as-map))
            attributes-as-map))))
  (testing "builds a Resource with attributes and schema url"
    (let [some-schema-url   (gen/generate generators/string-generator)
          attributes        (gen/generate (generators/attributes-map-generator))
          result-as-map     (-> (subject/new attributes some-schema-url) datafy)
          attributes-as-map (test-utils/attribute-arguments->map attributes)]
      (is (= (:schema-url result-as-map) some-schema-url))
      (is (match?
            (m/match-with [map? m/equals] (:attributes result-as-map))
            attributes-as-map)))))

