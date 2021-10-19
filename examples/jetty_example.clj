(ns jetty-example
  (:require [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.interceptor.sieppari]
            [sieppari.async.core-async]                     ;; needed for core.async
            [sieppari.async.manifold]                       ;; needed for manifold
            [ring.adapter.jetty :as jetty]
            [muuntaja.interceptor]
            [clojure.core.async :as a]
            [manifold.deferred :as d]
            [opentelemetry-clj.trace.span :as span]
            [opentelemetry-clj.context :as context]
            [hikari-cp.core :refer :all]
            [next.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource))

  (:import (io.opentelemetry.api GlobalOpenTelemetry)))

;; tracer instance
(def tracer (GlobalOpenTelemetry/getTracer "jetty-handler"))

;; JDBC
(def datasource-component (atom nil))

(defn start-jdbc []
  (let [ds (component/start (connection/component
                              HikariDataSource
                              {:dbtype "postgresql"
                               :dbname "postgres"
                               :port 5431
                               :username "postgres"
                               :password "changeme"}))]
    (reset! datasource-component ds)
    (jdbc/execute! (ds) ["SELECT 'hello world'"])))

(defn stop-jdbc []
  (component/stop @datasource-component))

;; Ring / Reitit

;(defn interceptor [f x]
;  {:enter (fn [ctx] (f (update-in ctx [:request :via] (fnil conj []) {:enter x})))
;   :leave (fn [ctx] (f (update-in ctx [:response :body] conj {:leave x})))})


;(def <sync> identity)
;(def <future> #(future %))
;(def <async> #(a/go %))
;(def <deferred> d/success-deferred)


(defn handler
  ([request]
   (let [span (-> (span/new-builder tracer "FOO 1")
                (span/set-kind :server)
                span/start)]
     (Thread/sleep 100)
     (with-open [_ (context/make-current! span)]
       (let [context (context/current)]
         (a/thread
           (println "c" context)
           (with-open [_ (context/make-current! context)]
             (jdbc/execute! (@datasource-component) ["SELECT 'hello world'"]))
           (span/end span)
           {:status 200, :headers {}, :body "Hello World"})))))
  ([request respond raise]))
   ;;TODO))





(def router
  (ring/router
    ["/ping" {:get handler}]))

(def app
  (ring/ring-handler router))

       ;["/sync"
       ; {:interceptors [(interceptor <sync> :sync)]
       ;  :get {:interceptors [(interceptor <sync> :get)]
       ;        :handler (handler <sync>)}}]
       ;
       ;["/future"
       ; {:interceptors [(interceptor <future> :future)]
       ;  :get {:interceptors [(interceptor <future> :get)]
       ;        :handler (handler <future>)}}](
       ;
       ;["/async"
       ; {:interceptors [(interceptor <async> :async)]
       ;  :get {:interceptors [(interceptor <async> :get)]
       ;        :handler (handler <async>)}}]
       ;
       ;["/deferred"
       ; {:interceptors [(interceptor <deferred> :deferred)]
       ;  :get {:interceptors [(interceptor <deferred> :get)]
       ;        :handler (handler <deferred>)}}]])))
(def server (atom nil))


(defn start []
  (let [jetty (jetty/run-jetty #'app {:port 3000, :join? false, :async? true})]
    (reset! server jetty)
    (println "server running in port 3000")))

(defn stop []
  (when @server
    (.join @server)))

;; Tracer

(comment
  (stop)

  (start))
