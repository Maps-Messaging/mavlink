# MAVLink Payload Codec (Java)

This repository provides a lightweight, schema-driven MAVLink payload
encoder and decoder for Java.

It parses MAVLink XML dialects (including `<include>` handling), compiles
message definitions, and provides fast, thread-safe encoding and decoding
of MAVLink message **payloads**.

This library is intentionally limited in scope.


## Scope (Important)

This library operates **only on MAVLink message payloads**.

It does **not**:

- Parse or generate MAVLink v1/v2 frames
- Handle headers, CRCs, signing, or message framing
- Manage system IDs, component IDs, or sequence numbers
- Implement any transport (UART, UDP, TCP, etc.)

If you need a full MAVLink stack, this library is not it.

This library is designed to sit **under** a framing and transport layer.


## Features

- MAVLink XML dialect parsing with `<include>` support
- Canonical dialect compilation (e.g. `common.xml`)
- Immutable, thread-safe compiled registries
- Correct MAVLink field ordering and packing rules
- Extension field handling (present vs omitted)
- Array fields and `char[]` handling
- Enum and bitmask enum resolution
- Safe failure on invalid or truncated payloads
- No shared mutable state at runtime


## Getting Started

### Load the built-in `common` dialect

```java
MavlinkMessageFormatLoader loader =
    MavlinkMessageFormatLoader.getInstance();

MavlinkCodec codec =
    loader.getDialectOrThrow("common");
```


## Encoding a Payload

Example: `SYS_STATUS` (message id `1`)

```java
Map<String, Object> values = Map.of(
    "onboard_control_sensors_present", 1,
    "onboard_control_sensors_enabled", 1,
    "onboard_control_sensors_health", 1,
    "load", 250,
    "voltage_battery", 12000,
    "current_battery", 100,
    "battery_remaining", 90
);

byte[] payload = codec.encodePayload(1, values);
```


## Decoding a Payload

```java
Map<String, Object> decoded =
    codec.parsePayload(1, payload);

int load = (int) decoded.get("load");
```


## Loading Custom Dialects

You can load MAVLink dialects from any `InputStream`:

```java
try (InputStream xml = Files.newInputStream(Path.of("custom.xml"))) {
    MavlinkCodec custom =
        loader.loadDialect("custom", xml, includeResolver);
}
```

Dialects are compiled once and cached by name.


## Thread Safety

- `MavlinkCodec` is immutable
- `MavlinkMessageRegistry` is immutable
- Encoders and decoders are stateless

A single codec instance may be safely:

- Shared across threads
- Cached globally
- Used in high-throughput systems


## Error Handling

- Unknown message IDs result in `IOException`
- Invalid field types result in `IOException`
- Invalid enum values result in `IOException`
- Truncated or malformed payloads result in `IOException`

No unchecked exceptions escape parsing paths.


## Design Philosophy

- Payloads only, no transport assumptions
- Compile once, run fast
- Immutability over defensive copying
- Explicit errors over silent failure
- Practical MAVLink usage, not theoretical completeness


## License

Apache License 2.0

See the `LICENSE` file for details.


## Intended Users

This library is intended for:

- Protocol bridges
- Schema-driven message systems
- Telemetry ingestion pipelines
- MAVLink-aware tooling that does not want transport coupling

If you want a full MAVLink stack, use something else.
If you want a clean, deterministic payload codec, this is it.
