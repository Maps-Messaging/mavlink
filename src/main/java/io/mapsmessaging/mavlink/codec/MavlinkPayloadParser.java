/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2025 ] MapsMessaging B.V.
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

package io.mapsmessaging.mavlink.codec;


import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.fields.AbstractMavlinkFieldCodec;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkWireType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavlinkPayloadParser {

  private final MavlinkMessageRegistry messageRegistry;

  public MavlinkPayloadParser(MavlinkMessageRegistry messageRegistry) {
    this.messageRegistry = messageRegistry;
  }

  public Map<String, Object> parsePayload(int messageId, byte[] payload) {
    MavlinkCompiledMessage compiledMessage =
        messageRegistry.getCompiledMessagesById().get(messageId);
    if (compiledMessage == null) {
      throw new IllegalArgumentException("Unknown MAVLink message id: " + messageId);
    }

    Map<String, Object> result = new HashMap<>();

    ByteBuffer buffer = ByteBuffer.wrap(payload);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    for (MavlinkCompiledField compiledField : compiledMessage.getCompiledFields()) {
      MavlinkFieldDefinition fieldDefinition = compiledField.getFieldDefinition();
      AbstractMavlinkFieldCodec fieldCodec = compiledField.getFieldCodec();
      String fieldName = fieldDefinition.getName();

      int fieldSize = compiledField.getSizeInBytes();

      // Base fields MUST be present
      if (!fieldDefinition.isExtension()) {
        if (buffer.remaining() < fieldSize) {
          throw new IllegalArgumentException(
              "Payload too short for base field '" + fieldName +
                  "' in message " + compiledMessage.getName() +
                  " remaining=" + buffer.remaining() +
                  " required=" + fieldSize
          );
        }
      } else {
        // Extension fields are optional: if not enough bytes left, treat as absent
        if (buffer.remaining() < fieldSize) {
          // you can either omit it or explicitly put null; your choice
          result.put(fieldName, null);
          continue;
        }
      }

      if (!fieldDefinition.isArray()) {
        Object value = fieldCodec.decode(buffer);
        result.put(fieldName, value);
        continue;
      }

      int len = fieldDefinition.getArrayLength();

      if (fieldDefinition.getWireType() == MavlinkWireType.CHAR) {
        // MAVLink strings: fixed-size, null-terminated, null-padded
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
          Object v = fieldCodec.decode(buffer);   // codec returns Byte
          bytes[i] = (byte) v;
        }
        int end = len;
        while (end > 0 && bytes[end - 1] == 0) {
          end--;
        }
        String value = new String(bytes, 0, end, StandardCharsets.UTF_8);
        result.put(fieldName, value);
      } else {
        List<Object> values = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
          values.add(fieldCodec.decode(buffer));
        }
        result.put(fieldName, values);
      }
    }

    return result;
  }
}
