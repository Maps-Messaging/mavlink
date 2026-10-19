package io.mapsmessaging.mavlink.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.mapsmessaging.mavlink.message.CompiledField;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.Frame;
import io.mapsmessaging.mavlink.message.MessageDefinition;
import io.mapsmessaging.mavlink.message.Version;
import io.mapsmessaging.mavlink.message.X25Crc;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacyCodecCoverageTest {

  @Test
  void extractsScalarAndArrayValuesWithoutInterpretingNestedObjects() {
    CompiledMessage message = compiledMessage(7, "number", "text", "enabled", "samples", "absent", "ignored");
    JsonObject json = new JsonObject();
    json.addProperty("number", 42);
    json.addProperty("text", "ready");
    json.addProperty("enabled", true);
    JsonArray samples = new JsonArray();
    samples.add(3);
    samples.add(false);
    samples.add("four");
    samples.add(new JsonObject());
    samples.add((String) null);
    json.add("samples", samples);
    json.add("ignored", new JsonObject());

    Map<String, Object> values = new JsonValuesExtractor().extractValues(json, message);

    assertEquals(42, ((Number) values.get("number")).intValue());
    assertEquals("ready", values.get("text"));
    assertEquals(1, values.get("enabled"));
    List<?> extractedSamples = (List<?>) values.get("samples");
    assertEquals(3, extractedSamples.size());
    assertEquals(3, ((Number) extractedSamples.get(0)).intValue());
    assertEquals(0, extractedSamples.get(1));
    assertEquals("four", extractedSamples.get(2));
    assertFalse(values.containsKey("absent"));
    assertFalse(values.containsKey("ignored"));
  }

  @Test
  void encodesV2HeaderPayloadAndCrcAndParsesViaFrameFactory() {
    CompiledMessage message = compiledMessage(0x12345);
    byte[] payload = {1, 2, 3};

    byte[] wire = new FrameEncoder().encodeV2Frame(257, 258, 259, message.getMessageId(), payload, message);

    assertEquals(15, wire.length);
    assertEquals(0xfd, wire[0] & 0xff);
    assertEquals(3, wire[1] & 0xff);
    assertEquals(1, wire[4] & 0xff);
    assertEquals(2, wire[5] & 0xff);
    assertEquals(3, wire[6] & 0xff);
    assertEquals(0x45, wire[7] & 0xff);
    assertEquals(0x23, wire[8] & 0xff);
    assertEquals(0x01, wire[9] & 0xff);

    X25Crc crc = new X25Crc();
    crc.update(wire, 1, wire.length - 3);
    crc.update(50);
    assertEquals(crc.getCrc(), (wire[13] & 0xff) | ((wire[14] & 0xff) << 8));

    Frame decoded = new FrameFactory().parse(wire);
    assertEquals(Version.V2, decoded.getVersion());
    assertEquals(message.getMessageId(), decoded.getMessageId());
    assertArrayEquals(payload, decoded.getPayload());
  }

  private static CompiledMessage compiledMessage(int id, String... names) {
    MessageDefinition definition = new MessageDefinition();
    definition.setExtraCrc(50);
    CompiledMessage compiled = new CompiledMessage();
    compiled.setMessageId(id);
    compiled.setMessageDefinition(definition);
    compiled.setCompiledFields(java.util.Arrays.stream(names).map(name -> {
      FieldDefinition field = new FieldDefinition();
      field.setName(name);
      CompiledField compiledField = new CompiledField();
      compiledField.setFieldDefinition(field);
      return compiledField;
    }).toList());
    return compiled;
  }
}
