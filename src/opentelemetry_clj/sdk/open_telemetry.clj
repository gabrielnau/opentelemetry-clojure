(ns opentelemetry-clj.sdk.open-telemetry
  "Helpers to build OpenTelemetrySdk instance.

  *Warning:* this namespace requires the dependency `io.opentelemetry/opentelemetry-sdk` that should be set manually or available in classpath if using the java agent.
  "
  (:import (io.opentelemetry.sdk.trace SdkTracerProvider)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.context.propagation ContextPropagators TextMapPropagator)))

(defn- ^:no-doc list->ContextPropagators
  "Return ContextPropagators from given propagators

  Example:
  ```clojure
  (list->ContextPropagators
    [HttpTraceContext/getInstance W3CBaggagePropagator/getInstance])
  ```
  "
  [propagators]
  (if (= 1  (count propagators))
    (ContextPropagators/create propagators)
    (ContextPropagators/create
      (TextMapPropagator/composite propagators))))

(defn sdk
  "Return an OpenTelemetrySdk given the options map.

  Arguments: a map of:
  | key         | description |
  | -------------|-------------|
  | `:register-global?` | Optionnal, default to false. If true: Returns a new OpenTelemetrySdk built with the configuration of this OpenTelemetrySdkBuilder and registers it as the global OpenTelemetry. An exception will be thrown if this method is attempted to be called multiple times in the lifecycle of an application - ensure you have only one SDK for use as the global instance. If you need to configure multiple SDKs for tests, use GlobalOpenTelemetry.resetForTest() between them. If false: Returns a new OpenTelemetrySdk built with the configuration of this OpenTelemetrySdkBuilder. This SDK is not registered as the global OpenTelemetry. It is recommended that you register one SDK using buildAndRegisterGlobal() for use by instrumentation that requires access to a global instance of OpenTelemetry. (https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk/latest/io/opentelemetry/sdk/OpenTelemetrySdkBuilder.html).
  | `:tracer-provider` | Optionnal, .
  | `:propagators` | Optionnal, list of TextMapPropagator instances.

  Example:
  ```clojure
  (sdk
    {
      :register-global? true
      :propagators [HttpTraceContext/getInstance W3CBaggagePropagator/getInstance]
      :tracer-provider (SimpleSpanProcessor/create (LoggingSpanExporter.))
    })
  ```
  "
  [{:keys [register-global? tracer-provider propagators] :as opts}]
  (let [otel-sdk-builder (OpenTelemetrySdk/builder)]
    (when tracer-provider
      (.setTracerProvider tracer-provider))
    (when (and propagators (not (empty? propagators)))
      (.setPropagators (list->ContextPropagators propagators)))
    (if register-global?
      (.buildAndRegisterGlobal otel-sdk-builder)
      (.build otel-sdk-builder))))
