(ns jetty-example
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [integrant.core :as ig]
            [muuntaja.interceptor]
            [reitit.interceptor.sieppari]
            [sieppari.async.core-async]
            [sieppari.async.manifold]
            [clojure.core.async :as a]
            [manifold.deferred :as d]
            [opentelemetry-clj.trace.span :as span]
            [opentelemetry-clj.context :as context]
            [hikari-cp.core :refer :all]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource))

  (:import (io.opentelemetry.api GlobalOpenTelemetry)))

;; tracer instance
(def tracer (GlobalOpenTelemetry/getTracer "jetty-handler"))

(defn handler
  ([db request]
   (let [span (-> (span/new-builder tracer "FOO 1")
                (span/set-kind :server)
                span/start)]
     (Thread/sleep 100)
     (with-open [_ (context/make-current! span)]
       (let [context (context/current)]
         (a/thread
           (println "c" context)
           (with-open [_ (context/make-current! context)]
             (jdbc/execute! (db) ["SELECT 'hello world'"]))
           (span/end span)
           {:status 200, :headers {}, :body "Hello World"}))))))

;; Integrant wiring

(defmethod ig/init-key :example/jetty [_ {:keys [port join? handler async?]}]
  (jetty/run-jetty handler {:port port :join? join? :async? async?}))

(defmethod ig/halt-key! :example/jetty [_ server]
  (.join server))

(defmethod ig/init-key :example/handler [_ {:keys [db]}]
  (ring/ring-handler
    (ring/router
      ["/ping" {:get {:handler (partial handler db)}}])))

(defmethod ig/init-key :example/db [_ {:keys [port username password]}]
  (connection/->pool HikariDataSource {:dbtype "postgresql" :dbname "postgres" :port port :username username :password password}))

(defmethod ig/halt-key! :example/db [_ datasource]
  (.close datasource))

(def runtime-system (atom nil))
(def system-config
  {:example/jetty   {:port    3000
                     :join?   false
                     :async?  false
                     :handler (ig/ref :example/handler)}
   :example/db      {:port     5431
                     :username "postgres"
                     :password "changeme"}
   :example/handler {:db (ig/ref :example/db)}})


(defn start []
   (reset! runtime-system (ig/init system-config))
  :ok)

(defn stop []
  (ig/halt! @runtime-system))

