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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static io.mapsmessaging.mavlink.framing.V2FrameHandler.CRC_LENGTH;
import static io.mapsmessaging.mavlink.framing.V2FrameHandler.SIGNATURE_LENGTH;

public final class V2SignatureGenerator {

  public static byte[] buildSignature(ByteBuffer candidateFrame,
                                      int frameStartIndex,
                                      int crcStartIndex,
                                      int linkId,
                                      long timestamp,
                                      byte[] signingKey) {

    if (signingKey == null || signingKey.length != 32) {
      throw new IllegalArgumentException("signingKey must be 32 bytes");
    }
    if (linkId < 0 || linkId > 0xFF) {
      throw new IllegalArgumentException("linkId must be an unsigned byte");
    }
    if (timestamp < 0 || timestamp > 0xFFFFFFFFFFFFL) {
      throw new IllegalArgumentException("timestamp must be an unsigned 48-bit value");
    }

    int packetEndExclusive = crcStartIndex + CRC_LENGTH;
    int packetLength = packetEndExclusive - frameStartIndex;

    if (packetLength <= 0) {
      throw new IllegalArgumentException("Invalid frameStartIndex or crcStartIndex");
    }

    byte[] packetBytes = new byte[packetLength];
    ByteBuffer readOnly = candidateFrame.duplicate();
    readOnly.position(frameStartIndex);
    readOnly.get(packetBytes);

    byte[] signatureBlock = new byte[SIGNATURE_LENGTH];
    signatureBlock[0] = (byte) linkId;
    writeUnsigned48BitLittleEndian(signatureBlock, 1, timestamp);

    byte[] digest = sha256(packetBytes, signatureBlock, signingKey);
    byte[] signatureBytes = Arrays.copyOf(digest, 6);

    System.arraycopy(signatureBytes, 0, signatureBlock, 7, 6);
    return signatureBlock;
  }

  private static void writeUnsigned48BitLittleEndian(byte[] target, int offset, long value) {
    long masked = value & 0xFFFFFFFFFFFFL;

    target[offset]     = (byte) (masked);
    target[offset + 1] = (byte) (masked >>> 8);
    target[offset + 2] = (byte) (masked >>> 16);
    target[offset + 3] = (byte) (masked >>> 24);
    target[offset + 4] = (byte) (masked >>> 32);
    target[offset + 5] = (byte) (masked >>> 40);
  }

  private static byte[] sha256(byte[] packetBytes, byte[] signatureBlock, byte[] signingKey) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }

    digest.update(signingKey);
    digest.update(packetBytes);
    digest.update(signatureBlock, 0, 7);
    return digest.digest();
  }

  private V2SignatureGenerator() {
  }
}
