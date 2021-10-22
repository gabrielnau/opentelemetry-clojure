(ns opentelemetry-clj.trace.span-test
  (:require [clojure.test :refer :all]
            [clojure.datafy :refer [datafy]]
            [opentelemetry-clj.datafy] ;; required
            [opentelemetry-clj.trace.span :as subject]
            [opentelemetry-clj.test-utils :as utils]
            [clojure.string :as str]))

;; fixture reset

(defn span->span-map [span]
  (let [span-data (.toSpanData span)]
    {:name (.name span)}))

(deftest build
  (let [[tracer memory-exporter] (utils/get-tracer-and-exporter)]
    (with-bindings {#'utils/*tracer* tracer
                    #'utils/*memory-exporter* memory-exporter}
      (testing "with :name"
        (let [name (utils/span-name)
              r    (utils/closed-span {:name name})]
          (println (datafy r)))))))





