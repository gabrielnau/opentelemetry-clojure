
### To use or not the auto-instrumentation agent

When using Agent, you get the following constraints / caveats:

- "direct usage of the OpenTelemetry SDK is not supported when running agent" TODO: source
  ~~- Span implementation when using autoinstrumentation java agent differs from the one from the SDK
    - TODO: ask about this on cncf slack, Span doesn't implement Context interface in agent but it does in api~~
- tracer is built by the agent, thus need to use auto configuration or ?
- Javaagent version vs SDK version, see this slack message:
  > We support all OTel APIs up to the version of the javaagent, but not necessarily newer. The assumption is it’s generally at least as easy to update the javaagent version as an app. And while not impossible for all cases, it’s hard to guarantee compatibility with something that doesn’t exist yet
- https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1926

How to
leverage [supported libraries, frameworks etc](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md)
without running the agent ?

-
example: https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/okhttp/okhttp-3.0/library

- Programmatic injection of agent https://cloud-native.slack.com/archives/C0150QF88FL/p1632249888088300?thread_ts=1631980158.059400&cid=C0150QF88FL

- auto configuration https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure