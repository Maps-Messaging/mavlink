# MAVLink library release notes

## 3.0.0: changes since 2.0.0

**Repository:** [Maps-Messaging/mavlink](https://github.com/Maps-Messaging/mavlink)  
**Comparison:** [`main` → `development`](https://github.com/Maps-Messaging/mavlink/compare/main...development)  
**Baseline:** `main` at `e355405b7108d26e77468903859b8dc1c1feda2b` (`2.0.0`)  
**Reviewed implementation:** `development` at `e3b8737c7247ac9bb380b4ee415da32d2a8d2342` (`2.0.1-SNAPSHOT`)  
**Prepared:** 18 October 2026

> **Major release:** version 3.0.0 reflects the removal of the public
> `V2FrameSigning.appendSignature()` API. This method was removed rather than deprecated.

### Highlights

- MAVLink 1 and MAVLink 2 framing, decoding, CRC handling and MAVLink 2 signing were corrected for protocol compliance.
- Added configurable MAVLink 2 signing policies, link IDs, shared replay/timestamp state, and optional file-backed state persistence.
- Preserved **unsigned MAVLink 1 and MAVLink 2 support by default** for existing callers and configurations.
- Added a telemetry `.tlog` writer with buffering, rotation, compression, retention, startup recovery and diagnostics.
- Improved dialect loading, payload type handling, security checks and regression coverage.
- Updated CI, static analysis, dependency-check caching and release-artifact signing.

### Breaking API change

**Removed `V2FrameSigning.appendSignature(ByteBuffer, int, long, byte[])`.** It was not a
specification-compliant way to sign an already packed unsigned MAVLink 2 packet: changing
the signed incompatibility flag also requires recomputing the CRC using the message dialect's
`CRC_EXTRA`. The method was **removed, not deprecated**.

**Migration:** set `Frame.isSigned(true)`, provide a valid 32-byte key using
`SigningKeyProvider`, and sign through `FramePacker.pack(...)` or
`MavlinkFrameCodec.packFrame(...)`. Those paths have dialect-aware framing and CRC
calculation. See [the removal commit](https://github.com/Maps-Messaging/mavlink/commit/e3b8737c7247ac9bb380b4ee415da32d2a8d2342)
and [MSG-372](https://mapsmessaging.atlassian.net/browse/MSG-372).

Also review callers with exhaustive switches over `FrameFailureReason`: new values
`SIGNATURE_UNVERIFIED`, `SIGNATURE_REPLAY`, `SIGNATURE_STALE` and
`UNSIGNED_REJECTED` have been added. Protocol validation is stricter for invalid sender
identities, unsupported incompatibility flags, malformed CRCs and MAVLink 1 fixed payload
lengths. Correctly formed unsigned traffic continues to be accepted by the default policy.

### MAVLink framing, payloads and signing — MSG-372

- Fixed MAVLink 1 outgoing CRC computation and fixed/base payload-length handling, including
  dialect messages with MAVLink 2 extension fields.
- Corrected MAVLink 2 trailing-zero payload truncation and reception of truncated scalar,
  array and extension data; valid non-trimmed MAVLink 2 frames remain supported.
- Corrected the signature digest to authenticate the 32-byte key, packet including CRC,
  link ID and 48-bit timestamp, with constant-time signature comparison.
- Switched signing timestamps to MAVLink's 2015 epoch and 10-microsecond ticks, with
  monotonic progression for logical sender/link state.
- Added receive replay, rollback and stale-timestamp handling keyed by sender system,
  component and signing link.
- Added `MavlinkSigningConfig` and `MavlinkSigningPolicy` to configure signing
  independently of the existing unsigned defaults. Existing codec constructors remain.
- Added `SigningState`, `SigningStreamId`, `SigningStateSnapshot`, `SigningStateStore`
  and `FileSigningStateStore`. The file store includes integrity checking and atomic
  replacement; signing keys are not saved with timestamp state.
- Rejects malformed source identifiers and unknown MAVLink 2 incompatibility flags while
  continuing to tolerate unknown compatibility flags.
- Invalid CRC, unauthenticated signed frames and replays no longer enter normal payload
  decoding or header-only routing.
- Added signing, replay, concurrency, policy, timestamp, state-store, malformed-frame,
  HEARTBEAT and COMMAND_ACK regression tests.

**Compatibility:** the default codec continues to accept valid **unsigned MAVLink 1 and 2**
frames without signing configuration. Server-level signing integration is a separate concern
and is not included here. Physical PX4/ArduPilot drone checks are operational validation,
not an issue-closure or release-notes dependency.

**Jira:** [MSG-372 — MAVLink wire encoding and signing compliance](https://mapsmessaging.atlassian.net/browse/MSG-372) (Done).

### Telemetry logging and reliability — feature commits and MSG-345

- Added `MavlinkTlogWriter`, `TlogConfiguration`, `RotatingTlogFile`,
  `TlogArchiveManager`, `TlogRecord` and `TlogOutput`.
- Added a bounded asynchronous write queue, configurable file size/age rotation,
  optional compression, archive retention, startup recovery, periodic flush and
  accepted/written/dropped counters with writer health/failure reporting.
- Tlog reliability fixes preserve thread interruption during shutdown, use `long`
  for accumulated file sizes, and compare record/frame byte-array contents correctly.
- Expanded tlog writer, archive, configuration and record regression coverage.
- Raw MAVLink frame bytes are now retained in `ProcessedFrame` for diagnostics and
  traceability.

**Source:** [telemetry-log introduction](https://github.com/Maps-Messaging/mavlink/commit/ed0b649b33)
(unlinked feature commit) and
[MSG-345 — Resolve MAVLink SonarCloud findings](https://mapsmessaging.atlassian.net/browse/MSG-345)
(Done; includes tlog reliability fixes and additional tests).

### Dialects, codecs and robustness — reviewed commits without Jira keys

- Improved bundled dialect resolution and lazy classpath loading, direct file-path
  loading, include resolution and common-dialect initialization.
- Strengthened field encoding and type validation across signed/unsigned integers,
  floating-point values, numeric strings, enums and arrays.
- Improved handling of truncated character fields, XML enum values and payload JSON
  schema validation.
- Added XML parser protection against external entities, plus parser/codec boundary
  and regression tests.
- Expanded full-frame, JSON codec, dialect-loader and schema test coverage.

Notable source commits: [dialect loading](https://github.com/Maps-Messaging/mavlink/commit/006402838e),
[field-codec validation](https://github.com/Maps-Messaging/mavlink/commit/e1695d392f),
[truncated character fields](https://github.com/Maps-Messaging/mavlink/commit/7678a48483),
[raw-frame capture](https://github.com/Maps-Messaging/mavlink/commit/7e34bf3de2).

These changes had **no Jira key in their commit messages**. They are described here
based on the source diff and commit content, not assigned to a Jira retrospectively.

### Security, quality and CI

- Closed the scoped MAVLink static-analysis remediation work, with tests around
  affected framing, codec, parser and tlog paths.
  [MSG-345](https://mapsmessaging.atlassian.net/browse/MSG-345) (Done).
- Standardized SonarCloud reporting against `development`, using
  `sonar.token`, test-class output and JaCoCo XML report paths.
  [MSG-341](https://mapsmessaging.atlassian.net/browse/MSG-341) (Done).
- Configured Buildkite workers to consume the shared OWASP Dependency-Check 13
  cache rather than performing a full NVD bootstrap on every ephemeral agent.
  [MSG-314](https://mapsmessaging.atlassian.net/browse/MSG-314) (Done).
- Updated Buildkite snapshot and release agent queues to `java_build_queue_aws`;
  snapshot CI runs `mvn clean deploy -Psnapshot` followed by Sonar analysis.
- Updated release publishing configuration, including Maven Central publication
  support and GPG artifact signing.

### Dependency and build-tool updates

Notable version changes observed in the Maven POM from `main` to `development`:

| Component | Before | Development |
| --- | --- | --- |
| OWASP Dependency-Check Maven plugin | 12.1.8 | 13.0.0 |
| JSON Schema Validator | 1.5.1 | 3.0.8 |
| Gson | 2.13.2 | 2.14.0 |
| Swagger annotations | 2.2.50 | 2.2.55 |
| Lombok | 1.18.38 / 1.18.42 | 1.18.48 |
| JUnit Jupiter engine | 6.0.1 | 6.1.3 |
| Mockito core / Jupiter | 5.23.0 | 5.24.0 |
| Maven Compiler plugin | 3.14.1 | 3.16.0 |
| Maven Surefire plugin | earlier baseline | 3.6.0 |
| Maven Deploy plugin | 3.1.1 | 3.2.0 |
| Maven Central Publishing plugin | 0.9.0 | 0.11.1 |

Other routine Dependabot updates include CycloneDX, build-helper,
Sonar Maven plugin and additional Maven build plugins.

### Traceability

The `main` → `development` comparison contains **135 commits across 61 changed paths**
before this release-notes commit. Jira keys present in those commit messages are:

| Jira | Scope in this repository | Status |
| --- | --- | --- |
| [MSG-372](https://mapsmessaging.atlassian.net/browse/MSG-372) | Signing, MAVLink 1/2 framing, replay/state, backwards compatibility and public API cleanup | Done |
| [MSG-345](https://mapsmessaging.atlassian.net/browse/MSG-345) | SonarCloud remediation, regression coverage and tlog reliability | Done |
| [MSG-341](https://mapsmessaging.atlassian.net/browse/MSG-341) | SonarCloud attribution, token and coverage reporting | Done |
| [MSG-314](https://mapsmessaging.atlassian.net/browse/MSG-314) | OWASP NVD cache usage in the build pipeline | Done |

The history also contains automated dependency-update commits and manual commits
without Jira keys. These are grouped above by actual changes; a Jira is not inferred
where no key was present.

### Validation and release preparation

- The maintainer confirmed the full local MAVLink suite **all green** on
  `development` after the MAVLink 1 payload-length and malformed-sender regressions
  were corrected. The earlier failing run reported 1,649 tests, 0 failures and
  6 errors, before those fixes.
- The later breaking-method-removal commit `e3b8737c` updated corresponding tests;
  **a separate full Maven result for this precise HEAD has not been supplied**.
  Avoid presenting the old green run as verification of that later commit.
- These notes are based on Git history, current source, associated Jira descriptions
  and the user's local test confirmation; they do **not** certify live drone operation.
- Before publishing: re-run the current `development` suite, inspect Buildkite release publishing
  and signing, and review the final release PR/notes.
- This document describes the 3.0.0 release changes; publication is performed separately.
