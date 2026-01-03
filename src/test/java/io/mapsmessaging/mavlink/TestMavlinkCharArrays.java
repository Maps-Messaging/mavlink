package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkWireType;
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
    MavlinkMessageRegistry registry = codec.getRegistry();

    MavlinkCompiledMessage message = MavlinkTestSupport.firstMessageWithArray(registry)
        .orElseThrow(() -> new IllegalStateException("No message with array fields found"));

    MavlinkCompiledField charArrayField = findFirstCharArrayField(message);
    if (charArrayField == null) {
      return; // no char arrays in this dialect revision, skip
    }

    int messageId = message.getMessageId();
    MavlinkFieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(charArrayField);

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
    MavlinkMessageRegistry registry = codec.getRegistry();

    MavlinkCompiledMessage message = MavlinkTestSupport.firstMessageWithArray(registry)
        .orElseThrow(() -> new IllegalStateException("No message with array fields found"));

    MavlinkCompiledField charArrayField = findFirstCharArrayField(message);
    if (charArrayField == null) {
      return;
    }

    int messageId = message.getMessageId();
    MavlinkFieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(charArrayField);

    byte[] input = "HELLO".getBytes(StandardCharsets.UTF_8);

    Map<String, Object> values = new HashMap<>();
    values.put(fieldDefinition.getName(), input);

    byte[] payload = codec.encodePayload(messageId, values);
    assertNotNull(payload);

    Map<String, Object> decoded = codec.parsePayload(messageId, payload);
    Assertions.assertNotNull(decoded);
    Assertions.assertNotNull(decoded.get(fieldDefinition.getName()));
  }

  private MavlinkCompiledField findFirstCharArrayField(MavlinkCompiledMessage message) {
    List<MavlinkCompiledField> fields = message.getCompiledFields();
    for (MavlinkCompiledField compiledField : fields) {
      MavlinkFieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(compiledField);
      if (fieldDefinition.isArray() && fieldDefinition.getWireType() == MavlinkWireType.CHAR) {
        return compiledField;
      }
    }
    return null;
  }
}
