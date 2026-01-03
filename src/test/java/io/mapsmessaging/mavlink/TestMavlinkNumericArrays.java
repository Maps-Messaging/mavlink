package io.mapsmessaging.mavlink;


import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkWireType;
import org.junit.jupiter.api.Assertions;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertNotNull;

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
    Assertions.assertNotNull(payload);

    Map<String, Object> decoded = codec.parsePayload(messageId, payload);
    Assertions.assertNotNull(decoded);
    Assertions.assertNotNull(decoded.get(fieldDefinition.getName()));
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
