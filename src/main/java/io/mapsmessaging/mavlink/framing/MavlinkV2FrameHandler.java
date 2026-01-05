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
import io.mapsmessaging.mavlink.message.X25Crc;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class MavlinkV2FrameHandler implements MavlinkFrameHandler {

  private static final int STX = 0xFD;

  public static final int INCOMPAT_FLAG_SIGNED = 0x01;

  private static final int HEADER_LENGTH = 10; // LEN, INC, COMP, SEQ, SYSID, COMPID, MSGID(3)
  private static final int CRC_LENGTH = 2;
  private static final int SIGNATURE_LENGTH = 13;

  private final MavlinkDialectRegistry dialectRegistry;

  public MavlinkV2FrameHandler(MavlinkDialectRegistry dialectRegistry) {
    this.dialectRegistry = dialectRegistry;
  }

  @Override
  public int minimumBytesRequiredForHeader() {
    return 1 + HEADER_LENGTH;
  }

  @Override
  public int peekPayloadLength(ByteBuffer buffer, int frameStartIndex) {
    return buffer.get(frameStartIndex + 1) & 0xFF;
  }

  @Override
  public int computeTotalFrameLength(ByteBuffer buffer, int frameStartIndex, int payloadLength) {
    int incompatibilityFlags = buffer.get(frameStartIndex + 2) & 0xFF;
    boolean signed = (incompatibilityFlags & INCOMPAT_FLAG_SIGNED) != 0;
    int signatureBytes = signed ? SIGNATURE_LENGTH : 0;
    return HEADER_LENGTH + payloadLength + CRC_LENGTH + signatureBytes;
  }

  @Override
  public Optional<MavlinkFrame> tryDecode(ByteBuffer candidateFrame) {
    int frameStartIndex = candidateFrame.position();

    int stx = candidateFrame.get(frameStartIndex) & 0xFF;
    if (stx != STX) {
      return Optional.empty();
    }

    int payloadLength = candidateFrame.get(frameStartIndex + 1) & 0xFF;

    byte incompatibilityFlags = candidateFrame.get(frameStartIndex + 2);
    byte compatibilityFlags = candidateFrame.get(frameStartIndex + 3);

    int sequence = candidateFrame.get(frameStartIndex + 4) & 0xFF;
    int systemId = candidateFrame.get(frameStartIndex + 5) & 0xFF;
    int componentId = candidateFrame.get(frameStartIndex + 6) & 0xFF;

    int messageId = ByteBufferUtils.readUnsigned24BitLittleEndian(candidateFrame, frameStartIndex + 7);

    int minimumPayloadLength = dialectRegistry.minimumPayloadLength(MavlinkVersion.V2, messageId);
    if (payloadLength < minimumPayloadLength) {
      return Optional.empty();
    }

    boolean signed = ((incompatibilityFlags & INCOMPAT_FLAG_SIGNED) != 0);
    int payloadStartIndex = frameStartIndex  + HEADER_LENGTH;
    int crcStartIndex = payloadStartIndex + payloadLength;

    int receivedChecksum = ByteBufferUtils.readUnsignedLittleEndianShort(candidateFrame, crcStartIndex);

    int crcExtra = dialectRegistry.crcExtra(MavlinkVersion.V2, messageId);
    int checksum = CrcHelper.computeChecksumFromWritten(candidateFrame, frameStartIndex+1, (HEADER_LENGTH-1) + payloadLength, crcExtra);

    if (checksum != receivedChecksum) {
      return Optional.empty();
    }

    byte[] payload = ByteBufferUtils.copyBytes(candidateFrame, payloadStartIndex, payloadLength);

    byte[] signature = null;
    if (signed) {
      int signatureStartIndex = crcStartIndex + CRC_LENGTH;
      signature = ByteBufferUtils.copyBytes(candidateFrame, signatureStartIndex, SIGNATURE_LENGTH);
    }

    MavlinkFrame frame = new MavlinkFrame();
    frame.setVersion(MavlinkVersion.V2);
    frame.setSequence(sequence);
    frame.setSystemId(systemId);
    frame.setComponentId(componentId);
    frame.setMessageId(messageId);
    frame.setPayloadLength(payloadLength);
    frame.setPayload(payload);
    frame.setChecksum(receivedChecksum);
    frame.setSigned(signed);
    frame.setIncompatibilityFlags(incompatibilityFlags);
    frame.setCompatibilityFlags(compatibilityFlags);
    frame.setSignature(signature);

    return Optional.of(frame);
  }

  private static void updateCrcFromCandidate(X25Crc crc, ByteBuffer candidateFrame, int index, int length) {
    int endIndex = index + length;
    for (int currentIndex = index; currentIndex < endIndex; currentIndex++) {
      crc.update(candidateFrame.get(currentIndex));
    }
  }
}
