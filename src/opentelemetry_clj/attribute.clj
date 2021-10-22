(ns opentelemetry-clj.attribute
  (:import (io.opentelemetry.api.common Attributes AttributeKey)
           (clojure.lang Keyword)))

(set! *warn-on-reflection* true)
;; Decisions:
;; - it looks ok to allow mixed keys and let the end user manage them
;; - it is of course way faster to use a typed version of AttributeKey manually instead of relying on reflection
;; - we can allow keyword keys, with a slight performance degradation that should be documented
;; - if using typed AttributeKey, then we can avoid the coercion of the value upfront (TODO: validate with a smoke test with jaeger exporter)
;; - not for raw performance, but for memory churn probably, we should implement an attribute key "store" to easily allow end users to preallocate them
;;   - attribute key preallocation with weak map support, see keywords implementation: https://github.com/clojure/clojure/blob/clojure-1.10.1/src/jvm/clojure/lang/Keyword.java#L32

(defn string-array
  "Helper to build a Java string array"
  [items]
  (into-array String items))

(defn key->String [key]
  (if (keyword? key)
    ^String (-> ^Keyword key .-sym .getName)                ;; Keyword -> Symbol -> name
    ^String (str key)))

(defn build-map [attr-map]
  (let [builder (Attributes/builder)]
    (doseq [a attr-map]
      (.put
        builder
        ^String (key->String (nth a 0))
        (nth a 1)))
    (.build builder)))

(defn build-map-string-keys [attr-map]
  (let [builder (Attributes/builder)]
    (doseq [a attr-map]
      (.put
        builder
        (nth a 0)
        (nth a 1)))
    (.build builder)))

(defn build-map-with-attribute-keys [attr-map]
  (let [builder (Attributes/builder)]
    (doseq [a attr-map]
      (.put
        builder
        ^AttributeKey (nth a 0)
        (nth a 1)))
    (.build builder)))

(defn key->StringOrAttribute [key]
  (if (keyword? key)
    ^String (-> ^Keyword key .-sym .getName)                ;; Keyword -> Symbol -> name
    ^String (str key)))

(defn build-map-with-mixed-keys [attr-map]
  (let [builder (Attributes/builder)]
    (doseq [a attr-map]
      (let [k (nth a 0)]
        (if (instance? AttributeKey k)
          (.put builder ^AttributeKey k (nth a 1))
          (.put builder ^String (key->StringOrAttribute k) (nth a 1)))))
    (.build builder)))

;;stringArrayKey (into-array String ["as" "as"])
;booleanArrayKey boolean-array
;longArrayKey long-array
;doubleArrayKey double-array

;- AttributeKey:
;  - key value must be a keyword or string
;  - value must be typed if untyped key
;  - value MAY (TODO: validate) not be typed if typed key


(comment
  ;; Try different implementations to evaluate choices we have
  (use 'criterium.core)

  ;; Conclusions:
  ;- with string keys 115µs +/- 8
  ;- with keyword keys 142µs +/- 21
  ;- with raw string keys 116µs +/- 9
  ;- with known type keys 3µs +/- 50 (it does call .put AttributeKey<V>, v instead of reflecting on other put implements)
  ;- with know type keys memoized 3µs +/- 600ns
  ;  ->> with such elevated std deviation ?
  ;- with mixed types: AttributeKey / String : 123 +/- 9
  ;  >> OK


  (bench
    (build-map {"foo"  "bar"
                "foo2" (long 121321231)
                "foo3" false
                "foo4" (double 309240293490)
                "foo5" (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
                "foo6" (boolean-array [false true true])
                "foo7" (long-array [1 192 10212 221])
                "foo8" (double-array [10921290 121212])}))
  ; Evaluation count : 708060 in 60 samples of 11801 calls.
  ;             Execution time mean : 84.057787 µs
  ;    Execution time std-deviation : 1.353403 µs
  ;   Execution time lower quantile : 82.942015 µs ( 2.5%)
  ;   Execution time upper quantile : 88.223526 µs (97.5%)
  ;                   Overhead used : 7.835663 ns
  ;
  ;Found 8 outliers in 60 samples (13.3333 %)
  ;	low-severe	 4 (6.6667 %)
  ;	low-mild	 4 (6.6667 %)
  ; Variance from outliers : 1.6389 % Variance is slightly inflated by outliers


  ;; with only string key values instead of calling key->String
  (bench
    (build-map-string-keys {"foo"  "bar"
                            "foo2" (long 121321231)
                            "foo3" false
                            "foo4" (double 309240293490)
                            "foo5" (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
                            "foo6" (boolean-array [false true true])
                            "foo7" (long-array [1 192 10212 221])
                            "foo8" (double-array [10921290 121212])}))
  ;; Evaluation count : 551580 in 60 samples of 9193 calls.
  ;             Execution time mean : 116.515148 µs
  ;    Execution time std-deviation : 9.645522 µs
  ;   Execution time lower quantile : 107.768287 µs ( 2.5%)
  ;   Execution time upper quantile : 139.692659 µs (97.5%)
  ;                   Overhead used : 7.631417 ns
  ;
  ;Found 5 outliers in 60 samples (8.3333 %)
  ;	low-severe	 4 (6.6667 %)
  ;	low-mild	 1 (1.6667 %)
  ; Variance from outliers : 60.2216 % Variance is severely inflated by outliers

  ;; with keyword keys
  (bench
    (build-map {:foo  "bar"
                :foo2 (long 121321231)
                :foo3 false
                :foo4 (double 309240293490)
                :foo5 (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
                :foo6 (boolean-array [false true true])
                :foo7 (long-array [1 192 10212 221])
                :foo8 (double-array [10921290 121212])}))
  ;; Evaluation count : 704520 in 60 samples of 11742 calls.
  ;             Execution time mean : 98.930515 µs
  ;    Execution time std-deviation : 36.660813 µs
  ;   Execution time lower quantile : 83.886252 µs ( 2.5%)
  ;   Execution time upper quantile : 223.599390 µs (97.5%)
  ;                   Overhead used : 7.835663 ns
  ;
  ;Found 6 outliers in 60 samples (10.0000 %)
  ;	low-severe	 2 (3.3333 %)
  ;	low-mild	 4 (6.6667 %)
  ; Variance from outliers : 98.1500 % Variance is severely inflated by outliers

  ;; with known type key:
  (bench
    (build-map-with-attribute-keys
      {(AttributeKey/stringKey "foo")        "bar"
       (AttributeKey/longKey "foo2")         (long 121321231)
       (AttributeKey/booleanKey "foo3")      false
       (AttributeKey/doubleKey "foo4")       (double 309240293490)
       (AttributeKey/stringArrayKey "foo5")  (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
       (AttributeKey/booleanArrayKey "foo6") (boolean-array [false true true])
       (AttributeKey/longArrayKey "foo7")    (long-array [1 192 10212 221])
       (AttributeKey/doubleArrayKey "foo8")  (double-array [10921290 121212])}))

  ;; with preallocated keys: it will only impact the churn

  ;; Evaluation count : 20804460 in 60 samples of 346741 calls.
  ;             Execution time mean : 2.896390 µs
  ;    Execution time std-deviation : 49.891604 ns
  ;   Execution time lower quantile : 2.855121 µs ( 2.5%)
  ;   Execution time upper quantile : 3.038103 µs (97.5%)
  ;                   Overhead used : 7.631417 ns
  ;
  ;Found 9 outliers in 60 samples (15.0000 %)
  ;	low-severe	 4 (6.6667 %)
  ;	low-mild	 5 (8.3333 %)
  ; Variance from outliers : 6.2766 % Variance is slightly inflated by outliers
  (def k1 (AttributeKey/stringKey "foo"))
  (def k2 (AttributeKey/longKey "foo2"))
  (def k3 (AttributeKey/booleanKey "foo3"))
  (def k4 (AttributeKey/doubleKey "foo4"))
  (def k5 (AttributeKey/stringArrayKey "foo5"))
  (def k6 (AttributeKey/booleanArrayKey "foo6"))
  (def k7 (AttributeKey/longArrayKey "foo7"))
  (def k8 (AttributeKey/doubleArrayKey "foo8"))
  (bench
    (build-map-with-attribute-keys
      {k1 "bar"
       k2 (long 121321231)
       k3 false
       k4 (double 309240293490)
       k5 (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
       k6 (boolean-array [false true true])
       k7 (long-array [1 192 10212 221])
       k8 (double-array [10921290 121212])}))
  ;; Evaluation count : 20976060 in 60 samples of 349601 calls.
  ;             Execution time mean : 3.164242 µs
  ;    Execution time std-deviation : 598.342805 ns
  ;   Execution time lower quantile : 2.842310 µs ( 2.5%)
  ;   Execution time upper quantile : 4.929555 µs (97.5%)
  ;                   Overhead used : 7.631417 ns
  ;
  ;Found 7 outliers in 60 samples (11.6667 %)
  ;	low-severe	 3 (5.0000 %)
  ;	low-mild	 4 (6.6667 %)
  ; Variance from outliers : 89.4101 % Variance is severely inflated by outliers


  ;; with mixed keys, like "foo", or :foo, or (AttributeKey/stringKey "foo")
  (bench
    (build-map-with-mixed-keys
      {(AttributeKey/stringKey "foo")       "bar"
       "foo2"                               (long 121321231)
       "foo3"                               false
       "foo4"                               (double 309240293490)
       (AttributeKey/stringArrayKey "foo5") (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
       "foo6"                               (boolean-array [false true true])
       (AttributeKey/longArrayKey "foo7")   (long-array [1 192 10212 221])
       (AttributeKey/doubleArrayKey "foo8") (double-array [10921290 121212])}))
  ;; Evaluation count : 531360 in 60 samples of 8856 calls.
  ;             Execution time mean : 123.473243 µs
  ;    Execution time std-deviation : 7.985436 µs
  ;   Execution time lower quantile : 115.005491 µs ( 2.5%)
  ;   Execution time upper quantile : 139.622943 µs (97.5%)
  ;                   Overhead used : 7.631417 ns
  ;
  ;Found 2 outliers in 60 samples (3.3333 %)
  ;	low-severe	 2 (3.3333 %)
  ; Variance from outliers : 48.4435 % Variance is moderately inflated by outliers



  ;; with typed keys + untyped values
  ;; TODO: check if that will work with exporters, like Jaeger
  (bench
    (build-map-with-attribute-keys
      {(AttributeKey/stringKey "foo")        "bar"
       (AttributeKey/longKey "foo2")         121321231
       (AttributeKey/booleanKey "foo3")      false
       (AttributeKey/doubleKey "foo4")       30924029349.92310293
       (AttributeKey/stringArrayKey "foo5")  ["123123" "adasdasdasdasdasd" "1231@#123"]
       (AttributeKey/booleanArrayKey "foo6") [false true true]
       (AttributeKey/longArrayKey "foo7")    [1 192 10212 221]
       (AttributeKey/doubleArrayKey "foo8")  [109212.90 12121.1231232]}))
  ;; Evaluation count : 24649620 in 60 samples of 410827 calls.
  ;             Execution time mean : 2.565877 µs
  ;    Execution time std-deviation : 175.189442 ns
  ;   Execution time lower quantile : 2.420441 µs ( 2.5%)
  ;   Execution time upper quantile : 3.119749 µs (97.5%)
  ;                   Overhead used : 7.631417 ns
  ;
  ;Found 7 outliers in 60 samples (11.6667 %)
  ;	low-severe	 4 (6.6667 %)
  ;	low-mild	 3 (5.0000 %)
  ; Variance from outliers : 51.7443 % Variance is severely inflated by outliers


  ;; with mixed keys, containing only preallocated attributekeys
  (bench
    (build-map-with-mixed-keys
      {(AttributeKey/stringKey "foo")        "bar"
       (AttributeKey/longKey "foo2")         (long 121321231)
       (AttributeKey/booleanKey "foo3")      false
       (AttributeKey/doubleKey "foo4")       (double 309240293490)
       (AttributeKey/stringArrayKey "foo5")  (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
       (AttributeKey/booleanArrayKey "foo6") (boolean-array [false true true])
       (AttributeKey/longArrayKey "foo7")    (long-array [1 192 10212 221])
       (AttributeKey/doubleArrayKey "foo8")  (double-array [10921290 121212])}))
  ;; Evaluation count : 20049240 in 60 samples of 334154 calls.
  ;             Execution time mean : 3.012571 µs
  ;    Execution time std-deviation : 40.288298 ns
  ;   Execution time lower quantile : 2.975127 µs ( 2.5%)
  ;   Execution time upper quantile : 3.124335 µs (97.5%)
  ;                   Overhead used : 7.835663 ns
  ;
  ;Found 3 outliers in 60 samples (5.0000 %)
  ;	low-severe	 2 (3.3333 %)
  ;	low-mild	 1 (1.6667 %)
  ; Variance from outliers : 1.6389 % Variance is slightly inflated by outliers

  ;; with mixed keys, containing only string keys
  (bench
    (build-map-with-mixed-keys
      {"foo"  "bar"
       "foo2" (long 121321231)
       "foo3" false
       "foo4" (double 309240293490)
       "foo5" (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
       "foo6" (boolean-array [false true true])
       "foo7" (long-array [1 192 10212 221])
       "foo8" (double-array [10921290 121212])}))
  ;; Evaluation count : 709860 in 60 samples of 11831 calls.
  ;             Execution time mean : 85.325562 µs
  ;    Execution time std-deviation : 2.497573 µs
  ;   Execution time lower quantile : 82.792843 µs ( 2.5%)
  ;   Execution time upper quantile : 90.195614 µs (97.5%)
  ;                   Overhead used : 7.835663 ns
  ;
  ;Found 2 outliers in 60 samples (3.3333 %)
  ;	low-severe	 1 (1.6667 %)
  ;	low-mild	 1 (1.6667 %)
  ; Variance from outliers : 15.8224 % Variance is moderately inflated by outliers

  ;; with mixed keys, containing only keyword keys
  ;; NB: quite strange the std deviation is way lower than on the simple case.
  (bench
    (build-map-with-mixed-keys
      {:foo  "bar"
       :foo2 (long 121321231)
       :foo3 false
       :foo4 (double 309240293490)
       :foo5 (string-array ["123123" "adasdasdasdasdasd" "1231@#123"])
       :foo6 (boolean-array [false true true])
       :foo7 (long-array [1 192 10212 221])
       :foo8 (double-array [10921290 121212])}))
  ;; Evaluation count : 706080 in 60 samples of 11768 calls.
  ;             Execution time mean : 91.719398 µs
  ;    Execution time std-deviation : 7.932897 µs
  ;   Execution time lower quantile : 84.708368 µs ( 2.5%)
  ;   Execution time upper quantile : 105.703931 µs (97.5%)
  ;                   Overhead used : 7.835663 ns
  ;
  ;Found 3 outliers in 60 samples (5.0000 %)
  ;	low-severe	 1 (1.6667 %)
  ;	low-mild	 2 (3.3333 %)
  ; Variance from outliers : 63.5292 % Variance is severely inflated by outliers




  :ok)
