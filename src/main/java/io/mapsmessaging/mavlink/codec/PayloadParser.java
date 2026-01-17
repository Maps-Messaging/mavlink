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


import io.mapsmessaging.mavlink.message.CompiledField;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.fields.AbstractMavlinkFieldCodec;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import io.mapsmessaging.mavlink.message.fields.WireType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PayloadParser {

  private final MessageRegistry messageRegistry;

  public PayloadParser(MessageRegistry messageRegistry) {
    this.messageRegistry = messageRegistry;
  }

  public Map<String, Object> parsePayload(int messageId, byte[] payload) throws IOException {
    CompiledMessage compiledMessage = messageRegistry.getCompiledMessagesById().get(messageId);
    if (compiledMessage == null) {
      throw new IllegalArgumentException("Unknown MAVLink message id: " + messageId);
    }

    Map<String, Object> result = new HashMap<>();

    ByteBuffer buffer = ByteBuffer.wrap(payload);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    boolean truncated = false;

    for (CompiledField compiledField : compiledMessage.getCompiledFields()) {
      FieldDefinition fieldDefinition = compiledField.getFieldDefinition();
      AbstractMavlinkFieldCodec fieldCodec = compiledField.getFieldCodec();
      String fieldName = fieldDefinition.getName();
      int fieldSize = compiledField.getSizeInBytes();

      if (!fieldDefinition.isExtension()) {
        if (truncated || buffer.remaining() < fieldSize) {
          truncated = true;
          result.put(fieldName, zeroValue(fieldDefinition));
          continue;
        }
      } else {
        // Extension fields: absent if not enough bytes (or if we've already truncated)
        if (truncated || buffer.remaining() < fieldSize) {
          result.put(fieldName, null);
          continue;
        }
      }

      // Normal decode path (enough bytes available)
      if (!fieldDefinition.isArray()) {
        result.put(fieldName, fieldCodec.decode(buffer));
        continue;
      }

      int len = fieldDefinition.getArrayLength();
      if (fieldDefinition.getWireType() == WireType.CHAR) {
        // read fixed-size char[N] bytes; codec may or may not do this correctly
        byte[] raw = new byte[len];
        buffer.get(raw);
        int end = 0;
        while (end < raw.length && raw[end] != 0) {
          end++;
        }
        result.put(fieldName, new String(raw, 0, end, StandardCharsets.UTF_8));
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

  private Object zeroValue(FieldDefinition fieldDefinition) {
    if (!fieldDefinition.isArray()) {
      // Scalars: 0 / 0.0 / false, etc.
      return switch (fieldDefinition.getWireType()) {
        case FLOAT, DOUBLE -> 0.0;
        case CHAR -> ""; // should never happen for non-array in MAVLink, but safe
        default -> 0L;   // ints: you can use Long universally in your map
      };
    }

    int len = fieldDefinition.getArrayLength();

    if (fieldDefinition.getWireType() == WireType.CHAR) {
      return "";
    }

    // Numeric arrays: list of zeros
    List<Object> zeros = new ArrayList<>(len);
    Object z = switch (fieldDefinition.getWireType()) {
      case FLOAT, DOUBLE -> 0.0;
      default -> 0L;
    };
    for (int i = 0; i < len; i++) {
      zeros.add(z);
    }
    return zeros;
  }

}
