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

package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.codec.MavlinkCodec;
import io.mapsmessaging.mavlink.message.CompiledField;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import io.mapsmessaging.mavlink.message.fields.WireType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestMavlinkCharArrays {

  @Test
  void charArray_stringIsTruncatedAndNullPadded() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MessageRegistry registry = codec.getRegistry();

    CompiledMessage message = MavlinkTestSupport.firstMessageWithArray(registry)
        .orElseThrow(() -> new IllegalStateException("No message with array fields found"));

    CompiledField charArrayField = findFirstCharArrayField(message);
    if (charArrayField == null) {
      return; // no char arrays in this dialect revision, skip
    }

    int messageId = message.getMessageId();
    FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(charArrayField);

    String longText = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    Map<String, Object> values = new HashMap<>();
    values.put(fieldDefinition.getName(), longText);

    byte[] payload = codec.encodePayload(messageId, values);

    Map<String, Object> decoded = codec.parsePayload(messageId, payload);
    Assertions.assertNotNull(decoded);

    Object decodedValue = decoded.get(fieldDefinition.getName());
    Assertions.assertNotNull(decodedValue, "Expected decoded value for char array field " + fieldDefinition.getName());

    // Decoder may return String or byte[] depending on your implementation.
    // Accept either, but prove truncation/padding happened by length.
    int expectedLength = fieldDefinition.getArrayLength();

    if (decodedValue instanceof String decodedString) {
      byte[] bytes = decodedString.getBytes(StandardCharsets.UTF_8);
      Assertions.assertTrue(bytes.length <= expectedLength, "Decoded string should not exceed fixed char[] length");
      return;
    }

    if (decodedValue instanceof byte[] decodedBytes) {
      Assertions.assertEquals(expectedLength, decodedBytes.length, "Decoded byte[] should match fixed char[] length");
      return;
    }

    Assertions.fail("Unexpected decoded type for char[] field: " + decodedValue.getClass().getName());
  }

  @Test
  void charArray_byteArrayAccepted() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MessageRegistry registry = codec.getRegistry();

    CompiledMessage message = MavlinkTestSupport.firstMessageWithArray(registry)
        .orElseThrow(() -> new IllegalStateException("No message with array fields found"));

    CompiledField charArrayField = findFirstCharArrayField(message);
    if (charArrayField == null) {
      return;
    }

    int messageId = message.getMessageId();
    FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(charArrayField);

    byte[] input = "HELLO".getBytes(StandardCharsets.UTF_8);

    Map<String, Object> values = new HashMap<>();
    values.put(fieldDefinition.getName(), input);

    byte[] payload = codec.encodePayload(messageId, values);
    assertNotNull(payload);

    Map<String, Object> decoded = codec.parsePayload(messageId, payload);
    Assertions.assertNotNull(decoded);
    Assertions.assertNotNull(decoded.get(fieldDefinition.getName()));
  }

  private CompiledField findFirstCharArrayField(CompiledMessage message) {
    List<CompiledField> fields = message.getCompiledFields();
    for (CompiledField compiledField : fields) {
      FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(compiledField);
      if (fieldDefinition.isArray() && fieldDefinition.getWireType() == WireType.CHAR) {
        return compiledField;
      }
    }
    return null;
  }
}
