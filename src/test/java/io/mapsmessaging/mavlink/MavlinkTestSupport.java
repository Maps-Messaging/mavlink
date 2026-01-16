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

import io.mapsmessaging.mavlink.message.CompiledField;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MavlinkTestSupport {

  private MavlinkTestSupport() {
  }

  public static MavlinkCodec codec() throws Exception {
    MavlinkMessageFormatLoader loader = MavlinkMessageFormatLoader.getInstance();
    return loader.getDialectOrThrow("common");
  }

  public static MessageRegistry registry(MavlinkCodec codec) {
    return codec.getRegistry();
  }

  public static FieldDefinition fieldDefinition(CompiledField compiledField) {
    try {
      Field field = CompiledField.class.getDeclaredField("fieldDefinition");
      field.setAccessible(true);
      return (FieldDefinition) field.get(compiledField);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot access MavlinkCompiledField.fieldDefinition", exception);
    }
  }

  public static int offset(CompiledField compiledField) {
    try {
      Field field = CompiledField.class.getDeclaredField("offsetInPayload");
      field.setAccessible(true);
      return (int) field.get(compiledField);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot access MavlinkCompiledField.offsetInPayload", exception);
    }
  }

  public static int size(CompiledField compiledField) {
    try {
      Field field = CompiledField.class.getDeclaredField("sizeInBytes");
      field.setAccessible(true);
      return (int) field.get(compiledField);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot access MavlinkCompiledField.sizeInBytes", exception);
    }
  }

  public static Optional<CompiledMessage> firstMessageWithExtensions(MessageRegistry registry) {
    return registry.getCompiledMessages().stream()
        .filter(message -> message.getCompiledFields().stream().anyMatch(field -> fieldDefinition(field).isExtension()))
        .findFirst();
  }

  public static Optional<CompiledMessage> firstMessageWithArray(MessageRegistry registry) {
    return registry.getCompiledMessages().stream()
        .filter(message -> message.getCompiledFields().stream().anyMatch(field -> fieldDefinition(field).isArray()))
        .findFirst();
  }

  public static Optional<CompiledMessage> firstMessageWithEnum(MessageRegistry registry) {
    return registry.getCompiledMessages().stream()
        .filter(message -> message.getCompiledFields().stream().anyMatch(field -> {
          FieldDefinition definition = fieldDefinition(field);
          String enumName = definition.getEnumName();
          return enumName != null && !enumName.isEmpty();
        }))
        .findFirst();
  }

  public static Map<String, Object> payloadWithScalar(FieldDefinition field, Number value) {
    return Map.of(field.getName(), value);
  }

  public static Map<String, Object> payloadWithArrayFilled(FieldDefinition field, Number value) {
    int arrayLength = field.getArrayLength();
    List<Number> list = java.util.stream.IntStream.range(0, arrayLength)
        .mapToObj(index -> value)
        .toList();
    return Map.of(field.getName(), list);
  }

  public static List<CompiledField> fieldsSortedByOffset(CompiledMessage message) {
    return message.getCompiledFields().stream()
        .sorted(Comparator.comparingInt(MavlinkTestSupport::offset))
        .toList();
  }

  public static String requireNonEmpty(String value, String message) {
    if (value == null || value.isEmpty()) {
      throw new IllegalStateException(message);
    }
    return value;
  }
}
