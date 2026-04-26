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

import io.mapsmessaging.mavlink.message.CompiledField;
import io.mapsmessaging.mavlink.message.CompiledMessage;
import io.mapsmessaging.mavlink.message.MessageRegistry;
import io.mapsmessaging.mavlink.message.fields.EnumDefinition;
import io.mapsmessaging.mavlink.message.fields.EnumEntry;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RandomValueFactory {

  protected RandomValueFactory() {
  }

  public static Map<String, Object> buildValues(
      MessageRegistry registry,
      CompiledMessage message,
      MavlinkRoundTripAllMessagesTest.ExtensionMode extensionMode,
      long baseSeed
  ) throws IOException {

    Map<String, Object> values = new LinkedHashMap<>();
    List<CompiledField> fields = message.getCompiledFields();

    for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
      CompiledField compiledField = fields.get(fieldIndex);
      FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(compiledField);

      if (fieldDefinition.isExtension() && extensionMode == MavlinkRoundTripAllMessagesTest.ExtensionMode.OMIT_ALL) {
        continue;
      }

      if (fieldDefinition.isExtension() && extensionMode == MavlinkRoundTripAllMessagesTest.ExtensionMode.SOME_PRESENT) {
        long seed = perFieldSeed(baseSeed, message.getMessageId(), fieldIndex);
        Random random = new Random(seed);

        if (random.nextInt(100) < 60) {
          continue;
        }
      }

      Object value = generateValueForField(
          registry,
          message.getMessageId(),
          fieldDefinition,
          fieldIndex,
          baseSeed
      );

      values.put(fieldDefinition.getName(), value);
    }

    return values;
  }

  static FieldDefinition fieldByName(CompiledMessage message, String fieldName) {
    for (CompiledField compiledField : message.getCompiledFields()) {
      FieldDefinition fieldDefinition = MavlinkTestSupport.fieldDefinition(compiledField);

      if (fieldName.equals(fieldDefinition.getName())) {
        return fieldDefinition;
      }
    }

    throw new IllegalStateException("Unknown field '" + fieldName + "' in message " + message.getMessageId());
  }

  protected static Object generateValueForField(
      MessageRegistry registry,
      int messageId,
      FieldDefinition fieldDefinition,
      int fieldIndex,
      long baseSeed
  ) throws IOException {

    long seed = perFieldSeed(baseSeed, messageId, fieldIndex);
    Random random = new Random(seed);

    if (fieldDefinition.isArray()) {
      if (isCharType(fieldDefinition)) {
        return randomAsciiString(fieldDefinition.getArrayLength(), messageId, fieldDefinition.getName(), random);
      }

      Object[] values = new Object[fieldDefinition.getArrayLength()];

      for (int arrayIndex = 0; arrayIndex < values.length; arrayIndex++) {
        values[arrayIndex] = generateScalar(registry, fieldDefinition, random);
      }

      return values;
    }

    return generateScalar(registry, fieldDefinition, random);
  }

  protected static Object generateScalar(
      MessageRegistry registry,
      FieldDefinition fieldDefinition,
      Random random
  ) throws IOException {

    String enumName = fieldDefinition.getEnumName();

    if (enumName != null && !enumName.isEmpty()) {
      EnumDefinition enumDefinition = registry.getEnumsByName().get(enumName);

      if (
          enumDefinition != null
              && enumDefinition.getEntries() != null
              && !enumDefinition.getEntries().isEmpty()
      ) {
        long enumValue;

        if (enumDefinition.isBitmask()) {
          enumValue = pickBitmask(enumDefinition, random);
        } else {
          enumValue = pickEnumValue(enumDefinition, random);
        }

        return coerceValueToFieldType(fieldDefinition, enumValue);
      }
    }

    String type = normalizeType(fieldDefinition.getType());

    return switch (type) {
      case "uint8_t" -> Integer.valueOf(random.nextInt(256));
      case "int8_t" -> Integer.valueOf(random.nextInt(256) - 128);

      case "uint16_t" -> Integer.valueOf(random.nextInt(65536));
      case "int16_t" -> Integer.valueOf(random.nextInt(65536) - 32768);

      case "uint32_t" -> Long.valueOf(nextUnsignedIntValue(random));
      case "int32_t" -> Integer.valueOf(random.nextInt());

      case "uint64_t" -> Long.valueOf(nextPositiveLongValue(random));
      case "int64_t" -> Long.valueOf(random.nextLong());

      case "float" -> Float.valueOf(nextFloatBounded(random, -10_000f, 10_000f));
      case "double" -> Double.valueOf(nextDoubleBounded(random, -10_000d, 10_000d));

      case "char" -> Integer.valueOf(random.nextInt(128));

      default -> throw new IOException(
          "Unsupported MAVLink field type: "
              + fieldDefinition.getType()
              + " (normalized="
              + type
              + ")"
      );
    };
  }

  protected static Object coerceValueToFieldType(
      FieldDefinition fieldDefinition,
      long value
  ) throws IOException {

    String type = normalizeType(fieldDefinition.getType());

    return switch (type) {
      case "uint8_t" -> Integer.valueOf((int) (value & 0xFFL));
      case "int8_t" -> Integer.valueOf((byte) value);

      case "uint16_t" -> Integer.valueOf((int) (value & 0xFFFFL));
      case "int16_t" -> Integer.valueOf((short) value);

      case "uint32_t" -> Long.valueOf(value & 0xFFFF_FFFFL);
      case "int32_t" -> Integer.valueOf((int) value);

      case "uint64_t" -> Long.valueOf(value);
      case "int64_t" -> Long.valueOf(value);

      case "char" -> Integer.valueOf((int) (value & 0x7FL));

      default -> throw new IOException(
          "Unsupported MAVLink enum field type: "
              + fieldDefinition.getType()
              + " (normalized="
              + type
              + ")"
      );
    };
  }

  protected static long pickEnumValue(
      EnumDefinition enumDefinition,
      Random random
  ) {
    List<EnumEntry> entries = enumDefinition.getEntries();
    EnumEntry entry = entries.get(random.nextInt(entries.size()));

    return entry.getValue();
  }

  protected static long pickBitmask(
      EnumDefinition enumDefinition,
      Random random
  ) {
    List<EnumEntry> entries = enumDefinition.getEntries();
    int count = Math.min(entries.size(), 1 + random.nextInt(3));
    long mask = 0L;

    for (int index = 0; index < count; index++) {
      EnumEntry entry = entries.get(random.nextInt(entries.size()));
      mask |= entry.getValue();
    }

    return mask;
  }

  protected static boolean isCharType(FieldDefinition fieldDefinition) {
    String type = normalizeType(fieldDefinition.getType());

    return "char".equals(type);
  }

  protected static String randomAsciiString(
      int maxLength,
      int messageId,
      String fieldName,
      Random random
  ) {
    String prefix = "M" + messageId + "_" + fieldName + "_";
    byte[] bytes = new byte[Math.max(1, maxLength)];
    byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);

    int position = 0;

    for (int index = 0; index < prefixBytes.length && position < bytes.length; index++) {
      bytes[position] = prefixBytes[index];
      position++;
    }

    while (position < bytes.length) {
      int character = 32 + random.nextInt(95);
      bytes[position] = (byte) character;
      position++;
    }

    return new String(bytes, StandardCharsets.US_ASCII);
  }

  protected static String normalizeType(String type) {
    if (type == null) {
      return "";
    }

    String trimmedType = type.trim();

    if (trimmedType.endsWith("_t")) {
      return trimmedType;
    }

    if (trimmedType.contains("uint8_t")) {
      return "uint8_t";
    }

    if (trimmedType.contains("int8_t")) {
      return "int8_t";
    }

    if (trimmedType.contains("uint16_t")) {
      return "uint16_t";
    }

    if (trimmedType.contains("int16_t")) {
      return "int16_t";
    }

    if (trimmedType.contains("uint32_t")) {
      return "uint32_t";
    }

    if (trimmedType.contains("int32_t")) {
      return "int32_t";
    }

    if (trimmedType.contains("uint64_t")) {
      return "uint64_t";
    }

    if (trimmedType.contains("int64_t")) {
      return "int64_t";
    }

    if (trimmedType.equals("float")) {
      return "float";
    }

    if (trimmedType.equals("double")) {
      return "double";
    }

    if (trimmedType.equals("char")) {
      return "char";
    }

    return trimmedType;
  }

  protected static long perFieldSeed(
      long baseSeed,
      int messageId,
      int fieldIndex
  ) {
    long seed = baseSeed;
    seed ^= (long) messageId * 0x9E37_79B9_7F4A_7C15L;
    seed ^= (long) fieldIndex * 0xC2B2_AE3D_27D4_EB4FL;

    return seed;
  }

  protected static long nextUnsignedIntValue(Random random) {
    return Integer.toUnsignedLong(random.nextInt());
  }

  protected static long nextPositiveLongValue(Random random) {
    return random.nextLong() & Long.MAX_VALUE;
  }

  protected static float nextFloatBounded(
      Random random,
      float minimum,
      float maximum
  ) {
    return minimum + random.nextFloat() * (maximum - minimum);
  }

  protected static double nextDoubleBounded(
      Random random,
      double minimum,
      double maximum
  ) {
    return minimum + random.nextDouble() * (maximum - minimum);
  }
}