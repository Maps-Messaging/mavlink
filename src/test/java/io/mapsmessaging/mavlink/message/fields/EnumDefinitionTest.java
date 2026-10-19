package io.mapsmessaging.mavlink.message.fields;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnumDefinitionTest {

  @Test
  void resolvesEntriesByNameValueAndBitmask() {
    EnumEntry one = entry(1, "ONE", "first");
    EnumEntry two = entry(2, "TWO", null);
    EnumEntry three = entry(3, "THREE", "");
    EnumDefinition definition = new EnumDefinition();
    definition.setName("FLAGS");
    definition.setBitmask(true);
    definition.setEntries(List.of(one, two, three));

    assertSame(one, definition.getByName("ONE"));
    assertNull(definition.getByName(null));
    assertNull(definition.getByName("UNKNOWN"));
    assertTrue(definition.hasEntry("TWO"));
    assertFalse(definition.hasEntry("MISSING"));
    assertSame(two, definition.getByValue(2));
    assertNull(definition.getByValue(4));
    assertEquals(List.of(one, two, three), definition.getByBitmask(3));
    assertEquals(List.of(), definition.getByBitmask(0));
    definition.setBitmask(false);
    assertEquals(List.of(), definition.getByBitmask(3));
    assertThrows(UnsupportedOperationException.class, () -> definition.getEntries().clear());
    assertTrue(definition.toString().contains("ONE(1) : first"));
    assertTrue(two.toString().contains("TWO(2)"));
    assertTrue(three.toString().contains("THREE(3)"));
  }

  @Test
  void fieldAndCompiledMetadataDescribeProtocolFields() {
    FieldDefinition field = new FieldDefinition();
    field.setIndex(2);
    field.setType("uint8_t");
    field.setName("flags");
    field.setArray(true);
    field.setArrayLength(4);
    field.setUnits("m");
    field.setEnumName("FLAGS");
    field.setDescription("status");
    assertTrue(field.toString().contains("uint8_t[4] flags (units=m) <enum=FLAGS> : status"));
    field.setArray(false);
    field.setUnits("");
    field.setEnumName(null);
    field.setDescription("");
    assertFalse(field.toString().contains("(units="));
    assertFalse(field.toString().contains("<enum="));
  }

  private static EnumEntry entry(long value, String name, String description) {
    EnumEntry entry = new EnumEntry();
    entry.setValue(value);
    entry.setName(name);
    entry.setDescription(description);
    return entry;
  }
}
