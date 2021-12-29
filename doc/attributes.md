
### Attributes

OTEL documentation:

- best practices: https://opentelemetry.lightstep.com/best-practices/using-attributes/
- Recommendations for app
  devs: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/attribute-naming.md#recommendations-for-application-developers
- Naming: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/attribute-naming.md
  Spec: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/common.md#attributes
- Limits behavior +
  configuration: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/common.md#attribute-limits-configuration

- semconv artifact
  -> https://javadoc.io/doc/io.opentelemetry/opentelemetry-semconv/latest/io/opentelemetry/semconv/trace/attributes/SemanticAttributes.html
  -> resources
  attributes: https://javadoc.io/doc/io.opentelemetry/opentelemetry-semconv/latest/io/opentelemetry/semconv/resource/attributes/ResourceAttributes.html

- They can be attached to a Resource, Span, span Event, span Exception,
- why typed ?
  > this type-safety is specifically to support tracing backends that have typed attributes/tags (like Jaeger). OTLP also has this built in to the model. source: https://cloud-native.slack.com/archives/C014L2KCTE3/p1634928183008800?thread_ts=1634889110.007100&cid=C014L2KCTE3

Implementation choice:
- attributes: takes a map or an instance of attributes
- Because of the Java class we have to implement, we have to declare the attribute's value type
- I evaluated the path of implementing in Clojure the interface, but I don't see the point since downstream (in
  exporters) we HAVE TO provide the `.getType` method which either will required either reflection or manual hint
- Valid types are: string, boolean, long, double, string array, boolean array, long array, double array
- I tested several hypothesis here that led to the first
  implementation: https://github.com/gabrielnau/opentelemetry-clojure/blob/722263bdea07bcce0e4ce3e46e97e7a5a574f4e2/src/opentelemetry_clj/attribute.clj
- TODO: refine the benchmark with a spec generator to see the use case where we
  hit [attributes limits](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/common.md#attribute-limits)
