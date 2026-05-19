/*
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package io.mapsmessaging.mavlink.codec;

import io.mapsmessaging.mavlink.MavlinkFrameEnvelope;
import io.mapsmessaging.mavlink.framing.*;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.Version;
import io.mapsmessaging.mavlink.signing.NoSigningKeyProvider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MAVLink full-frame codec (framing + CRC + optional signature bytes + payload encode/decode).
 *
 * <p>This codec combines:</p>
 * <ul>
 *   <li>Frame decoding from a streaming network buffer via {@link FrameFramer}</li>
 *   <li>Frame packing (including CRC) via {@link FramePacker}</li>
 *   <li>Payload field encoding/decoding via {@link MavlinkCodec}</li>
 * </ul>
 *
 * <h2>Buffer contract</h2>
 * <ul>
 *   <li>{@link #tryUnpackFrame(ByteBuffer)} consumes a network-owned {@link ByteBuffer} in write-mode and
 *       may {@code flip}/{@code compact} internally.</li>
 *   <li>{@link #packFrame(ByteBuffer, Frame)} writes a complete MAVLink frame into the provided output
 *       buffer at its current position.</li>
 * </ul>
 */
public final class MavlinkFrameCodec {

  private final MavlinkCodec payloadCodec;
  private final FrameFramer framer;
  private final FramePacker packer;


  public MavlinkFrameCodec(MavlinkCodec payloadCodec) {
    this(payloadCodec, new NoSigningKeyProvider());
  }
  /**
   * Creates a frame codec for the dialect contained in the provided payload codec.
   *
   * @param payloadCodec codec providing the dialect name, message registry, and payload encode/decode
   * @throws NullPointerException if {@code payloadCodec} is {@code null}
   */
  public MavlinkFrameCodec(MavlinkCodec payloadCodec,  SigningKeyProvider signingKeyProvider) {
    this.payloadCodec = Objects.requireNonNull(payloadCodec, "payloadCodec");

    DialectRegistry dialectRegistry = new RegistryAdapter(payloadCodec.getRegistry());

    this.framer = new FrameFramer(dialectRegistry, signingKeyProvider);
    this.packer = new FramePacker(dialectRegistry, signingKeyProvider);
  }

  /**
   * Returns the dialect name for this codec (for example {@code "common"}).
   *
   * @return dialect name
   */
  public String getDialectName() {
    return payloadCodec.getName();
  }

  /**
   * Returns the compiled message registry for the dialect.
   *
   * @return message registry
   */
  public MessageRegistry getRegistry() {
    return payloadCodec.getRegistry();
  }

  /**
   * Attempts to decode a single MAVLink frame from the provided network-owned buffer.
   *
   * <p>Returns {@link Optional#empty()} if a complete valid frame is not yet available.</p>
   *
   * @param networkOwnedBuffer network buffer in write-mode (may be flipped/compacted internally)
   * @return decoded frame if available and valid
   */
  public Optional<Frame> tryUnpackFrame(ByteBuffer networkOwnedBuffer) {
    return framer.tryDecode(networkOwnedBuffer);
  }

  /**
   * Attempts to decode a single MAVLink frame and returns header + raw payload bytes only.
   *
   * <p>This does NOT decode payload fields. It is intended for routing decisions based on header metadata.</p>
   *
   * @param networkOwnedBuffer network buffer in write-mode (may be flipped/compacted internally)
   * @return envelope containing header fields + raw payload bytes if a complete valid frame is available
   */
  public Optional<MavlinkFrameEnvelope> tryUnpackHeaderAndPayload(ByteBuffer networkOwnedBuffer) {
    Optional<Frame> decoded = framer.tryDecode(networkOwnedBuffer);
    if (decoded.isEmpty()) {
      return Optional.empty();
    }

    Frame frame = decoded.get();
    byte[] payload = Objects.requireNonNull(frame.getPayload(), "frame.payload");

    MavlinkFrameEnvelope envelope = new MavlinkFrameEnvelope(
        frame.getVersion(),
        frame.getMessageId(),
        frame.getSystemId(),
        frame.getComponentId(),
        frame.getSequence(),
        frame.getPayloadLength(),
        payload,
        frame.isSigned()
    );

    return Optional.of(envelope);
  }

  /**
   * Packs a MAVLink frame into the provided output buffer at its current position.
   *
   * <p>CRC is computed using the dialect registry and written into the frame.</p>
   *
   * @param out output buffer to write into (at current position)
   * @param frame frame to pack
   */
  public void packFrame(ByteBuffer out, Frame frame) {
    packer.pack(out, frame);
  }

  /**
   * Parses the payload of a decoded MAVLink frame into a field map.
   *
   * @param frame decoded frame containing {@code messageId} and payload bytes
   * @return field map keyed by field name
   * @throws IOException if payload decoding fails for the message type
   * @throws NullPointerException if {@code frame} or its payload is {@code null}
   */
  public Map<String, Object> parsePayload(Frame frame) throws IOException {
    Objects.requireNonNull(frame, "frame");
    byte[] payload = Objects.requireNonNull(frame.getPayload(), "frame.payload");

    return payloadCodec.parsePayload(frame.getMessageId(), payload);
  }

  /**
   * Encodes the supplied field map into payload bytes for the given message id.
   *
   * @param messageId MAVLink message id
   * @param values field values keyed by field name
   * @return encoded payload bytes
   * @throws IOException if payload encoding fails for the message type or values
   */
  public byte[] encodePayload(int messageId, Map<String, Object> values) throws IOException {
    return payloadCodec.encodePayload(messageId, values);
  }

  /**
   * Encodes the supplied field map and populates the provided frame with {@code messageId},
   * payload bytes, and payload length.
   *
   * @param frame target frame to update
   * @param messageId MAVLink message id
   * @param values field values keyed by field name
   * @throws IOException if payload encoding fails
   * @throws NullPointerException if {@code frame} or {@code values} is {@code null}
   */
  public void encodePayloadIntoFrame(Frame frame, int messageId, Map<String, Object> values) throws IOException {
    Objects.requireNonNull(frame, "frame");
    Objects.requireNonNull(values, "values");

    byte[] payloadBytes = payloadCodec.encodePayload(messageId, values);

    frame.setMessageId(messageId);
    frame.setPayload(payloadBytes);
    frame.setPayloadLength(payloadBytes.length);
  }

  /**
   * Adapter from the compiled registry to the framing requirements (CRC extra and minimum/base payload length).
   */
  private static final class RegistryAdapter implements DialectRegistry {

    private final MessageRegistry registry;

    private RegistryAdapter(MessageRegistry registry) {
      this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Returns the CRC extra byte for a message id.
     *
     * @param version MAVLink version
     * @param messageId MAVLink message id
     * @return CRC extra byte (0..255)
     * @throws IllegalArgumentException if the message id is unknown to the registry
     */
    @Override
    public int crcExtra(Version version, int messageId) {
      CompiledMessage compiled = registry.getCompiledMessagesById().get(messageId);
      if (compiled == null) {
        throw new IllegalArgumentException("Unknown MAVLink message id: " + messageId);
      }
      return compiled.getCrcExtra() & 0xFF;
    }

    /**
     * Returns the minimum payload length required for a message id.
     *
     * <p>For MAVLink v1 this is the fixed payload size.
     * For MAVLink v2 this is the base payload size (extensions are optional).</p>
     *
     * @param version MAVLink version
     * @param messageId MAVLink message id
     * @return minimum required payload length in bytes; {@link Integer#MAX_VALUE} if unknown
     */
    @Override
    public int minimumPayloadLength(Version version, int messageId) {
      CompiledMessage compiled = registry.getCompiledMessagesById().get(messageId);
      if (compiled == null) {
        return Integer.MAX_VALUE; // force failure
      }

      if (version == Version.V1) {
        return compiled.getPayloadSizeBytes();
      }
      return compiled.getMinimumPayloadSizeBytes();
    }
  }
}