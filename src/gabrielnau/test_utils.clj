(ns gabrielnau.test-utils
  (:import (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)))

(def memory-exporter (InMemorySpanExporter/create))

;; wip check in memory spans matches expectations
(defn spans []
  (-> (.getFinishedSpanItems memory-exporter)))

