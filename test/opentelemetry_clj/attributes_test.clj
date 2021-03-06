(ns opentelemetry-clj.attributes-test
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [clojure.test.check :as test-check]
    [clojure.test.check.properties :as prop]
    [matcher-combinators.matchers :as m]
    [matcher-combinators.test]
    [opentelemetry-clj.attributes :as subject]
    [opentelemetry-clj.test-generators :as generators]
    [opentelemetry-clj.sdk.datafy]
    [opentelemetry-clj.test-utils :as test-utils]
    [clojure.test.check.generators :as gen])
  (:import (io.opentelemetry.api.common AttributeKey Attributes AttributeType)))

(deftest string-array-fn
  (let [str-array (subject/string-array ["foo" "bar"])]
    (is (match? 2 (count str-array)))
    (is (match? "foo" (first str-array)))
    (is (match? "bar" (last str-array)))))

(deftest new-key-fn
  (test-check/quick-check
    1000
    (prop/for-all [key generators/attribute-key-name-gen
                   type generators/attribute-type-gen]
      (let [type-as-keyword (subject/AttributeType->keyword type)
            result (subject/new-key key type-as-keyword)]
        (is (instance? AttributeKey result))
        (is (match? key (.getKey result)))
        (is (match? type-as-keyword (subject/AttributeType->keyword (.getType result))))))))

(deftest new-fn
  (test-check/quick-check
    1000
    (prop/for-all [args generators/attributes-gen]
      (let [result        (subject/new args)
            result-as-map (subject/->map result)
            args-as-map   (test-utils/attribute-arguments->map args)]
        (is (instance? Attributes result))
        (is (= (count args) (.size result) (count result-as-map) (count args-as-map)))
        (is (match?
              (m/equals result-as-map)
              args-as-map))))))

(deftest get-fn
  (let [attribute-key   (gen/generate (generators/AttributeKey-gen AttributeType/DOUBLE))
        attribute-value (double 123)
        resource        (subject/new {attribute-key attribute-value})
        result          (subject/get resource attribute-key)]
    (is (match? result (double 123)))))

