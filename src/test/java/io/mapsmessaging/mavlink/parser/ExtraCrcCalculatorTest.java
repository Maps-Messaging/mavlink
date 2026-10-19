package io.mapsmessaging.mavlink.parser;

import io.mapsmessaging.mavlink.message.MessageDefinition;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtraCrcCalculatorTest {

  @Test
  void heartbeatMatchesKnownMavlinkCrcExtra() {
    MessageDefinition heartbeat = new MessageDefinition();
    heartbeat.setName("HEARTBEAT");
    heartbeat.setFields(List.of(
        field("uint32_t", "custom_mode"),
        field("uint8_t", "type"),
        field("uint8_t", "autopilot"),
        field("uint8_t", "base_mode"),
        field("uint8_t", "system_status"),
        field("uint8_t", "mavlink_version")));

    assertEquals(50, ExtraCrcCalculator.computeExtraCrc(heartbeat));
  }

  @Test
  void extensionsDoNotAffectCrcButArrayLengthDoes() {
    MessageDefinition message = new MessageDefinition();
    message.setName("SAMPLE");
    FieldDefinition array = field("uint8_t", "samples");
    array.setArray(true);
    array.setArrayLength(3);
    message.setFields(List.of(array));
    int original = ExtraCrcCalculator.computeExtraCrc(message);

    FieldDefinition extension = field("float", "future");
    extension.setExtension(true);
    message.setFields(List.of(array, extension));
    assertEquals(original, ExtraCrcCalculator.computeExtraCrc(message));
    array.setArrayLength(4);
    assertNotEquals(original, ExtraCrcCalculator.computeExtraCrc(message));

    message.setName(null);
    array.setName(null);
    assertDoesNotThrow(() -> ExtraCrcCalculator.computeExtraCrc(message));
  }

  private static FieldDefinition field(String type, String name) {
    FieldDefinition field = new FieldDefinition();
    field.setType(type);
    field.setName(name);
    return field;
  }
}
