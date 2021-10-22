(ns opentelemetry-clj.sdk.resource
  (:require [clojure.core.protocols :as protocols])
  (:import (io.opentelemetry.sdk.resources Resource)
           (io.opentelemetry.api.common Attributes)))

(defn create
  "TODO: from javadoc https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk/common/src/main/java/io/opentelemetry/sdk/resources/Resource.java#L26"
  ([attributes] (Resource/create ^Attributes attributes))
  ([attributes schema-url] (Resource/create ^Attributes attributes ^String schema-url)))
