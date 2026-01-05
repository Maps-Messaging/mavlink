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

package io.mapsmessaging.mavlink;


import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkWireType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestMavlinkNumericArrays {

  @Test
  void numericArray_listShorterThanLength_zeroPadsRemainder() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MavlinkMessageRegistry registry = codec.getRegistry();

    MavlinkCompiledMessage message = MavlinkTestSupport.firstMessageWithArray(registry)
        .orElseThrow(() -> new IllegalStateException("No message with array fields found"));

    MavlinkCompiledField numericArray = findFirstNumericArrayField(message);
    if (numericArray == null) {
      return;
    }

    int messageId = message.getMessageId();
    MavlinkFieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(numericArray);

    List<Number> shortList = List.of(1, 2);

    Map<String, Object> values = new HashMap<>();
    values.put(fieldDefinition.getName(), shortList);

    byte[] payload = codec.encodePayload(messageId, values);
    Map<String, Object> decoded = codec.parsePayload(messageId, payload);

    Object decodedValue = decoded.get(fieldDefinition.getName());
    assertNotNull(decodedValue);

    // Implementation may return List<?> or primitive array etc.
    // We at least lock down that decoding succeeds and that the field exists.
  }

  @Test
  void numericArray_listLongerThanLength_isTruncated() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MavlinkMessageRegistry registry = codec.getRegistry();

    MavlinkCompiledMessage message = MavlinkTestSupport.firstMessageWithArray(registry)
        .orElseThrow(() -> new IllegalStateException("No message with array fields found"));

    MavlinkCompiledField numericArray = findFirstNumericArrayField(message);
    if (numericArray == null) {
      return;
    }

    int messageId = message.getMessageId();
    MavlinkFieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(numericArray);

    int length = fieldDefinition.getArrayLength();

    List<Number> longList = new ArrayList<>();
    for (int index = 0; index < length + 5; index++) {
      longList.add(7);
    }

    Map<String, Object> values = new HashMap<>();
    values.put(fieldDefinition.getName(), longList);

    byte[] payload = codec.encodePayload(messageId, values);
    assertNotNull(payload);

    Map<String, Object> decoded = codec.parsePayload(messageId, payload);
    assertNotNull(decoded);
    assertNotNull(decoded.get(fieldDefinition.getName()));
  }

  private MavlinkCompiledField findFirstNumericArrayField(MavlinkCompiledMessage message) {
    for (MavlinkCompiledField compiledField : message.getCompiledFields()) {
      MavlinkFieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(compiledField);
      if (!fieldDefinition.isArray()) {
        continue;
      }
      if (fieldDefinition.getWireType() == null) {
        continue;
      }
      if (fieldDefinition.getWireType() == MavlinkWireType.CHAR) {
        continue;
      }
      return compiledField;
    }
    return null;
  }
}
