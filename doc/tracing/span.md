### Span

- kind: list of possible kinds, if not good one fallback on internal, following Java implementation
- usage to build a spanbuilder and start it elsewhere
- Not implemented:
    - Span#wrap
    - Span#getInvalid
    - Span#isRecording -> when no SDK present, implementation is false, when SDK present, it says wether span has ended
      or not, not sure what usage we can get from that. will add if needed
- Spans are created by the SpanBuilder.startSpan() method.

- span granularity:         
  transaction -> the entire trace process -> every network hop library -> every library transition function ->
  granularity if often in the middle of library - function  
  one per lib maybe ? span are more expensive than logs start / end span involves juggling scopes / context
- trace indexing is span based often: can't search for attributes accross multiple spans
- prefer coarsely grained spans, with rich data
- only reason to create a subspan is measuring latency
- choice to propose a new-builder with a map instead of each function, if needed it's simple to use java




- Note about: span per retry in http query https://neskazu.medium.com/thoughts-on-http-instrumentation-with-opentelemetry-9fc22fa35bc7 + So for the
  sake of consistency and to give users better observability, I believe each redirect should be a separate HTTP
  span.