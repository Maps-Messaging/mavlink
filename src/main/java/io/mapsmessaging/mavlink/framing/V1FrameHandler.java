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

package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.context.FrameFailureReason;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.Version;
import io.mapsmessaging.mavlink.message.X25Crc;

import java.nio.ByteBuffer;
import java.util.Optional;

public final class V1FrameHandler implements FrameHandler {

  private static final int STX = 0xFE;

  private static final int HEADER_LENGTH = 6; // LEN, SEQ, SYSID, COMPID, MSGID
  private static final int CRC_LENGTH = 2;

  private final DialectRegistry dialectRegistry;

  public V1FrameHandler(DialectRegistry dialectRegistry) {
    this.dialectRegistry = dialectRegistry;
  }

  @Override
  public int minimumBytesRequiredForHeader() {
    return HEADER_LENGTH;
  }

  @Override
  public int peekPayloadLength(ByteBuffer buffer, int frameStartIndex) {
    return buffer.get(frameStartIndex + 1) & 0xFF;
  }

  @Override
  public int computeTotalFrameLength(ByteBuffer buffer, int frameStartIndex, int payloadLength) {
    return HEADER_LENGTH + payloadLength + CRC_LENGTH;
  }

  @Override
  public Optional<Frame> tryDecode(ByteBuffer candidateFrame) {
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

    int minimumPayloadLength = dialectRegistry.minimumPayloadLength(Version.V1, messageId);
    if (payloadLength < minimumPayloadLength) {
      return Optional.empty();
    }

    int payloadStartIndex = frameStartIndex + HEADER_LENGTH;
    int crcStartIndex = payloadStartIndex + payloadLength;

    int receivedChecksum = ByteBufferUtils.readUnsignedLittleEndianShort(candidateFrame, crcStartIndex);

    int crcExtra = dialectRegistry.crcExtra(Version.V1, messageId);
    int computedChecksum = CrcHelper.computeChecksumFromWritten(candidateFrame, frameStartIndex + 1, (HEADER_LENGTH) + payloadLength-1, crcExtra);
    FrameFailureReason v = FrameFailureReason.OK;
    if (computedChecksum != receivedChecksum) {
      v = FrameFailureReason.CRC_FAILED;
    }

    byte[] payload = ByteBufferUtils.copyBytes(candidateFrame, payloadStartIndex, payloadLength);

    Frame frame = new Frame();
    frame.setVersion(Version.V1);
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
    frame.setValidated(v);

    return Optional.of(frame);
  }

  private static void updateCrcFromCandidate(X25Crc crc, ByteBuffer candidateFrame, int index, int length) {
    int endIndex = index + length;
    for (int currentIndex = index; currentIndex < endIndex; currentIndex++) {
      crc.update(candidateFrame.get(currentIndex));
    }
  }
}
