(ns gabrielnau.aleph
  (:require [aleph.http :as http]
            [gabrielnau.opentelemetry-clojure :as otel]))


(defn handle-body [req]
  (println req)
  (let [otel/tracer])

  "hello!")

(defn handler [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body (handle-body req)})

(defn start-server []
  (http/start-server handler {:port 8080}))