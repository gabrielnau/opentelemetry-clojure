(ns jetty-example
  (:require [integrant.core :as ig]
            [integrant.repl :refer [clear go halt prep init reset reset-all]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
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
  (:import (io.opentelemetry.api GlobalOpenTelemetry)
           (io.opentelemetry.context Context)
           (com.zaxxer.hikari HikariDataSource)
           (io.opentelemetry.sdk.trace SdkTracerProvider)
           (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)
           (io.opentelemetry.exporters.logging LoggingSpanExporter)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.context.propagation ContextPropagators)
           (io.opentelemetry.api.trace.propagation W3CTraceContextPropagator)))

;; tracer instance
(def tracer-atom (atom nil))

(defn query-db [db]
  (let [context (context/current)]
    (a/thread
      (println "thread" context)
      (with-open [_ (context/make-current! context)]
        (let [span   (-> (span/new-builder @tracer-atom "CHILD") span/start)
              result (jdbc/execute! db ["SELECT 'hello world'"])]
          (span/end span)
          result)))))


(defn handler
  ([db request]
   (let [span (-> (span/new-builder @tracer-atom "FOO 2")
                (span/set-kind :server)
                span/start)]
     ;(with-open [_ (-> (Context/current) (.with span) .makeCurrent)])
     (with-open [_ (.makeCurrent span)]
       (let [res (query-db db)]
         (a/go
           (println (a/<! res)))
         (span/end span)
         {:status 200, :headers {}, :body "Hello World"})))))

;; Integrant wiring

(defmethod ig/init-key ::jetty [_ {:keys [port join? handler async?]}]
  (jetty/run-jetty handler {:port port :join? join? :async? async?}))

(defmethod ig/halt-key! ::jetty [_ server]
  (.stop server))

(defmethod ig/init-key ::handler [_ {:keys [db]}]
  (ring/ring-handler
    (ring/router
      ["/ping" {:get {:handler (partial handler db)}}])))

(defmethod ig/init-key ::tracer [_ _]
  (let [tracer-provider (-> (doto (SdkTracerProvider/builder)
                              (.addSpanProcessor (SimpleSpanProcessor/create (LoggingSpanExporter.))))
                          .build)
        otel            (-> (doto (OpenTelemetrySdk/builder)
                              (.setTracerProvider tracer-provider)
                              (.setPropagators (ContextPropagators/create (W3CTraceContextPropagator/getInstance))))
                          .buildAndRegisterGlobal)
        tracer          (.getTracer otel "my-custom-tracer" "1.0.0")]
    (reset! tracer-atom tracer)
    tracer))


(defmethod ig/init-key ::db [_ _]
  (let [ds (connection/->pool HikariDataSource {:dbtype "postgres" :dbname "postgres" :username "postgres" :password "changeme"})]
    (.close (jdbc/get-connection ds))                       ;; initializes the pool and performs a validation check:
    ds))

(defmethod ig/halt-key! ::db [_ datasource]
  (.close datasource))

(def system-config
  {::jetty   {:port    3000
              :join?   false
              :async?  false
              :handler (ig/ref ::handler)}
   ::db      {}
   ::tracer  {}
   ::handler {:db (ig/ref ::db)}})

(integrant.repl/set-prep! (constantly system-config))
