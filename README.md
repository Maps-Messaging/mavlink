# MAVLink Payload Codec (Java)

This repository provides a lightweight, schema-driven MAVLink **payload**
encoder and decoder for Java.

It parses MAVLink XML dialects (including `{@code <include>}` handling),
compiles message definitions, and provides fast, thread-safe encoding and
decoding of MAVLink message **payloads only**.

This library is intentionally limited in scope and designed to be composed
into larger MAVLink-capable systems.

---

## Scope (Read This First)

This library operates **only on MAVLink message payloads**.

It **does**:

- Parse MAVLink XML dialects
- Compile message definitions into an immutable registry
- Encode Java field maps into MAVLink payload bytes
- Decode MAVLink payload bytes into typed field maps
- Correctly handle MAVLink field ordering, arrays, enums, and extensions

It **does not**:

- Manage system IDs, component IDs, or sequence numbers
- Implement any transport (UART, UDP, TCP, etc.)

Frame parsing, CRC handling, and framing are provided by the framework layer
built on top of this codec.

- Manage system IDs, component IDs, or sequence numbers
- Implement any transport (UART, UDP, TCP, etc.)

Whant to know more?
 
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Maps-Messaging/mavlink)
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
- No shared mutable state at runtime

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

No unchecked exceptions escape payload encode/decode paths.

---

## Design Philosophy

- Payloads only, no transport assumptions
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

If you want a full MAVLink stack, use something else.  
If you want a clean, deterministic payload codec, this is it.

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

`V2FrameSigning.appendSignature` is deprecated because appending raw bytes to an unsigned packet
cannot update its signed flag and CRC without dialect context. Prefer `FramePacker` or
`MavlinkFrameCodec.packFrame`.

## License

Apache License 2.0

See the `LICENSE` file for details.

