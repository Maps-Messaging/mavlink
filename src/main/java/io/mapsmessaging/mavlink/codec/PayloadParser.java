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

    // MAVLink 2 omits trailing zero bytes, including bytes within scalar fields.
    // Restore the full wire layout before decoding, without changing the supplied payload.
    int decodedLength = 0;
    for (CompiledField compiledField : compiledMessage.getCompiledFields()) {
      decodedLength += compiledField.getSizeInBytes();
    }

    byte[] paddedPayload = java.util.Arrays.copyOf(payload, Math.max(decodedLength, payload.length));
    ByteBuffer buffer = ByteBuffer.wrap(paddedPayload).order(ByteOrder.LITTLE_ENDIAN);

    for (CompiledField compiledField : compiledMessage.getCompiledFields()) {
      FieldDefinition fieldDefinition = compiledField.getFieldDefinition();
      AbstractMavlinkFieldCodec fieldCodec = compiledField.getFieldCodec();
      String fieldName = fieldDefinition.getName();
      int fieldSize = compiledField.getSizeInBytes();

      if (fieldDefinition.isExtension() && buffer.position() >= payload.length) {
        result.put(fieldName, null);
        buffer.position(buffer.position() + fieldSize);
        continue;
      }

      if (!fieldDefinition.isArray()) {
        result.put(fieldName, fieldCodec.decode(buffer));
        continue;
      }

      int len = fieldDefinition.getArrayLength();
      if (fieldDefinition.getWireType() == WireType.CHAR) {
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

}
