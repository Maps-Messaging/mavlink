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

package io.mapsmessaging.mavlink.codec;

import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.X25Crc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MavlinkFrameEncoder {

  private static final byte MAVLINK2_STX = (byte) 0xFD;
  private static final int MAVLINK2_HEADER_LENGTH = 10;
  private static final int MAVLINK2_CHECKSUM_LENGTH = 2;

  public byte[] encodeV2Frame(
      int sequence,
      int systemId,
      int componentId,
      int messageId,
      byte[] payload,
      MavlinkCompiledMessage compiledMessage
  ) {
    int payloadLength = payload.length;
    int frameLength = MAVLINK2_HEADER_LENGTH + payloadLength + MAVLINK2_CHECKSUM_LENGTH;

    ByteBuffer buffer = ByteBuffer.allocate(frameLength);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    writeHeader(buffer, payloadLength, sequence, systemId, componentId, messageId);
    buffer.put(payload);

    byte[] bytesBeforeCrc = snapshot(buffer);
    short crcValue = computeCrc(bytesBeforeCrc, compiledMessage);

    buffer.put((byte) (crcValue & 0xFF));
    buffer.put((byte) ((crcValue >> 8) & 0xFF));

    return snapshot(buffer);
  }

  private void writeHeader(
      ByteBuffer buffer,
      int payloadLength,
      int sequence,
      int systemId,
      int componentId,
      int messageId
  ) {
    buffer.put(MAVLINK2_STX);
    buffer.put((byte) payloadLength);
    buffer.put((byte) 0x00);
    buffer.put((byte) 0x00);
    buffer.put((byte) (sequence & 0xFF));
    buffer.put((byte) (systemId & 0xFF));
    buffer.put((byte) (componentId & 0xFF));
    buffer.put((byte) (messageId & 0xFF));
    buffer.put((byte) ((messageId >> 8) & 0xFF));
    buffer.put((byte) ((messageId >> 16) & 0xFF));
  }

  private short computeCrc(byte[] frameBytesWithoutCrc, MavlinkCompiledMessage compiledMessage) {
    int start = 1;
    int lengthForCrc = frameBytesWithoutCrc.length - 1;

    X25Crc crc = new X25Crc();
    crc.update(frameBytesWithoutCrc, start, lengthForCrc);
    crc.update(compiledMessage.getMessageDefinition().getExtraCrc() & 0xFF);

    return crc.getCrcAsShort();
  }

  private byte[] snapshot(ByteBuffer buffer) {
    int length = buffer.position();
    byte[] bytes = new byte[length];
    System.arraycopy(buffer.array(), 0, bytes, 0, length);
    return bytes;
  }
}
