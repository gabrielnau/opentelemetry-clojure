(ns opentelemetry-clj.trace.span-test
  (:require
    [clojure.test :refer :all]
    [matcher-combinators.test]
    [clojure.datafy :refer [datafy]]
    [clojure.test.check.generators :as gen]
    [opentelemetry-clj.sdk.datafy]
    [opentelemetry-clj.test-generators :as generators]
    [opentelemetry-clj.test-utils :as utils]
    [opentelemetry-clj.trace.span :as subject]
    [clojure.test.check :as test-check]
    [clojure.test.check.properties :as prop]
    [opentelemetry-clj.test-utils :as test-utils]
    [portal.api :as portal]
    [matcher-combinators.matchers :as m]
    [opentelemetry-clj.attributes :as attributes])
  (:import (io.opentelemetry.api.trace SpanBuilder Span SpanContext)
           (java.time Instant)
           (java.util.concurrent TimeUnit)))

(deftest update-name-fn
  (let [[tracer _] (utils/get-tracer-and-exporter)]
    (test-check/quick-check
      100
      (prop/for-all [distinct-names (gen/list-distinct
                                      generators/span-name-gen
                                      {:num-elements 2})]
        (let [[old-name new-name] distinct-names
              span (subject/new-started tracer {:name old-name})]
          (subject/update-name span new-name)
          (let [span-name (-> span datafy :name)]
            (is (match? new-name span-name))))))))

(deftest set-status-fn
  (let [[tracer _] (utils/get-tracer-and-exporter)
        span (subject/new-started tracer {:name (gen/generate generators/span-name-gen)})]
    (is (match? subject/status-unset (-> span datafy :status)))

    (testing "ignores unknown status"
      (subject/set-status span :incorrect-status)
      (is (match? subject/status-unset (-> span datafy :status))))

    (testing "set known statuses"
      (test-check/quick-check
        100
        (prop/for-all [status generators/span-status-gen]
          (subject/set-status span status)
          (is (match? status (-> span datafy :status))))))))

(deftest record-exception-fn
  (let [[tracer _] (utils/get-tracer-and-exporter)]
    (let [msg       (gen/generate generators/non-empty-printable-string)
          exception (ex-info msg {})
          span      (subject/new-started tracer {:name (gen/generate generators/span-name-gen)})]
      (subject/record-exception span exception)
      (is (match?
            ;; don't test stacktrace
            {:message msg :type "clojure.lang.ExceptionInfo"}
            (first
              (test-utils/span->recorded-exceptions span)))))))

(deftest get-span-context-fn
  (let [[tracer _] (utils/get-tracer-and-exporter)]
    (let [span         (subject/new-started tracer {:name (gen/generate generators/span-name-gen)})
          span-context (subject/get-span-context span)]
      (is (instance? SpanContext span-context)))))

(deftest new-builder
  (let [[tracer _] (utils/get-tracer-and-exporter)
        b (subject/new-builder tracer {:name (gen/generate generators/span-name-gen)})]
    (is (instance? SpanBuilder b))))

(deftest start-fn
  (let [[tracer _] (utils/get-tracer-and-exporter)
        span-builder (subject/new-builder tracer {:name (gen/generate generators/span-name-gen)})
        span         (subject/start span-builder)]
    (is (instance? Span span))
    (is (.isRecording span))))

(deftest end-fn
  (let [[tracer _] (utils/get-tracer-and-exporter)]
    (testing "no end timestamp given"
      (let [span-builder (subject/new-builder tracer {:name (gen/generate generators/span-name-gen)})
            span         (subject/start span-builder)]
        (is (not (.hasEnded span)))
        (subject/end span)
        (is (.hasEnded span))
        (is
          (match?
            (test-utils/matcher-nano-max-100ms-ago)
            (-> span datafy :timestamps :end-epoch-nanos)))))
    (testing "Instant end timestamp given"
      (let [span-builder (subject/new-builder tracer {:name (gen/generate generators/span-name-gen)})
            span         (subject/start span-builder)
            now          (Instant/now)]
        (is (not (.hasEnded span)))
        (subject/end span now)
        (is (.hasEnded span))
        (is (match?
              (test-utils/instant->epoch-nanos now)
              (-> span datafy :timestamps :end-epoch-nanos)))))
    (testing "ts and time unit given"
      (let [span-builder (subject/new-builder tracer {:name (gen/generate generators/span-name-gen)})
            span         (subject/start span-builder)
            now          (Instant/now)]
        (is (not (.hasEnded span)))
        (subject/end span (test-utils/instant->epoch-nanos now) TimeUnit/NANOSECONDS)
        (is (.hasEnded span))
        (is (match?
              (test-utils/instant->epoch-nanos now)
              (-> span datafy :timestamps :end-epoch-nanos)))))))

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
        ;; TODO: finish this
        (is (match? (:name span-as-map) (:name attributes)))))))

(deftest set-attribute-fn
  (test-check/quick-check
    1000
    (prop/for-all [{:keys [key value type]} generators/attribute-gen]
      (let [[tracer _] (utils/get-tracer-and-exporter)
            span (subject/new-started tracer {:name (gen/generate generators/span-name-gen)})]
        (subject/set-attribute span key value)
        (let [span-attributes (-> span datafy :attributes)
              key-as-string   (datafy key) ;; can be a string or an AttributeKey
              attr-value-clojurized (attributes/clojurize-attribute-value type value)
              result-value    (get span-attributes key-as-string)]
          (is (match?
                result-value
                attr-value-clojurized)))))))


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