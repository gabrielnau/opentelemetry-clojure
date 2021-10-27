(ns opentelemetry-clj.sdk.resource
  "Implements OpenTelemetry [Resource](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/sdk.md)).

  See [Resource Semantic Conventions](https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/resource/semantic_conventions).

  *Warning:* this namespace requires the dependency `io.opentelemetry/opentelemetry-sdk` that should be set manually or available in classpath if using the java agent.
  "
  (:require [opentelemetry-clj.attribute :as attribute])
  (:import (io.opentelemetry.sdk.resources Resource)
           (io.opentelemetry.api.common Attributes)))

(set! *warn-on-reflection* true)

(defn new
  "Returns a new `Resource` with given `:attributes` and optionnal `:schema-url`

  Arguments:
  - `attributes`: Map of attributes, see [[opentelemetry-clj.attribute/new]]
  - `schema-url`: Optionnal, a string"
  ([attributes] (Resource/create ^Attributes (attribute/attributes attributes)))
  ([attributes schema-url] (Resource/create ^Attributes (attribute/attributes attributes) ^String schema-url)))

;;FIXME: incomplete API, should handle merge etc