(ns opentelemetry-clj.sdk.open-telemetry
  "
  Entrypoint
  NB: needs opentelemetry-sdk"
  (:import (io.opentelemetry.sdk.trace SdkTracerProvider)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.context.propagation ContextPropagators TextMapPropagator)))

(defn build-tracer-provider [options]
  (let [builder (SdkTracerProvider/builder)]
    (doseq [span-processor (get options :span-processors [])]
      (.addSpanProcessor builder span-processor))
    (.build builder)))

(defn build-propagators
  "
  ex: [HttpTraceContext/getInstance W3CBaggagePropagator/getInstance]
  "
  [propagators]
  (if (= 1  (count propagators))
    (ContextPropagators/create propagators)
    (ContextPropagators/create
      (TextMapPropagator/composite propagators))))

(comment
  ;; TODO: possible with spec to generate this on the fly ?
  {:register-global? true
   :tracer-provider {:span-processors [< instances >]}
   :propagators [{}]})

(defn build-skd
  "TODO document opts map
  register-global?
    true: Returns a new OpenTelemetrySdk built with the configuration of this OpenTelemetrySdkBuilder and registers it as the global OpenTelemetry. An exception will be thrown if this method is attempted to be called multiple times in the lifecycle of an application - ensure you have only one SDK for use as the global instance. If you need to configure multiple SDKs for tests, use GlobalOpenTelemetry.resetForTest() between them.
    false: Returns a new OpenTelemetrySdk built with the configuration of this OpenTelemetrySdkBuilder. This SDK is not registered as the global OpenTelemetry. It is recommended that you register one SDK using buildAndRegisterGlobal() for use by instrumentation that requires access to a global instance of OpenTelemetry. (https://javadoc.io/doc/io.opentelemetry/opentelemetry-sdk/latest/io/opentelemetry/sdk/OpenTelemetrySdkBuilder.html)
  "
  [{:keys [register-global? tracer-provider propagators]}]
  (let [otel-sdk-builder (OpenTelemetrySdk/builder)]
    (when tracer-provider
      (.setTracerProvider (build-tracer-provider tracer-provider)))
    (when (and propagators (not (empty? propagators)))
      (.setPropagators (build-propagators propagators)))
    ;; TODO: fallback to register-global?
    (if register-global?
      (.buildAndRegisterGlobal otel-sdk-builder)
      (.build otel-sdk-builder))))
