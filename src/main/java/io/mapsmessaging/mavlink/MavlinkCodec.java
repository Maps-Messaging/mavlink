package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.codec.MavlinkPayloadPacker;
import io.mapsmessaging.mavlink.codec.MavlinkPayloadParser;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;

/**
 * MAVLink payload codec for a specific dialect.
 *
 * This class encodes/decodes MAVLink *payloads* (message body only).
 * It does NOT parse or build full MAVLink frames (header, CRC, signing).
 */
public final class MavlinkCodec {

  @Getter
  private final String name;

  @Getter
  private final MavlinkMessageRegistry registry;

  private final MavlinkPayloadPacker payloadPacker;
  private final MavlinkPayloadParser payloadParser;

  public MavlinkCodec(
      String name,
      MavlinkMessageRegistry registry,
      MavlinkPayloadPacker payloadPacker,
      MavlinkPayloadParser payloadParser
  ) {
    this.name = Objects.requireNonNull(name, "name");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.payloadPacker = Objects.requireNonNull(payloadPacker, "payloadPacker");
    this.payloadParser = Objects.requireNonNull(payloadParser, "payloadParser");
  }

  public Map<String, Object> parsePayload(int messageId, byte[] payloadBytes) throws IOException {
    Objects.requireNonNull(payloadBytes, "payloadBytes");

    if (!registry.getCompiledMessagesById().containsKey(messageId)) {
      throw new IOException("Unknown MAVLink message id: " + messageId);
    }

    return payloadParser.parsePayload(messageId, payloadBytes);
  }

  public byte[] encodePayload(int messageId, Map<String, Object> values) throws IOException {
    Objects.requireNonNull(values, "values");

    if (!registry.getCompiledMessagesById().containsKey(messageId)) {
      throw new IOException("Unknown MAVLink message id: " + messageId);
    }

    return payloadPacker.packPayload(messageId, values);
  }
}
