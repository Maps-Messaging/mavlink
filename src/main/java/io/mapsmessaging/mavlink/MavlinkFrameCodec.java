/*
 *
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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.framing.MavlinkDialectRegistry;
import io.mapsmessaging.mavlink.framing.MavlinkFrameFramer;
import io.mapsmessaging.mavlink.framing.MavlinkFramePacker;
import io.mapsmessaging.mavlink.message.MavlinkFrame;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkVersion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MAVLink full-frame codec (framing + CRC + optional signature bytes + payload encode/decode).
 *
 * Buffer contract:
 * - Unpack uses a network-owned ByteBuffer in write-mode. It flips/compacts internally.
 * - Pack writes a complete MAVLink frame to the provided output buffer at its current position.
 */
public final class MavlinkFrameCodec {

  private final MavlinkCodec payloadCodec;
  private final MavlinkFrameFramer framer;
  private final MavlinkFramePacker packer;

  public MavlinkFrameCodec(MavlinkCodec payloadCodec) {
    this.payloadCodec = Objects.requireNonNull(payloadCodec, "payloadCodec");

    MavlinkDialectRegistry dialectRegistry = new RegistryAdapter(payloadCodec.getRegistry());

    this.framer = new MavlinkFrameFramer(dialectRegistry);
    this.packer = new MavlinkFramePacker(dialectRegistry);
  }

  public String getDialectName() {
    return payloadCodec.getName();
  }

  public MavlinkMessageRegistry getRegistry() {
    return payloadCodec.getRegistry();
  }

  /**
   * Attempts to decode a single MAVLink frame from the provided network-owned buffer.
   * Returns empty if no full valid frame is available yet.
   */
  public Optional<MavlinkFrame> tryUnpackFrame(ByteBuffer networkOwnedBuffer) {
    return framer.tryDecode(networkOwnedBuffer);
  }

  /**
   * Packs a MAVLink frame into the provided output buffer at its current position.
   * CRC is computed using the dialect registry and written into the frame.
   */
  public void packFrame(ByteBuffer out, MavlinkFrame frame) {
    packer.pack(out, frame);
  }

  /**
   * Parses the payload of a decoded MAVLink frame into a field map.
   */
  public Map<String, Object> parsePayload(MavlinkFrame frame) throws IOException {
    Objects.requireNonNull(frame, "frame");
    byte[] payload = Objects.requireNonNull(frame.getPayload(), "frame.payload");

    return payloadCodec.parsePayload(frame.getMessageId(), payload);
  }

  /**
   * Encodes the supplied field map into payload bytes for the given messageId.
   */
  public byte[] encodePayload(int messageId, Map<String, Object> values) throws IOException {
    return payloadCodec.encodePayload(messageId, values);
  }

  /**
   * Helper: encode payload and populate the provided frame (messageId + payload bytes + payloadLength).
   */
  public void encodePayloadIntoFrame(MavlinkFrame frame, int messageId, Map<String, Object> values) throws IOException {
    Objects.requireNonNull(frame, "frame");
    Objects.requireNonNull(values, "values");

    byte[] payloadBytes = payloadCodec.encodePayload(messageId, values);

    frame.setMessageId(messageId);
    frame.setPayload(payloadBytes);
    frame.setPayloadLength(payloadBytes.length);
  }

  /**
   * Adapter from compiled registry to framing needs (CRC extra + minimum/base payload length).
   */
  private static final class RegistryAdapter implements MavlinkDialectRegistry {

    private final MavlinkMessageRegistry registry;

    private RegistryAdapter(MavlinkMessageRegistry registry) {
      this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public int crcExtra(MavlinkVersion version, int messageId) {
      MavlinkCompiledMessage compiled = registry.getCompiledMessagesById().get(messageId);
      if (compiled == null) {
        throw new IllegalArgumentException("Unknown MAVLink message id: " + messageId);
      }
      return compiled.getCrcExtra() & 0xFF;
    }

    @Override
    public int minimumPayloadLength(MavlinkVersion version, int messageId) {
      MavlinkCompiledMessage compiled = registry.getCompiledMessagesById().get(messageId);
      if (compiled == null) {
        return Integer.MAX_VALUE; // force failure
      }

      // V1: fixed payload size. V2: base length (extensions optional).
      // Assuming your compiled message exposes both sizes; if not, see notes below.
      if (version == MavlinkVersion.V1) {
        return compiled.getPayloadSizeBytes();
      }
      return compiled.getMinimumPayloadSizeBytes();
    }
  }
}
