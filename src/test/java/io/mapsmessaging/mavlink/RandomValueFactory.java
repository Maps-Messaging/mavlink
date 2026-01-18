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

  protected RandomValueFactory() {}

  public static Map<String, Object> buildValues(
      MessageRegistry registry,
      CompiledMessage msg,
      MavlinkRoundTripAllMessagesTest.ExtensionMode extensionMode,
      long baseSeed
  ) throws IOException {

    Map<String, Object> values = new LinkedHashMap<>();
    List<CompiledField> fields = msg.getCompiledFields();

    for (int i = 0; i < fields.size(); i++) {
      CompiledField cf = fields.get(i);
      FieldDefinition fd = MavlinkTestSupport.fieldDefinition(cf);

      if (fd.isExtension() && extensionMode == MavlinkRoundTripAllMessagesTest.ExtensionMode.OMIT_ALL) {
        continue;
      }

      if (fd.isExtension() && extensionMode == MavlinkRoundTripAllMessagesTest.ExtensionMode.SOME_PRESENT) {
        // include some extensions deterministically (not all)
        long seed = perFieldSeed(baseSeed, msg.getMessageId(), i);
        Random random = new Random(seed);
        if (random.nextInt(100) < 60) {
          continue; // omit ~60% of extension fields
        }
      }

      Object value = generateValueForField(registry, msg.getMessageId(), fd, i, baseSeed);
      values.put(fd.getName(), value);
    }

    return values;
  }

  static FieldDefinition fieldByName(CompiledMessage msg, String fieldName) {
    for (CompiledField cf : msg.getCompiledFields()) {
      FieldDefinition fd = MavlinkTestSupport.fieldDefinition(cf);
      if (fieldName.equals(fd.getName())) {
        return fd;
      }
    }
    throw new IllegalStateException("Unknown field '" + fieldName + "' in message " + msg.getMessageId());
  }

  protected static Object generateValueForField(
      MessageRegistry registry,
      int messageId,
      FieldDefinition fd,
      int fieldIndex,
      long baseSeed
  ) throws IOException {

    long seed = perFieldSeed(baseSeed, messageId, fieldIndex);
    Random random = new Random(seed);

    if (fd.isArray()) {
      if (isCharType(fd)) {
        return randomAsciiString(fd.getArrayLength(), messageId, fd.getName(), random);
      }
      Object[] arr = new Object[fd.getArrayLength()];
      for (int i = 0; i < arr.length; i++) {
        arr[i] = generateScalar(registry, fd, random);
      }
      return arr;
    }

    return generateScalar(registry, fd, random);
  }

  protected static Object generateScalar(MessageRegistry registry, FieldDefinition fd, Random random) throws IOException {
    String enumName = fd.getEnumName();
    if (enumName != null && !enumName.isEmpty()) {
      EnumDefinition def = registry.getEnumsByName().get(enumName);
      if (def != null && def.getEntries() != null && !def.getEntries().isEmpty()) {
        if (def.isBitmask()) {
          return pickBitmask(def, random);
        }
        return pickEnumValue(def, random);
      }
      // If enum metadata missing, fall back to numeric in-range.
    }

    String type = normalizeType(fd.getType());

    return switch (type) {
      case "uint8_t" -> Integer.valueOf(random.nextInt(256));
      case "int8_t" -> Integer.valueOf(random.nextInt(256) - 128);

      case "uint16_t" -> Integer.valueOf(random.nextInt(65536));
      case "int16_t" -> Integer.valueOf(random.nextInt(65536) - 32768);

      case "uint32_t" -> Long.valueOf(nextLongBounded(random, 0L, 0xFFFF_FFFFL));
      case "int32_t" -> Integer.valueOf(random.nextInt());

      case "uint64_t" -> Long.valueOf(nextLongBounded(random, 0L, Long.MAX_VALUE));
      case "int64_t" -> Long.valueOf(random.nextLong());

      case "float" -> Float.valueOf(nextFloatBounded(random, -10_000f, 10_000f));
      case "double" -> Double.valueOf(nextDoubleBounded(random, -10_000d, 10_000d));

      // Some dialects use "char" for scalar, though rare.
      case "char" -> Integer.valueOf(random.nextInt(128));

      default -> throw new IOException("Unsupported MAVLink field type: " + fd.getType() + " (normalized=" + type + ")");
    };
  }

  protected static long pickEnumValue(EnumDefinition def, Random random) {
    List<EnumEntry> entries = def.getEntries();
    EnumEntry entry = entries.get(random.nextInt(entries.size()));
    return entry.getValue();
  }

  protected static long pickBitmask(EnumDefinition def, Random random) {
    List<EnumEntry> entries = def.getEntries();
    int count = Math.min(entries.size(), 1 + random.nextInt(3));
    long mask = 0L;

    for (int i = 0; i < count; i++) {
      EnumEntry entry = entries.get(random.nextInt(entries.size()));
      mask |= entry.getValue();
    }
    return mask;
  }

  protected static boolean isCharType(FieldDefinition fd) {
    String type = normalizeType(fd.getType());
    return "char".equals(type);
  }

  protected static String randomAsciiString(int maxLen, int messageId, String fieldName, Random random) {
    // Stable-ish prefix helps debugging.
    String prefix = "M" + messageId + "_" + fieldName + "_";
    byte[] bytes = new byte[Math.max(1, maxLen)];
    byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);

    int pos = 0;
    for (int i = 0; i < prefixBytes.length && pos < bytes.length; i++) {
      bytes[pos++] = prefixBytes[i];
    }
    while (pos < bytes.length) {
      int c = 32 + random.nextInt(95); // printable ASCII 32..126
      bytes[pos++] = (byte) c;
    }

    // Return String to match your packer expectation for char arrays.
    // If you prefer byte[], swap this line.
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  protected static String normalizeType(String type) {
    if (type == null) {
      return "";
    }
    // Your pipeline already normalizes decorated types; keep this defensive.
    String t = type.trim();
    if (t.endsWith("_t")) {
      return t;
    }
    if (t.contains("uint8_t")) return "uint8_t";
    if (t.contains("int8_t")) return "int8_t";
    if (t.contains("uint16_t")) return "uint16_t";
    if (t.contains("int16_t")) return "int16_t";
    if (t.contains("uint32_t")) return "uint32_t";
    if (t.contains("int32_t")) return "int32_t";
    if (t.contains("uint64_t")) return "uint64_t";
    if (t.contains("int64_t")) return "int64_t";
    if (t.equals("float")) return "float";
    if (t.equals("double")) return "double";
    if (t.equals("char")) return "char";
    return t;
  }

  protected static long perFieldSeed(long baseSeed, int messageId, int fieldIndex) {
    long s = baseSeed;
    s ^= (long) messageId * 0x9E37_79B9_7F4A_7C15L;
    s ^= (long) fieldIndex * 0xC2B2_AE3D_27D4_EB4FL;
    return s;
  }

  protected static long nextLongBounded(Random random, long minInclusive, long maxInclusive) {
    if (minInclusive > maxInclusive) {
      throw new IllegalArgumentException("min > max");
    }
    long bound = maxInclusive - minInclusive + 1;
    long r = random.nextLong();
    long v = Math.floorMod(r, bound);
    return minInclusive + v;
  }


  protected static float nextFloatBounded(Random random, float min, float max) {
    return min + random.nextFloat() * (max - min);
  }

  protected static double nextDoubleBounded(Random random, double min, double max) {
    return min + random.nextDouble() * (max - min);
  }
}