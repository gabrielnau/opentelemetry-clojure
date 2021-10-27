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
    [opentelemetry-clj.test-utils :as test-utils]))

(deftest new
  (testing "builds a Resource with attributes only"
    (let [attributes        (gen/generate generators/attributes-gen)
          result-as-map     (-> (subject/new attributes) datafy)
          attributes-as-map (test-utils/attribute-arguments->map attributes)]
      (is (nil? (:schema-url result-as-map)))
      (is (match?
            (m/match-with [map? m/equals] (:attributes result-as-map))
            attributes-as-map))))
  (testing "builds a Resource with attributes and schema url"
    (let [some-schema-url   (gen/generate generators/string-gen)
          attributes        (gen/generate generators/attributes-gen)
          result-as-map     (-> (subject/new attributes some-schema-url) datafy)
          attributes-as-map (test-utils/attribute-arguments->map attributes)]
      (is (= (:schema-url result-as-map) some-schema-url))
      (is (match?
            (m/match-with [map? m/equals] (:attributes result-as-map))
            attributes-as-map)))))

