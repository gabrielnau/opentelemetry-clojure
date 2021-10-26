(ns opentelemetry-clj.core-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test]
            [clojure.test.check :as test-check]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [matcher-combinators.matchers :as m]

            [clojure.datafy :refer [datafy]]
            [opentelemetry-clj.core :as subject]
            [opentelemetry-clj.datafy]
            [opentelemetry-clj.baggage :as baggage])
  (:import (io.opentelemetry.api.baggage Baggage)))


(deftest new-baggage
  (testing "assigns correct key and value, without metadata"
    (test-check/quick-check
      100
      (prop/for-all [args (s/gen ::baggage/arguments-without-metadata)]
        (let [baggage        (subject/new-baggage args)
              baggage-as-map (datafy baggage)]
          (is (instance? Baggage baggage))
          (is (= (baggage/size baggage) (count (keys args))))
          ;;; FIXME: matcher-combinator should allow to do this more easily?
          (doseq [[k v] args]
            (let [baggage (get baggage-as-map k)]
              ;; embeds -> default metadata set
              (is (= "" (:metadata baggage)))
              (is (match?
                    (m/match-with [map? m/embeds] v)
                    baggage))))))))
  (testing "assigns correct key and value, with metadata"
    (test-check/quick-check
      100
      (prop/for-all [args (s/gen ::baggage/arguments-with-metadata)]
        (let [baggage        (subject/new-baggage args)
              baggage-as-map (datafy baggage)]
          (is (instance? Baggage baggage))
          (is (= (baggage/size baggage) (count (keys args))))
          ;;; FIXME: matcher-combinator should allow to do this more easily?
          (doseq [[k v] args]
            (let [baggage (get baggage-as-map k)]
              ;; equals -> same metadata value
              (is (match?
                    (m/match-with [map? m/equals] v)
                    baggage)))))))))
