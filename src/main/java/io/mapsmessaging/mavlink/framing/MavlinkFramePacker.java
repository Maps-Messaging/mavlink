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
 */

package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.message.MavlinkFrame;
import io.mapsmessaging.mavlink.message.MavlinkVersion;

import java.nio.ByteBuffer;

public final class MavlinkFramePacker {

  private static final int MAVLINK_V1_STX = 0xFE;
  private static final int MAVLINK_V2_STX = 0xFD;

  private static final int MAVLINK_MAX_PAYLOAD_LENGTH = 255;

  private static final int MAVLINK_V1_HEADER_LENGTH = 6;  // LEN, SEQ, SYSID, COMPID, MSGID
  private static final int MAVLINK_V2_HEADER_LENGTH = 10; // LEN, INC, COMP, SEQ, SYSID, COMPID, MSGID(3)

  private static final int CRC_LENGTH = 2;

  private static final int V2_SIGNATURE_LENGTH = 13;
  private static final int V2_INCOMPAT_FLAG_SIGNED = 0x01;

  private final MavlinkDialectRegistry dialectRegistry;

  public MavlinkFramePacker(MavlinkDialectRegistry dialectRegistry) {
    this.dialectRegistry = dialectRegistry;
  }

  /**
   * Packs the supplied MAVLink frame into the provided buffer at the current position.
   * The buffer must be in write-mode and have sufficient remaining space.
   */
  public void pack(ByteBuffer out, MavlinkFrame frame) {
    if (out == null) {
      throw new IllegalArgumentException("out must not be null");
    }
    if (frame == null) {
      throw new IllegalArgumentException("frame must not be null");
    }
    if (frame.getPayloadLength() < 0 || frame.getPayloadLength() > MAVLINK_MAX_PAYLOAD_LENGTH) {
      throw new IllegalArgumentException("Invalid payloadLength: " + frame.getPayloadLength());
    }

    if (frame.getVersion() == null) {
      throw new IllegalArgumentException("frame.version must not be null");
    }

    switch (frame.getVersion()) {
      case V1:
        packV1(out, frame);
        return;

      case V2:
        packV2(out, frame);
        return;

      default:
        throw new IllegalArgumentException("Unsupported MAVLink version: " + frame.getVersion());
    }
  }

  private void packV1(ByteBuffer out, MavlinkFrame frame) {
    int payloadLength = frame.getPayloadLength();
    byte[] payload = frame.getPayload() == null ? new byte[0] : frame.getPayload();

    if (payload.length < payloadLength) {
      throw new IllegalArgumentException("payload length (" + payload.length + ") < payloadLength (" + payloadLength + ")");
    }

    int required = 1 + MAVLINK_V1_HEADER_LENGTH + payloadLength + CRC_LENGTH;
    requireRemaining(out, required);

    int startPosition = out.position();

    out.put((byte) MAVLINK_V1_STX);
    out.put((byte) payloadLength);
    out.put((byte) frame.getSequence());
    out.put((byte) frame.getSystemId());
    out.put((byte) frame.getComponentId());
    out.put((byte) frame.getMessageId());

    out.put(payload, 0, payloadLength);

    int crcExtra = dialectRegistry.crcExtra(MavlinkVersion.V1, frame.getMessageId());
    int checksum = CrcHelper.computeChecksumFromWritten(out, startPosition + 1, MAVLINK_V1_HEADER_LENGTH + payloadLength, crcExtra);

    out.put((byte) (checksum & 0xFF));
    out.put((byte) ((checksum >>> 8) & 0xFF));

    frame.setChecksum(checksum);
    frame.setSigned(false);
    frame.setIncompatibilityFlags((byte) 0);
    frame.setCompatibilityFlags((byte) 0);
    frame.setSignature(null);
  }

  private void packV2(ByteBuffer out, MavlinkFrame frame) {
    int payloadLength = frame.getPayloadLength();
    byte[] payload = frame.getPayload() == null ? new byte[0] : frame.getPayload();

    if (payload.length < payloadLength) {
      throw new IllegalArgumentException("payload length (" + payload.length + ") < payloadLength (" + payloadLength + ")");
    }

    boolean signed = frame.isSigned();
    byte[] signature = frame.getSignature();

    if (signed && (signature == null || signature.length != V2_SIGNATURE_LENGTH)) {
      throw new IllegalArgumentException("Signed v2 frame must have signature[13]");
    }

    int required = 1 + MAVLINK_V2_HEADER_LENGTH + payloadLength + CRC_LENGTH + (signed ? V2_SIGNATURE_LENGTH : 0);
    requireRemaining(out, required);

    int startPosition = out.position();

    out.put((byte) MAVLINK_V2_STX);
    out.put((byte) payloadLength);

    byte incompatibilityFlags = frame.getIncompatibilityFlags();
    if (signed) {
      incompatibilityFlags = (byte) (incompatibilityFlags | V2_INCOMPAT_FLAG_SIGNED);
    } else {
      incompatibilityFlags = (byte) (incompatibilityFlags & ~V2_INCOMPAT_FLAG_SIGNED);
    }

    out.put(incompatibilityFlags);
    out.put(frame.getCompatibilityFlags());

    out.put((byte) frame.getSequence());
    out.put((byte) frame.getSystemId());
    out.put((byte) frame.getComponentId());

    writeUnsigned24BitLittleEndian(out, frame.getMessageId());

    out.put(payload, 0, payloadLength);

    int crcExtra = dialectRegistry.crcExtra(MavlinkVersion.V2, frame.getMessageId());
    int checksum = CrcHelper.computeChecksumFromWritten(out, startPosition + 1, (MAVLINK_V2_HEADER_LENGTH-1) + payloadLength, crcExtra);

    out.put((byte) (checksum & 0xFF));
    out.put((byte) ((checksum >>> 8) & 0xFF));

    if (signed) {
      out.put(signature, 0, V2_SIGNATURE_LENGTH);
    }

    frame.setChecksum(checksum);
    frame.setIncompatibilityFlags(incompatibilityFlags);
  }

  private static void requireRemaining(ByteBuffer out, int required) {
    if (out.remaining() < required) {
      throw new IllegalArgumentException("Insufficient space in output buffer. required=" + required + " remaining=" + out.remaining());
    }
  }

  private static void writeUnsigned24BitLittleEndian(ByteBuffer out, int value) {
    out.put((byte) (value & 0xFF));
    out.put((byte) ((value >>> 8) & 0xFF));
    out.put((byte) ((value >>> 16) & 0xFF));
  }

}
