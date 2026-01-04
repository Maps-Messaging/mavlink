package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.codec.MavlinkPayloadPacker;
import io.mapsmessaging.mavlink.codec.MavlinkPayloadParser;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import lombok.Getter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
/**
 * MAVLink payload codec for a specific dialect.
 *
 * <p>This class encodes and decodes MAVLink <em>payloads only</em>
 * (the message body as defined by the dialect).</p>
 *
 * <p>It does <strong>not</strong> handle full MAVLink framing, including headers,
 * CRC calculation, signing, or stream parsing. For full-frame handling,
 * see {@link MavlinkFrameCodec}.</p>
 *
 * <p>Each instance is bound to a single dialect via its
 * {@link MavlinkMessageRegistry}.</p>
 */
public final class MavlinkCodec {

  /**
   * Dialect name (for example {@code "common"}).
   */
  @Getter
  private final String name;

  /**
   * Compiled message registry for the dialect.
   */
  @Getter
  private final MavlinkMessageRegistry registry;

  private final MavlinkPayloadPacker payloadPacker;
  private final MavlinkPayloadParser payloadParser;

  /**
   * Creates a payload codec for a specific MAVLink dialect.
   *
   * @param name dialect name
   * @param registry compiled message registry for the dialect
   * @param payloadPacker payload encoder
   * @param payloadParser payload decoder
   * @throws NullPointerException if any argument is {@code null}
   */
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

  /**
   * Decodes a MAVLink payload into a field map for the given message id.
   *
   * @param messageId MAVLink message id
   * @param payloadBytes raw payload bytes
   * @return field map keyed by field name
   * @throws IOException if the message id is unknown or decoding fails
   * @throws NullPointerException if {@code payloadBytes} is {@code null}
   */
  public Map<String, Object> parsePayload(int messageId, byte[] payloadBytes) throws IOException {
    Objects.requireNonNull(payloadBytes, "payloadBytes");

    if (!registry.getCompiledMessagesById().containsKey(messageId)) {
      throw new IOException("Unknown MAVLink message id: " + messageId);
    }

    return payloadParser.parsePayload(messageId, payloadBytes);
  }

  /**
   * Encodes a field map into MAVLink payload bytes for the given message id.
   *
   * @param messageId MAVLink message id
   * @param values field values keyed by field name
   * @return encoded payload bytes
   * @throws IOException if the message id is unknown or encoding fails
   * @throws NullPointerException if {@code values} is {@code null}
   */
  public byte[] encodePayload(int messageId, Map<String, Object> values) throws IOException {
    Objects.requireNonNull(values, "values");

    if (!registry.getCompiledMessagesById().containsKey(messageId)) {
      throw new IOException("Unknown MAVLink message id: " + messageId);
    }

    return payloadPacker.packPayload(messageId, values);
  }
}
