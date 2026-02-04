# CLAUDE.md

This file provides guidance for AI assistants working with the MAVLink Payload Codec codebase.

## Project Overview

A lightweight, schema-driven MAVLink **payload** encoder and decoder for Java. The library parses MAVLink XML dialects, compiles message definitions, and provides fast, thread-safe encoding and decoding of MAVLink message payloads.

**Key Characteristics:**
- Operates only on MAVLink message **payloads** (not full frames for transport)
- Designed to sit under a framing and transport layer
- Immutable, thread-safe compiled registries
- No shared mutable state at runtime

## Technology Stack

- **Language:** Java 17+
- **Build:** Maven
- **Dependencies:**
  - Lombok (compile-time annotations)
  - Gson (JSON serialization)
  - JUnit Jupiter 6.x (testing)
- **CI/CD:** BuildKite (see `.buildkite/pipeline.yml`)
- **Code Quality:** JaCoCo (coverage), SonarCloud, OWASP dependency-check

## Project Structure

```
src/
├── main/
│   ├── java/io/mapsmessaging/mavlink/
│   │   ├── MavlinkCodec.java              # Core payload codec
│   │   ├── MavlinkFrameCodec.java         # Full frame codec with CRC
│   │   ├── MavlinkMessageFormatLoader.java # Singleton dialect loader
│   │   ├── codec/                          # Encoding/decoding utilities
│   │   │   ├── MavlinkPayloadPacker.java
│   │   │   ├── MavlinkPayloadParser.java
│   │   │   ├── MavlinkJsonCodec.java
│   │   │   └── ...
│   │   ├── framing/                        # Frame parsing/packing
│   │   │   ├── MavlinkFrameFramer.java
│   │   │   ├── MavlinkFramePacker.java
│   │   │   ├── MavlinkV1FrameHandler.java
│   │   │   ├── MavlinkV2FrameHandler.java
│   │   │   └── ...
│   │   ├── message/                        # Message definitions & registry
│   │   │   ├── MavlinkFrame.java
│   │   │   ├── MavlinkMessageRegistry.java
│   │   │   ├── MavlinkCompiledMessage.java
│   │   │   ├── MavlinkCompiledField.java
│   │   │   └── fields/                     # Field type codecs
│   │   │       ├── AbstractMavlinkFieldCodec.java
│   │   │       ├── Int8FieldCodec.java
│   │   │       ├── UInt16FieldCodec.java
│   │   │       └── ...
│   │   └── parser/                         # XML dialect parsing
│   │       ├── MavlinkXmlParser.java
│   │       ├── MavlinkDialectLoader.java
│   │       └── MavlinkIncludeResolver.java
│   └── resources/mavlink/
│       ├── common.xml                      # Built-in MAVLink common dialect
│       ├── minimal.xml
│       └── standard.xml
└── test/java/io/mapsmessaging/mavlink/
    ├── MavlinkRoundTripAllMessagesTest.java
    ├── MavlinkFrameRoundTripAllMessagesTest.java
    ├── MavlinkTestSupport.java             # Test utilities
    └── ...
```

## Build Commands

```bash
# Compile and run tests
mvn clean test

# Full build with package
mvn clean package

# Build skipping GPG signing (for local development)
mvn clean package -Dgpg.skip=true

# Run tests with coverage report
mvn clean test jacoco:report

# Build snapshot (uses snapshot profile)
mvn clean deploy -Psnapshot -Dgpg.skip=true

# Run SonarCloud analysis (requires SONAR_TOKEN)
mvn sonar:sonar -Dsonar.login=${SONAR_TOKEN}
```

## Architecture Overview

### Entry Points

1. **`MavlinkMessageFormatLoader`** (Singleton)
   - Primary entry point for loading MAVLink dialects
   - The "common" dialect is loaded eagerly at startup
   - Use `getInstance()` to get the singleton, then `getDialectOrThrow("common")`

2. **`MavlinkCodec`**
   - Encodes/decodes **payloads only**
   - Thread-safe, immutable after construction
   - Key methods: `parsePayload(messageId, bytes)`, `encodePayload(messageId, values)`

3. **`MavlinkFrameCodec`**
   - Full MAVLink frame handling (headers, CRC, signatures)
   - Wraps a `MavlinkCodec` and adds framing capabilities
   - Key methods: `tryUnpackFrame(ByteBuffer)`, `packFrame(ByteBuffer, MavlinkFrame)`

### Design Principles

- **Payloads only:** No transport assumptions; framing/transport handled externally
- **Compile once, run fast:** Dialect definitions compiled into immutable registries
- **Immutability over defensive copying:** Registry, codec, and messages are immutable
- **Explicit errors over silent failure:** All errors throw `IOException`
- **Thread safety:** All codecs and registries are safe for concurrent use

### MAVLink Version Support

- **MAVLink v1:** Legacy protocol (STX `0xFE`)
- **MAVLink v2:** Current protocol (STX `0xFD`) with extension fields

## Key Classes Reference

| Class | Purpose |
|-------|---------|
| `MavlinkCodec` | Payload encode/decode for a dialect |
| `MavlinkFrameCodec` | Full frame codec with CRC handling |
| `MavlinkMessageFormatLoader` | Singleton dialect loader and cache |
| `MavlinkMessageRegistry` | Compiled message definitions for a dialect |
| `MavlinkCompiledMessage` | Single compiled message with fields |
| `MavlinkCompiledField` | Single compiled field with codec |
| `MavlinkFrame` | Decoded MAVLink frame (mutable DTO) |
| `MavlinkXmlParser` | Parses MAVLink XML dialect files |
| `MavlinkDialectLoader` | Handles `<include>` resolution |

## Code Conventions

### General Style

- Uses Lombok annotations (`@Data`, `@Getter`, `@Setter`) for boilerplate reduction
- Private constructors for utility classes
- `Objects.requireNonNull()` for parameter validation
- All public methods document nullability via JavaDoc

### Error Handling

- All parsing/encoding errors throw `IOException`
- Unknown message IDs → `IOException`
- Invalid field types → `IOException`
- Truncated/malformed payloads → `IOException`
- No unchecked exceptions escape payload encode/decode paths

### Naming Conventions

- Classes: PascalCase with `Mavlink` prefix (e.g., `MavlinkCodec`)
- Methods: camelCase (e.g., `parsePayload`, `encodePayload`)
- Constants: UPPER_SNAKE_CASE
- Package structure: `io.mapsmessaging.mavlink.<subpackage>`

### Thread Safety

- Codecs and registries are immutable and thread-safe
- Use `ConcurrentHashMap` for caching (see `MavlinkMessageFormatLoader`)
- No synchronization needed on codec instances

## Testing Guidelines

### Test Structure

- Tests located in `src/test/java/io/mapsmessaging/mavlink/`
- Use JUnit Jupiter (JUnit 5) with `@Test` and `@TestFactory`
- Dynamic tests (`DynamicTest`) for parameterized message testing
- Test utilities in `MavlinkTestSupport.java`

### Test Categories

1. **Round-trip tests:** Encode → decode verification for all messages
2. **Concurrency tests:** Parallel encode/decode with no shared state issues
3. **Extension tests:** MAVLink v2 extension field handling
4. **Robustness tests:** Invalid/truncated payload handling
5. **Array tests:** Numeric and char array field handling

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MavlinkRoundTripAllMessagesTest

# Run with verbose output
mvn test -Dtest=MavlinkRoundTripAllMessagesTest -X
```

### Writing New Tests

```java
// Use MavlinkTestSupport for common operations
MavlinkCodec codec = MavlinkTestSupport.codec();
MavlinkMessageRegistry registry = codec.getRegistry();

// Access field definitions via reflection helper
MavlinkFieldDefinition fd = MavlinkTestSupport.fieldDefinition(compiledField);

// Standard assertions
byte[] payload = codec.encodePayload(messageId, values);
Map<String, Object> decoded = codec.parsePayload(messageId, payload);
```

## Common Tasks

### Adding a New Field Type Codec

1. Create class in `io.mapsmessaging.mavlink.message.fields`
2. Extend `AbstractMavlinkFieldCodec`
3. Implement `encode()`, `decode()`, `getSizeInBytes()`
4. Register in `MavlinkFieldCodecFactory.createCodec()`

### Loading a Custom Dialect

```java
MavlinkMessageFormatLoader loader = MavlinkMessageFormatLoader.getInstance();
try (InputStream xml = Files.newInputStream(Path.of("custom.xml"))) {
    MavlinkCodec codec = loader.loadDialect("custom", xml, includeResolver);
}
```

### Working with JSON

```java
MavlinkJsonCodec jsonCodec = new MavlinkJsonCodec(dialectName, packer, mapConverter);
JsonObject json = jsonCodec.toJson(frame);
byte[] frameBytes = jsonCodec.fromJson(jsonObject);
```

## Important Files

| File | Description |
|------|-------------|
| `pom.xml` | Maven configuration with all dependencies and plugins |
| `.buildkite/pipeline.yml` | CI/CD pipeline configuration |
| `src/main/resources/mavlink/common.xml` | Built-in MAVLink common dialect |
| `LICENSE` | Apache License 2.0 with Commons Clause |

## Troubleshooting

### "Unknown MAVLink dialect" error
The dialect must be loaded before use. The "common" dialect is auto-loaded; custom dialects require explicit loading via `loadDialect()`.

### "Unknown MAVLink message id" error
The message ID is not defined in the loaded dialect. Check that you're using the correct dialect and that the message exists in the XML definition.

### Payload size mismatch
MAVLink v2 supports extension fields which may cause variable payload sizes. Use `getMinimumPayloadSizeBytes()` for base size vs `getPayloadSizeBytes()` for full size.

### Build failures with GPG
For local development, skip GPG signing: `mvn clean package -Dgpg.skip=true`

## License

Apache License 2.0 with Commons Clause. See `LICENSE` file for details.

---

*This file is intended to help AI assistants understand and work with this codebase effectively.*
