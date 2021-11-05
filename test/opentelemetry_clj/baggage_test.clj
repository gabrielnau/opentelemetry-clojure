(ns opentelemetry-clj.baggage-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [clojure.test.check :as test-check]
            [clojure.test.check.properties :as prop]
            [clojure.spec.gen.alpha :as gen]
            [clojure.datafy :refer [datafy]]
            [opentelemetry-clj.sdk.datafy]
            [opentelemetry-clj.baggage :as subject]
            [opentelemetry-clj.test-generators :as generators])
  (:import (io.opentelemetry.api.baggage Baggage)))

(deftest get-fn
  (let [args    (gen/generate generators/baggage-without-metadata-gen)
        baggage (subject/new args)
        [some-key some-val] (first args)]
    (is (= nil (subject/get baggage "non-existent-key")))
    (is (= (:value some-val) (subject/get baggage some-key)))))

(deftest count-fn
  (test-check/quick-check
    10
    (prop/for-all [args generators/baggage-without-metadata-gen]
      (let [baggage (subject/new args)]
        (is (= (subject/count baggage)
              (count (keys args))))))))

(deftest empty?-fn
  (let [empty-baggage     (Baggage/empty)
        not-empty-baggage (subject/new (gen/generate generators/baggage-without-metadata-gen))]
    (is (subject/empty? empty-baggage))
    (is (not (subject/empty? not-empty-baggage)))))

(deftest new-fn
  (testing "without metadata"
    (test-check/quick-check
      1000
      (prop/for-all [args generators/baggage-without-metadata-gen]
        (let [baggage        (subject/new args)
              baggage-as-map (subject/->map baggage)]
          (is (instance? Baggage baggage))
          (is (match? (subject/count baggage) (count (keys args))))
          (doseq [[k v] args]
            (let [baggage (get baggage-as-map k)]
              (is (= "" (:metadata baggage)))
              (is (match?
                    (m/match-with [map? m/embeds] v)
                    baggage))))
          (comment
            ;;FIXME: why the need to do the doseq, why this example doesn't work ?
            (is (match?
                  (m/match-with [map? m/embeds] args)
                  (subject/->map baggage))))))))
  (testing "with metadata"
    (test-check/quick-check
      1000
      (prop/for-all [args generators/baggage-with-metadata-gen]
        (let [baggage        (subject/new args)
              baggage-as-map (subject/->map baggage)]
          (is (instance? Baggage baggage))
          (is (match? (subject/count baggage) (count (keys args))))
          (doseq [[k v] args]
            (let [baggage (get baggage-as-map k)]
              (is (match?
                    (m/match-with [map? m/equals] v)
                    baggage))))
          (comment
            ;;FIXME: why the need to do the doseq, why this example doesn't work ?
            (is (match?
                  (m/match-with [map? m/equals] args)
                  (subject/->map baggage)))))))))
