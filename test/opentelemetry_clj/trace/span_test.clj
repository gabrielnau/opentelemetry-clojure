(ns opentelemetry-clj.trace.span-test
  (:require
    [clojure.datafy :refer [datafy]]
    [clojure.test :refer :all]
    [clojure.test.check.generators :as gen]
    [opentelemetry-clj.sdk.datafy]
    [opentelemetry-clj.test-generators :as generators]
    [opentelemetry-clj.test-utils :as utils]
    [opentelemetry-clj.trace.span :as subject])
  (:import (io.opentelemetry.api.trace SpanBuilder Span)))

(deftest new-builder
  (let [[tracer _] (utils/get-tracer-and-exporter)
        b (subject/new-builder tracer {:name "foo"})]
    (is (instance? SpanBuilder b))))

(deftest start
  (let [[tracer _] (utils/get-tracer-and-exporter)
        b    (subject/new-builder tracer {:name "foo"})
        span (subject/start! b)]
    (is (instance? Span span))))

(deftest build-span
  (let [[tracer memory-exporter] (utils/get-tracer-and-exporter)]
    (testing "name"
      (with-bindings {#'utils/*tracer*          tracer
                      #'utils/*memory-exporter* memory-exporter}
        (let [attributes  (gen/generate generators/span-gen)
              span        (subject/new-started tracer attributes)
              span-as-map (datafy span)]
          (is (match? (:name span-as-map) (:name attributes))))))
    (testing ":parent option "
      (let [parent-span (subject/new-started tracer (assoc (gen/generate generators/span-gen) :parent :none))
            attributes  (gen/generate generators/span-gen)
            span        (subject/new-started tracer attributes)
            span-as-map (datafy span)]
        (println (:parent span-as-map))
        (is (match? (:name span-as-map) (:name attributes)))))))

;; TODO:
;; parent: put some context in thread, create :another_span in another context then:
;; case 1: nothing done, gets the parent
;; case 2: parent with :none -> root
;; case 3: parent with :another_span -> parent is :another_span

;; links: add some, validate datafy they are there

;; attributes:: add some, validate datafy

;; kind:
;; case 1: wrong one specified, fallback
;; case 2: good one, it works

;; start-ts TODO decide