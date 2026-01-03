/*
 *
 *     Copyright [ 2020 - 2026 ] [Matthew Buckton]
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.message.MavlinkFrame;
import io.mapsmessaging.mavlink.message.MavlinkVersion;
import io.mapsmessaging.mavlink.message.X25Crc;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class MavlinkV1FrameHandler implements MavlinkFrameHandler {

  private static final int STX = 0xFE;

  private static final int HEADER_LENGTH = 6; // LEN, SEQ, SYSID, COMPID, MSGID
  private static final int CRC_LENGTH = 2;

  private final MavlinkDialectRegistry dialectRegistry;

  public MavlinkV1FrameHandler(MavlinkDialectRegistry dialectRegistry) {
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
    return 1 + HEADER_LENGTH + payloadLength + CRC_LENGTH;
  }

  @Override
  public Optional<MavlinkFrame> tryDecode(ByteBuffer candidateFrame) {
    int frameStartIndex = candidateFrame.position();

    int stx = candidateFrame.get(frameStartIndex) & 0xFF;
    if (stx != STX) {
      return Optional.empty();
    }

    int payloadLength = candidateFrame.get(frameStartIndex + 1) & 0xFF;
    int sequence = candidateFrame.get(frameStartIndex + 2) & 0xFF;
    int systemId = candidateFrame.get(frameStartIndex + 3) & 0xFF;
    int componentId = candidateFrame.get(frameStartIndex + 4) & 0xFF;
    int messageId = candidateFrame.get(frameStartIndex + 5) & 0xFF;

    int minimumPayloadLength = dialectRegistry.minimumPayloadLength(MavlinkVersion.V1, messageId);
    if (payloadLength < minimumPayloadLength) {
      return Optional.empty();
    }

    int payloadStartIndex = frameStartIndex + 1 + HEADER_LENGTH;
    int crcStartIndex = payloadStartIndex + payloadLength;

    int receivedChecksum = ByteBufferUtils.readUnsignedLittleEndianShort(candidateFrame, crcStartIndex);

    int crcExtra = dialectRegistry.crcExtra(MavlinkVersion.V1, messageId);
    int computedChecksum = CrcHelper.computeChecksumFromWritten(candidateFrame, frameStartIndex + 1, (HEADER_LENGTH-1) + payloadLength, crcExtra);
    if (computedChecksum != receivedChecksum) {
      return Optional.empty();
    }

    byte[] payload = ByteBufferUtils.copyBytes(candidateFrame, payloadStartIndex, payloadLength);

    MavlinkFrame frame = new MavlinkFrame();
    frame.setVersion(MavlinkVersion.V1);
    frame.setSequence(sequence);
    frame.setSystemId(systemId);
    frame.setComponentId(componentId);
    frame.setMessageId(messageId);
    frame.setPayloadLength(payloadLength);
    frame.setPayload(payload);
    frame.setChecksum(receivedChecksum);
    frame.setSigned(false);
    frame.setIncompatibilityFlags((byte) 0);
    frame.setCompatibilityFlags((byte) 0);
    frame.setSignature(null);

    return Optional.of(frame);
  }

  private static void updateCrcFromCandidate(X25Crc crc, ByteBuffer candidateFrame, int index, int length) {
    int endIndex = index + length;
    for (int currentIndex = index; currentIndex < endIndex; currentIndex++) {
      crc.update(candidateFrame.get(currentIndex));
    }
  }
}
