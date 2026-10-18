# MAVLink 3.0.0

Version 3.0.0 adds configurable MAVLink 2 signing and replay protection, improves MAVLink 1 and MAVLink 2 wire compatibility, and introduces a MAVLink telemetry log writer. It also removes an unsafe public signing method, requiring a major-version update for applications that used that method.

The Java library continues to support payload encoding/decoding from MAVLink XML dialects, full-frame processing, and unsigned MAVLink 1 and MAVLink 2 traffic.

## Breaking change: MAVLink 2 signing

**Removed:** `V2FrameSigning.appendSignature(ByteBuffer, int, long, byte[])`. This method was not deprecated. Appending a signature to an already-packed unsigned MAVLink 2 frame is incorrect because setting the signed incompatibility flag changes the header, requiring a dialect-aware CRC recalculation.

**Migration:** mark the outgoing `Frame` as signed using `Frame.isSigned(true)`, provide a 32-byte signing key through `SigningKeyProvider`, and encode through `MavlinkFrameCodec.packFrame(...)` or `FramePacker.pack(...)`. These paths correctly set the signed flag and calculate the CRC and signature together.

Code with exhaustive switches over `FrameFailureReason` should also account for the added `SIGNATURE_UNVERIFIED`, `SIGNATURE_REPLAY`, `SIGNATURE_STALE` and `UNSIGNED_REJECTED` results. Input validation is stricter for malformed sender identities, MAVLink 1 frame lengths, CRCs and unsupported MAVLink 2 incompatibility flags.

## Framing and protocol correctness

- Corrected MAVLink 1 CRC generation and fixed-length base payload handling, including messages whose dialect definitions contain MAVLink 2 extension fields.
- Corrected MAVLink 2 trailing-zero payload trimming and reconstruction of shortened scalar, array and extension fields.
- Improved validation of malformed frames, unsupported flags and invalid sender IDs.
- Prevented invalid CRCs and unauthenticated signed frames from entering normal payload or header-only decoding.

## MAVLink 2 signing and replay protection

- Added configurable `MavlinkSigningPolicy` settings: `DISABLED`, `OPTIONAL` and `REQUIRED`.
- Added configurable outgoing signing link IDs and shared `SigningState` tracking across codec replacements.
- Corrected signing input and timestamp handling, including the MAVLink epoch, 48-bit timestamps and monotonic sender/link progression.
- Added detection of replayed, stale and unverified signatures.
- Added optional file-backed timestamp/replay-state persistence with integrity checks and atomic replacement. Signing keys are not persisted with this state.
- Retained the existing unsigned codec constructor and default unsigned MAVLink 1/MAVLink 2 behaviour. Applications explicitly opt in to signing.

## Telemetry logging

- Added `MavlinkTlogWriter` with bounded asynchronous writes and configurable file size/age rotation.
- Added optional log compression, retention controls, recovery of existing archives and periodic flushing.
- Exposed accepted, written and dropped record counts, and writer-health diagnostics.
- Preserved raw frame bytes on `ProcessedFrame` for telemetry diagnostics.

## Dialects and payload processing

- Improved bundled and custom dialect loading, include resolution, and classpath/file-path lookup.
- Improved integer, floating-point, enum, array and truncated-character field handling.
- Hardened MAVLink XML parsing against external entity declarations.
- Expanded regression coverage of framing, signing, replay handling, codecs, dialects and telemetry-log recovery.

## Compatibility

- Requires **Java 17 or newer**.
- Maven coordinates: `io.mapsmessaging:mavlink:3.0.0`.
- Valid unsigned MAVLink 1 and MAVLink 2 traffic remains supported with the default signing policy.
- This library does not provide a network transport. Applications manage their own serial, UDP or TCP connections.
- For signing examples and payload/frame usage, see [README.md](README.md).
