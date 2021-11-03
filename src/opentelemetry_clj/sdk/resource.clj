(ns opentelemetry-clj.sdk.resource
  "Implements OpenTelemetry [Resource](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/sdk.md)).

  See [Resource Semantic Conventions](https://github.com/open-telemetry/opentelemetry-specification/tree/main/specification/resource/semantic_conventions).

  *Warning:* this namespace requires the dependency `io.opentelemetry/opentelemetry-sdk` that should be set manually or available in classpath if using the java agent.
  "
  (:refer-clojure :exclude [merge])
  (:require [opentelemetry-clj.attributes :as attributes])
  (:import (io.opentelemetry.sdk.resources Resource)
           (io.opentelemetry.api.common Attributes)))

(set! *warn-on-reflection* true)

(defn default
  "Returns the default `Resource`, which contains SDK default attributes."
  []
  (Resource/getDefault))

(defn new
  "Build a new `Resource` instance from the given opts map.

  Argument: a map of:
  | key         | description |
  | -------------|-------------|
  | `:attributes | Required, see [[opentelemetry-clj.attributes/new]]
  | `:schema-url` | Optionnal, a string [OpenTelemetry schema URL](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/schemas/overview.md) used to create this Resource"
  [opts]
  (if (:schema-url opts)
    (Resource/create ^Attributes (attributes/new (:attributes opts)) ^String (:schema-url opts))
    (Resource/create ^Attributes (attributes/new (:attributes opts)))))

(defn merge
  "Returns a new `Resource` that consists of the merge of the two given ones.
  Behavior for `attributes` merging is similar to `clojure.core/merge` applied to two maps. Behavior for `schema url` merging is more complex, see [specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/sdk.md#merge).

  If it's needed to merge a Clojure map into a `Resource` instance, you can do:

  ```clojure
  (let [attributes-map {\"foo\" \"bar\"}]
    (resource/merge
      resource
      (resource/new (attributes/new attributes-map))))
  ```

  [Complete specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/sdk.md#merge) of this function."
  [^Resource left ^Resource right]
  (.merge left right))

(defn schema-url
  "Return given `resource`'s [schema URL](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/schemas/overview.md)."
  [^Resource resource]
  (.getSchemaUrl resource))

(defn ^Attributes attributes
  "Returns `Attributes` from the given `resource`. If needed, to convert these `Attributes` into a map, use [[opentelemetry-clj.attributes/->map]]."
  [^Resource resource]
  (.getAttributes resource))

(defn ->map
  "Returns given `resource` as data."
  [^Resource resource]
  {:schema-url (schema-url resource)
   :attributes (attributes/->map (attributes resource))})
