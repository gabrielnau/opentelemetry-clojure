(ns opentelemetry-clj.baggage-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test]
            [clojure.test.check :as test-check]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.datafy :refer [datafy]]

            [matcher-combinators.matchers :as m]
            [opentelemetry-clj.baggage :as subject]
            [opentelemetry-clj.core :as core]
            [opentelemetry-clj.datafy]
            [opentelemetry-clj.baggage :as baggage])
  (:import (io.opentelemetry.api.baggage Baggage)))


(deftest get-key
  (let [args (gen/generate (s/gen ::baggage/arguments))
        baggage (core/new-baggage args)
        [some-key some-val] (first args)]
    (is (= nil (subject/get-key baggage "non-existent-key")))
    (is (= (:value some-val) (subject/get-key baggage some-key)))))

(deftest size
  (test-check/quick-check
    100
    (prop/for-all [args (s/gen ::baggage/arguments)]
      (let [baggage (core/new-baggage args)]
        (is (= (baggage/size baggage)
              (count (keys args))))))))

(deftest is-empty
  (let [empty-baggage     (Baggage/empty)
        not-empty-baggage (core/new-baggage (gen/generate (s/gen ::baggage/arguments)))]
    (is (subject/is-empty empty-baggage))
    (is (not
          (subject/is-empty not-empty-baggage)))))

(deftest with-value
  (let [baggage (core/new-baggage (gen/generate (s/gen ::baggage/arguments)))
        values  (gen/generate (s/gen ::baggage/arguments))
        result  (->
                   (subject/with-values baggage values)
                   datafy)]
    ;;; FIXME: matcher-combinator should allow to do this more easily?
    (doseq [[k v] values]
      (let [baggage (get result k)]
        (is (match?
              (m/match-with [map? m/embeds] v)
              baggage))))))


