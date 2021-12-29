
### Context

[Javadoc](https://javadoc.io/doc/io.opentelemetry/opentelemetry-context/latest/io/opentelemetry/context/Context.html):

- While Context objects are immutable they do not place such a restriction on the state they store.
- Context is not intended for passing optional parameters to an API and developers should take care to avoid excessive
  dependence on context when designing an API.
- Attaching Context from a different ancestor will cause information in the current Context to be lost. This should
  generally be avoided.

- don't implement `with` ? use storeInContext instead
  ? https://javadoc.io/static/io.opentelemetry/opentelemetry-context/1.9.1/io/opentelemetry/context/Context.html

- "context object follows execution path of your code"
- - code 2 gif is needed to explain context propagation: https://github.com/phronmophobic/clj-media

### Concurrency primitives

#### future

Solution 1: implicitely convey the Context to the Executor Downsides:

- can be intrusive, and it wraps Agent executor as well

Solution 2: wrap in a lexical scope

#### core.async

We have to differentiate 2 use cases:

1. We use a `go` block or a `thread`: wrap in a lexical scope

- warning about go block and parking/resume on another thread -> macro to help maintain the correct thread local storage

2. We don't have it, need to convey value in channel: todo example of code with aleph for example
