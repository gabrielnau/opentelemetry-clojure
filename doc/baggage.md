
### Baggage

- link to spec https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/baggage/api.md
- metadata field:
  > Metadata Optional metadata associated with the name-value pair. This should be an opaque wrapper for a string with no semantic meaning. Left opaque to allow for future functionality.

- choices:
    - there is fromContext which returns an empty baggage if none existing
    - there is current which returns an empty baggage if none existing
    - there is fromcontextornull which returns nil or a baggage
    - -> we are more used to this, nil if value doesn't exist
    - -> so propose only this: from-context [] -> current or nul [context] -> fromcontextornull

Operations:

- Extract the Baggage from a Context instance
- Insert the Baggage to a Context instance
