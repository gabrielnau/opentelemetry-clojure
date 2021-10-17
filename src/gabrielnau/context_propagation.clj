(ns gabrielnau.context-propagation
  (:import (io.opentelemetry.context Context)))

;; solution 1: binding conveyance

(def ^:dynamic *current-context*)
;; TODO: macro to wrap with-open and with-bindings thing

;; solution 2: wrap ExecutorServices

(defn wrap-agent-executors []
  (set-agent-send-off-executor! (Context/taskWrapping clojure.lang.Agent/soloExecutor))
  (set-agent-send-executor! (Context/taskWrapping clojure.lang.Agent/pooledExecutor)))
