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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

public final class V2FrameHandler implements FrameHandler {

  private static final int STX = 0xFD;

  public static final int INCOMPAT_FLAG_SIGNED = 0x01;

  private static final int HEADER_LENGTH = 10; // LEN, INC, COMP, SEQ, SYSID, COMPID, MSGID(3)

  static final int CRC_LENGTH = 2;
  static final int SIGNATURE_LENGTH = 13;

  private final DialectRegistry dialectRegistry;
  private final SigningKeyProvider signingKeyProvider;

  public V2FrameHandler(DialectRegistry dialectRegistry,
                        SigningKeyProvider signingKeyProvider) {
    this.dialectRegistry = dialectRegistry;
    this.signingKeyProvider = signingKeyProvider;
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
  public Optional<Frame> tryDecode(ByteBuffer candidateFrame) {
    FrameFailureReason validated = FrameFailureReason.OK;

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

    int minimumPayloadLength = dialectRegistry.minimumPayloadLength(Version.V2, messageId);
    if (payloadLength < minimumPayloadLength) {
      return Optional.empty();
    }

    boolean signed = ((incompatibilityFlags & INCOMPAT_FLAG_SIGNED) != 0);
    int payloadStartIndex = frameStartIndex  + HEADER_LENGTH;
    int crcStartIndex = payloadStartIndex + payloadLength;

    int receivedChecksum = ByteBufferUtils.readUnsignedLittleEndianShort(candidateFrame, crcStartIndex);

    int crcExtra = dialectRegistry.crcExtra(Version.V2, messageId);
    int checksum = CrcHelper.computeChecksumFromWritten(candidateFrame, frameStartIndex+1, (HEADER_LENGTH-1) + payloadLength, crcExtra);

    byte[] payload = new byte[0];
    byte[] signature = null;
    if (checksum != receivedChecksum) {
      validated = FrameFailureReason.CRC_FAILED;
    }
    else {
      payload = ByteBufferUtils.copyBytes(candidateFrame, payloadStartIndex, payloadLength);
      if (signed) {
        int signatureStartIndex = crcStartIndex + CRC_LENGTH;
        signature = ByteBufferUtils.copyBytes(candidateFrame, signatureStartIndex, SIGNATURE_LENGTH);
        if (signingKeyProvider.canValidate() &&
            !validateSignature(candidateFrame, frameStartIndex, crcStartIndex, systemId, componentId, signature)) {
          validated = FrameFailureReason.SIGNATURE_FAILED;
        }
      }
      else{
        validated = FrameFailureReason.UNSIGNED;
      }
    }
    Frame frame = new Frame();
    frame.setVersion(Version.V2);
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
    frame.setValidated(validated);
    return Optional.of(frame);
  }

  private boolean validateSignature(ByteBuffer candidateFrame,
                                    int frameStartIndex,
                                    int crcStartIndex,
                                    int systemId,
                                    int componentId,
                                    byte[] receivedSignatureBlock) {

    if (receivedSignatureBlock == null || receivedSignatureBlock.length != SIGNATURE_LENGTH) {
      return false;
    }

    int linkId = receivedSignatureBlock[0] & 0xFF;
    long timestamp = readUnsigned48BitLittleEndian(receivedSignatureBlock, 1);

    byte[] signingKey = signingKeyProvider.getSigningKey(systemId, componentId, linkId);
    if (signingKey == null || signingKey.length == 0) {
      return false;
    }

    byte[] expectedSignatureBlock = V2SignatureGenerator.buildSignature(
        candidateFrame,
        frameStartIndex,
        crcStartIndex,
        linkId,
        timestamp,
        signingKey
    );

    return Arrays.equals(expectedSignatureBlock, receivedSignatureBlock);
  }

  private static long readUnsigned48BitLittleEndian(byte[] source, int offset) {
    long value = 0;
    value |= (source[offset] & 0xFFL);
    value |= ((source[offset + 1] & 0xFFL) << 8);
    value |= ((source[offset + 2] & 0xFFL) << 16);
    value |= ((source[offset + 3] & 0xFFL) << 24);
    value |= ((source[offset + 4] & 0xFFL) << 32);
    value |= ((source[offset + 5] & 0xFFL) << 40);
    return value;
  }
}
