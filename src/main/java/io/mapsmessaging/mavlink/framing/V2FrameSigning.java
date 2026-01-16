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

import java.nio.ByteBuffer;
import java.util.Arrays;

import static io.mapsmessaging.mavlink.framing.V2FrameHandler.CRC_LENGTH;
import static io.mapsmessaging.mavlink.framing.V2FrameHandler.SIGNATURE_LENGTH;

public final class V2FrameSigning {

  private V2FrameSigning() {
  }

  public static ByteBuffer appendSignature(ByteBuffer unsignedFrame,
                                           int linkId,
                                           long timestamp,
                                           byte[] signingKey) {
    if (unsignedFrame == null) {
      throw new IllegalArgumentException("unsignedFrame must not be null");
    }
    if (signingKey == null || signingKey.length != 32) {
      throw new IllegalArgumentException("signingKey must be 32 bytes");
    }

    ByteBuffer readOnly = unsignedFrame.duplicate();

    int frameStartIndex = readOnly.position();
    int frameLimit = readOnly.limit();

    if (frameLimit - frameStartIndex < 1 + 10 + CRC_LENGTH) {
      throw new IllegalArgumentException("Frame too short");
    }

    int crcStartIndex = frameLimit - CRC_LENGTH;

    byte[] signatureBlock = V2SignatureGenerator.buildSignature(
        readOnly,
        frameStartIndex,
        crcStartIndex,
        linkId,
        timestamp,
        signingKey
    );

    ByteBuffer signedFrame = ByteBuffer.allocate((frameLimit - frameStartIndex) + SIGNATURE_LENGTH);
    signedFrame.put(readOnly.duplicate());
    signedFrame.put(signatureBlock);
    signedFrame.flip();
    return signedFrame;
  }

  public static boolean validateSignature(ByteBuffer signedFrame,
                                          int frameStartIndex,
                                          int crcStartIndex,
                                          int systemId,
                                          int componentId,
                                          SigningKeyProvider signingKeyProvider) {
    int signatureStartIndex = crcStartIndex + CRC_LENGTH;

    byte[] receivedSignatureBlock = ByteBufferUtils.copyBytes(signedFrame, signatureStartIndex, SIGNATURE_LENGTH);

    int linkId = receivedSignatureBlock[0] & 0xFF;
    long timestamp = readUnsigned48BitLittleEndian(receivedSignatureBlock, 1);

    byte[] signingKey = signingKeyProvider.getSigningKey(systemId, componentId, linkId);
    if (signingKey == null || signingKey.length != 32) {
      return false;
    }

    byte[] expectedSignatureBlock = V2SignatureGenerator.buildSignature(
        signedFrame,
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
