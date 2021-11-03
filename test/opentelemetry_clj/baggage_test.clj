(ns opentelemetry-clj.baggage-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [clojure.test.check :as test-check]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.datafy :refer [datafy]]
            [opentelemetry-clj.sdk.datafy]
            [opentelemetry-clj.test-generators :as generators]
            [opentelemetry-clj.baggage :as subject])
  (:import (io.opentelemetry.api.baggage Baggage)))

(deftest get-key
  (let [args (gen/generate (s/gen :baggage/arguments))
        baggage (subject/new-baggage args)
        [some-key some-val] (first args)]
    (is (= nil (subject/get baggage "non-existent-key")))
    (is (= (:value some-val) (subject/get baggage some-key)))))

(deftest size
  (test-check/quick-check
    100
    (prop/for-all [args (s/gen :baggage/arguments)]
      (let [baggage (subject/new-baggage args)]
        (is (= (subject/size baggage)
              (count (keys args))))))))

(deftest is-empty
  (let [empty-baggage     (Baggage/empty)
        not-empty-baggage (subject/new-baggage (gen/generate
                                                 (gen/not-empty (s/gen :baggage/arguments))))]
    (is (subject/is-empty empty-baggage))
    (is (not (subject/is-empty not-empty-baggage)))))

(deftest with-value
  (let [baggage (subject/new-baggage (gen/generate (s/gen :baggage/arguments)))
        values  (gen/generate (gen/not-empty (s/gen :baggage/arguments)))
        result  (-> (subject/with-values baggage values)
                   datafy)]
    ;;; FIXME: matcher-combinator should allow to do this more easily?
    (doseq [[k v] values]
      (let [baggage (get result k)]
        (is (match?
              (m/match-with [map? m/embeds] v)
              baggage))))))


(deftest new-baggage
  (testing "assigns correct key and value, without metadata"
    (test-check/quick-check
      100
      (prop/for-all [args (s/gen :baggage/arguments-without-metadata)]
        (let [baggage        (subject/new-baggage args)
              baggage-as-map (datafy baggage)]
          (is (instance? Baggage baggage))
          (is (= (subject/size baggage) (count (keys args))))
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
      (prop/for-all [args (s/gen :baggage/arguments-with-metadata)]
        (let [baggage        (subject/new-baggage args)
              baggage-as-map (datafy baggage)]
          (is (instance? Baggage baggage))
          (is (= (subject/size baggage) (count (keys args))))
          ;;; FIXME: matcher-combinator should allow to do this more easily?
          (doseq [[k v] args]
            (let [baggage (get baggage-as-map k)]
              ;; equals -> same metadata value
              (is (match?
                    (m/match-with [map? m/equals] v)
                    baggage)))))))))

