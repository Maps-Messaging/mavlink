# MAVLink Java Codec, Framing and Telemetry

[Release notes for version 3.0.0](RELEASE_NOTES.md)

This Java library parses MAVLink XML dialects and provides MAVLink 1 and MAVLink 2 payload codecs, full-frame encoding and decoding, CRC validation, optional MAVLink 2 signing and replay protection, and telemetry `.tlog` recording. It is transport-independent: applications supply their own serial, UDP or TCP connection.

**Java:** 17 or newer.

### Maven dependency

```xml
<dependency>
  <groupId>io.mapsmessaging</groupId>
  <artifactId>mavlink</artifactId>
  <version>3.0.0</version>
</dependency>
```

### Upgrade from 2.x

Version 3.0.0 intentionally removes `V2FrameSigning.appendSignature(...)`. Applications signing MAVLink 2 packets must use `MavlinkFrameCodec.packFrame(...)` or `FramePacker.pack(...)` with `Frame.isSigned(true)` and an appropriate signing key. See the [3.0.0 release notes](RELEASE_NOTES.md) for details.

The default codec configuration continues to support unsigned MAVLink 1 and MAVLink 2 traffic.

---

## Features

- MAVLink XML dialect parsing with `{@code <include>}` support
- Canonical dialect compilation (e.g. `common.xml`)
- Immutable, thread-safe compiled registries
- Correct MAVLink field ordering and packing rules
- Extension field handling (present vs omitted)
- Array fields and `char[]` handling
- Enum and bitmask enum resolution
- Safe failure on invalid or truncated payloads
- Immutable compiled dialects; explicit per-link state for signing and telemetry logging

---

## Getting Started

### Load the built-in `common` dialect

```java
MavlinkMessageFormatLoader loader =
    MavlinkMessageFormatLoader.getInstance();

MavlinkCodec codec =
    loader.getDialectOrThrow("common");
```

The built-in `common` dialect is loaded eagerly and is always available.

---

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

---

## Decoding a Payload

```java
Map<String, Object> decoded =
    codec.parsePayload(1, payload);

int load = (int) decoded.get("load");
```

Field values are returned using standard Java types based on the MAVLink
message definition.

---

## Loading Custom Dialects

You can load MAVLink dialects from any `InputStream`:

```java
try (InputStream xml = Files.newInputStream(Path.of("custom.xml"))) {
    MavlinkCodec custom =
        loader.loadDialect("custom", xml, includeResolver);
}
```

- Dialects are compiled once
- Compiled codecs are cached by name
- Subsequent lookups are lock-free

---

## Thread Safety

- `MavlinkCodec` is immutable
- `MavlinkMessageRegistry` is immutable
- Encoders and decoders are stateless

A single codec instance may be safely:

- Shared across threads
- Cached globally
- Used in high-throughput systems

---

## Error Handling

All parsing and encoding errors are reported explicitly:

- Unknown message IDs → `IOException`
- Invalid field types → `IOException`
- Invalid enum values → `IOException`
- Truncated or malformed payloads → `IOException`

Callers should handle the documented checked exceptions and validate external inputs; invalid arguments may also produce unchecked exceptions.

---

## Design Philosophy

- Payload and wire-frame processing without transport assumptions
- Compile once, run fast
- Immutability over defensive copying
- Explicit errors over silent failure
- Practical MAVLink usage, not theoretical completeness

---

## Intended Users

This library is intended for:

- Protocol bridges
- Schema-driven message systems
- Telemetry ingestion pipelines
- MAVLink-aware tooling that does not want transport coupling

This library focuses on codec, framing, signing and telemetry-log functionality; applications remain responsible for transport and higher-level vehicle/session management.

---

## MAVLink 1/2 Full-Frame Encoding and Signing

The `MavlinkCodec` type handles schema payloads. `MavlinkFrameCodec` additionally handles wire framing,
CRC validation, MAVLink 1 fixed-size payloads, MAVLink 2 zero-byte truncation, and optional MAVLink 2 signing.

Existing code using `new MavlinkFrameCodec(payloadCodec)` continues to accept unsigned MAVLink 1 and 2
packets without a signing configuration. Unverified signed packets are **not** treated as authenticated.

To enable signing, configure a stable link ID and a shared signing state for the logical channel:

```java
SigningStateStore store = new FileSigningStateStore(Path.of("data", "mavlink-signing-state.bin"));
SigningState state = new SigningState(store);
MavlinkSigningConfig config = new MavlinkSigningConfig(MavlinkSigningPolicy.OPTIONAL, 7, state);
MavlinkFrameCodec frames = new MavlinkFrameCodec(payloadCodec, signingKeyProvider, config);
```

Set `Frame.isSigned()` when sending an authenticated MAVLink 2 frame. The encoder sets the signed
incompatibility flag, computes CRC over the actual wire payload, and appends the 13-byte signature
using link ID 7 and a monotonic timestamp. Supply a 32-byte key through the existing
`SigningKeyProvider`. No signing keys are saved by `SigningStateStore`.

Incoming policy values are `DISABLED` (legacy unsigned default), `OPTIONAL` (unsigned accepted,
signed packets must authenticate), and `REQUIRED` (only authenticated signed MAVLink 2 accepted).
The disabled and optional policies both permit unsigned inbound packets; neither treats
a signed packet with an unknown key as valid. Outbound signing remains controlled by `Frame.isSigned()`.

Reuse `state` across codec/dialect replacements for the same logical link. The optional file-backed store
saves outgoing and replay timestamps periodically during activity and on `state.close()`.
File corruption fails closed on load; keep persisted state on stable storage. The caller owns state
and must close it on shutdown.

MAVLink 2 signing must use `FramePacker` or `MavlinkFrameCodec.packFrame` with
`Frame.isSigned()` set. Raw signature appending is not supported because updating
the signed header flag changes the CRC, which depends on the message dialect.

## License

Apache License 2.0

See the `LICENSE` file for details.

