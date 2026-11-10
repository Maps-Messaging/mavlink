/*
 * Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 * Licensed under the Apache License, Version 2.0 with the Commons Clause
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     https://commonsclause.com/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package io.mapsmessaging.mavlink.message;

import io.mapsmessaging.mavlink.message.fields.EnumDefinition;
import io.mapsmessaging.mavlink.message.fields.EnumEntry;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnumResolverUint64Test {

  private static FieldDefinition field(String type) {
    FieldDefinition field = new FieldDefinition();
    field.setName("capabilities");
    field.setType(type);
    field.setEnumName("CAPABILITIES");
    return field;
  }

  private static MessageRegistry registry() {
    EnumEntry entry = new EnumEntry();
    entry.setName("CAP_A");
    entry.setValue(1L);
    EnumDefinition definition = new EnumDefinition();
    definition.setName("CAPABILITIES");
    definition.setBitmask(true);
    definition.setEntries(List.of(entry));
    MessageRegistry registry = mock(MessageRegistry.class);
    when(registry.getEnumsByName()).thenReturn(Map.of("CAPABILITIES", definition));
    return registry;
  }

  @Test
  void fullUnsignedRangePassesEnumResolutionAndExistingCodec() throws Exception {
    MessageRegistry registry = registry();
    FieldDefinition field = field("uint64_t");
    io.mapsmessaging.mavlink.message.fields.UInt64FieldCodec codec =
        new io.mapsmessaging.mavlink.message.fields.UInt64FieldCodec();
    for (String text : List.of("0", "1", "9007199254740993", "9223372036854775807",
        "9223372036854775808", "18446744073709551615")) {
      BigInteger value = new BigInteger(text);
      Object resolved = EnumResolver.resolveEnumValue(registry, field, value);
      assertEquals(value, resolved);
      ByteBuffer binary = ByteBuffer.allocate(8);
      codec.encode(binary, resolved);
      binary.flip();
      assertEquals(value, codec.decode(binary));
    }
  }

  @Test
  void integerLongAndNamedEnumWorkWithoutNarrowing() throws Exception {
    MessageRegistry registry = registry();
    FieldDefinition unsigned = field("uint64_t");
    assertEquals(42, EnumResolver.resolveEnumValue(registry, unsigned, 42));
    assertEquals(4_294_967_297L, EnumResolver.resolveEnumValue(registry, unsigned, 4_294_967_297L));
    assertEquals(1L, EnumResolver.resolveEnumValue(registry, unsigned, "CAP_A"));
    assertEquals(List.of(new BigInteger("18446744073709551615"), 1L),
        EnumResolver.resolveEnumValue(registry, field("uint64_t[2]"),
            List.of(new BigInteger("18446744073709551615"), "CAP_A")));
    assertThrows(java.io.IOException.class, () -> EnumResolver.resolveEnumValue(registry, unsigned, "UNKNOWN"));
  }

  @Test
  void nonUint64EnumRetainsPreviousIntegerHandling() throws Exception {
    MessageRegistry registry = registry();
    FieldDefinition ordinary = field("uint32_t");
    assertEquals(1, EnumResolver.resolveEnumValue(registry, ordinary, 4_294_967_297L));
    assertEquals(1, EnumResolver.resolveEnumValue(registry, ordinary, "CAP_A"));
    assertThrows(IllegalArgumentException.class, () ->
        new io.mapsmessaging.mavlink.message.fields.UInt64FieldCodec().encode(ByteBuffer.allocate(8),
            new BigInteger("18446744073709551616")));
  }
}
