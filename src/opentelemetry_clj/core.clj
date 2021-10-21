(ns opentelemetry-clj.core)

;; top level API

;; defn start-new-span tracer {}
;; defn start-new-span {} ;; tracer from global ?
;; defn end-span

;; requirements:
;; - concise API for general use cases
;; - built on top of more simple fn that can be used
;; -

(comment

  (defn span-with-implicit-context [tracer ops])
  (defn span-with-context-value [tracer ops context])

  (defn set-context-as-current! [context & body]
    (with-open [_scope (.makeCurrent context)]))
      ;; body))


  (defmacro go-with-context [context]
    (let [c context]
      (go
        (set-context-as-current!
          context
          &body))))

  (defn with-conveyed-context [context & body])
  (let [conveyed-context context]
    (thread
      (set-context-as-current! conveyed-context))))
      ;; body))