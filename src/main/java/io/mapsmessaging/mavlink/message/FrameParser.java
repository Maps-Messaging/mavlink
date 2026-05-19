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

package io.mapsmessaging.mavlink.message;

import java.util.Arrays;

public class FrameParser {

  private static final int MAVLINK_V1_STX = 0xFE;
  private static final int MAVLINK_V2_STX = 0xFD;

  private static final int MAVLINK_V1_MIN_LENGTH = 1 + 1 + 1 + 1 + 1 + 1 + 2;
  private static final int MAVLINK_V2_MIN_LENGTH = 1 + 1 + 1 + 1 + 1 + 1 + 1 + 3 + 2;

  private static final int MAVLINK_V2_SIGNATURE_LENGTH = 13;
  private static final int MAVLINK_V2_SIGNING_FLAG = 0x01;

  public Frame parse(byte[] frameBytes) {
    if (frameBytes == null || frameBytes.length == 0) {
      throw new IllegalArgumentException("Empty MAVLink frame");
    }

    int startOfText = frameBytes[0] & 0xFF;
    if (startOfText == MAVLINK_V1_STX) {
      return parseV1(frameBytes);
    }
    if (startOfText == MAVLINK_V2_STX) {
      return parseV2(frameBytes);
    }

    throw new IllegalArgumentException("Unsupported MAVLink STX byte: 0x" + Integer.toHexString(startOfText));
  }

  private Frame parseV1(byte[] frameBytes) {
    if (frameBytes.length < MAVLINK_V1_MIN_LENGTH) {
      throw new IllegalArgumentException("MAVLink v1 frame too short: " + frameBytes.length);
    }

    int payloadLength = frameBytes[1] & 0xFF;
    int expectedLength = 1 + 1 + 1 + 1 + 1 + 1 + payloadLength + 2;
    if (frameBytes.length < expectedLength) {
      throw new IllegalArgumentException(
          "MAVLink v1 frame incomplete: expected=" + expectedLength + " actual=" + frameBytes.length
      );
    }

    Frame frame = new Frame();
    frame.setVersion(Version.V1);

    int sequence = frameBytes[2] & 0xFF;
    int systemId = frameBytes[3] & 0xFF;
    int componentId = frameBytes[4] & 0xFF;
    int messageId = frameBytes[5] & 0xFF;

    frame.setSequence(sequence);
    frame.setSystemId(systemId);
    frame.setComponentId(componentId);
    frame.setMessageId(messageId);
    frame.setPayloadLength(payloadLength);

    int payloadStart = 6;
    int payloadEnd = payloadStart + payloadLength;

    byte[] payload = Arrays.copyOfRange(frameBytes, payloadStart, payloadEnd);
    frame.setPayload(payload);

    int checksumLow = frameBytes[payloadEnd] & 0xFF;
    int checksumHigh = frameBytes[payloadEnd + 1] & 0xFF;
    int checksum = checksumLow | (checksumHigh << 8);
    frame.setChecksum(checksum);

    return frame;
  }

  private Frame parseV2(byte[] frameBytes) {
    if (frameBytes.length < MAVLINK_V2_MIN_LENGTH) {
      throw new IllegalArgumentException("MAVLink v2 frame too short: " + frameBytes.length);
    }

    int payloadLength = frameBytes[1] & 0xFF;
    byte incompatibilityFlags = frameBytes[2];
    byte compatibilityFlags = frameBytes[3];

    int sequence = frameBytes[4] & 0xFF;
    int systemId = frameBytes[5] & 0xFF;
    int componentId = frameBytes[6] & 0xFF;

    int messageIdByte0 = frameBytes[7] & 0xFF;
    int messageIdByte1 = frameBytes[8] & 0xFF;
    int messageIdByte2 = frameBytes[9] & 0xFF;
    int messageId = messageIdByte0 | (messageIdByte1 << 8) | (messageIdByte2 << 16);

    int headerLength = 10;
    int baseLength = headerLength + payloadLength + 2;

    if (frameBytes.length < baseLength) {
      throw new IllegalArgumentException(
          "MAVLink v2 frame incomplete: expected at least=" + baseLength + " actual=" + frameBytes.length
      );
    }

    boolean signed = (incompatibilityFlags & MAVLINK_V2_SIGNING_FLAG) != 0;
    int totalExpectedLength = baseLength;
    if (signed) {
      totalExpectedLength = totalExpectedLength + MAVLINK_V2_SIGNATURE_LENGTH;
    }

    if (frameBytes.length < totalExpectedLength) {
      throw new IllegalArgumentException(
          "MAVLink v2 signed frame incomplete: expected=" + totalExpectedLength + " actual=" + frameBytes.length
      );
    }

    Frame frame = new Frame();
    frame.setVersion(Version.V2);
    frame.setSequence(sequence);
    frame.setSystemId(systemId);
    frame.setComponentId(componentId);
    frame.setMessageId(messageId);
    frame.setPayloadLength(payloadLength);
    frame.setIncompatibilityFlags(incompatibilityFlags);
    frame.setCompatibilityFlags(compatibilityFlags);
    frame.setSigned(signed);

    int payloadStart = headerLength;
    int payloadEnd = payloadStart + payloadLength;

    byte[] payload = Arrays.copyOfRange(frameBytes, payloadStart, payloadEnd);
    frame.setPayload(payload);

    int checksumLow = frameBytes[payloadEnd] & 0xFF;
    int checksumHigh = frameBytes[payloadEnd + 1] & 0xFF;
    int checksum = checksumLow | (checksumHigh << 8);
    frame.setChecksum(checksum);

    if (signed) {
      int signatureStart = payloadEnd + 2;
      int signatureEnd = signatureStart + MAVLINK_V2_SIGNATURE_LENGTH;
      byte[] signature = Arrays.copyOfRange(frameBytes, signatureStart, signatureEnd);
      frame.setSignature(signature);
    }

    return frame;
  }
}