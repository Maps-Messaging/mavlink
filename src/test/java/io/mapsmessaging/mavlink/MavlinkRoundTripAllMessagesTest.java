package io.mapsmessaging.mavlink;

import io.mapsmessaging.mavlink.message.MavlinkCompiledField;
import io.mapsmessaging.mavlink.message.MavlinkCompiledMessage;
import io.mapsmessaging.mavlink.message.MavlinkMessageRegistry;
import io.mapsmessaging.mavlink.message.fields.MavlinkEnumDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkEnumEntry;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MavlinkRoundTripAllMessagesTest {

  private static final long BASE_SEED = 0xC0FFEE_1234ABCDL;

  @TestFactory
  Stream<DynamicTest> allMessages_baseFields_roundTrip() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MavlinkMessageRegistry registry = codec.getRegistry();

    return registry.getCompiledMessages().stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName(),
            () -> roundTripForMessage(codec, registry, msg, ExtensionMode.OMIT_ALL)
        ));
  }

  @TestFactory
  Stream<DynamicTest> allMessages_extensions_omitted_vs_present_payloadSizing() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MavlinkMessageRegistry registry = codec.getRegistry();

    return registry.getCompiledMessages().stream()
        .filter(msg -> msg.getCompiledFields().stream().anyMatch(cf -> MavlinkTestSupport.fieldDefinition(cf).isExtension()))
        .map(msg -> DynamicTest.dynamicTest(
            msg.getMessageId() + " " + msg.getName(),
            () -> extensionSizingForMessage(codec, registry, msg)
        ));
  }

  @Test
  void parallel_encode_decode_sanity_no_shared_state() throws Exception {
    MavlinkCodec codec = MavlinkTestSupport.codec();
    MavlinkMessageRegistry registry = codec.getRegistry();

    List<MavlinkCompiledMessage> messages = registry.getCompiledMessages();
    assertFalse(messages.isEmpty());

    int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    ExecutorService executor = Executors.newFixedThreadPool(threads);

    try {
      int tasks = Math.min(200, messages.size() * 2);
      List<Callable<Void>> callables = new ArrayList<>(tasks);

      for (int i = 0; i < tasks; i++) {
        MavlinkCompiledMessage msg = messages.get(i % messages.size());
        int messageId = msg.getMessageId();

        callables.add(() -> {
          roundTripForMessage(codec, registry, msg, ExtensionMode.SOME_PRESENT);
          return null;
        });
      }

      List<Future<Void>> futures = executor.invokeAll(callables, 30, TimeUnit.SECONDS);
      for (Future<Void> f : futures) {
        f.get(); // propagate failures
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static void roundTripForMessage(
      MavlinkCodec codec,
      MavlinkMessageRegistry registry,
      MavlinkCompiledMessage msg,
      ExtensionMode extensionMode
  ) throws Exception {

    Map<String, Object> input = RandomValueFactory.buildValues(registry, msg, extensionMode, BASE_SEED);
    byte[] payload = codec.encodePayload(msg.getMessageId(), input);
    assertNotNull(payload);
    assertTrue(payload.length > 0, "payload length must be > 0");

    Map<String, Object> output = codec.parsePayload(msg.getMessageId(), payload);
    assertNotNull(output);

    // Compare only the fields we provided.
    // (If you want to enforce “missing required becomes 0”, add a separate test.)
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      String fieldName = entry.getKey();
      Object expected = entry.getValue();
      Object actual = output.get(fieldName);

      // Parser may omit trailing extension fields if payload shorter.
      if (actual == null) {
        // If we set it, it should exist after decode.
        fail("Missing field after decode for message " + msg.getMessageId() + " (" + msg.getName() + "): " + fieldName);
      }

      MavlinkFieldDefinition fd = RandomValueFactory.fieldByName(msg, fieldName);
      ValueAssertions.assertFieldEquals(fd, expected, actual, msg);
    }
  }

  private static void extensionSizingForMessage(
      MavlinkCodec codec,
      MavlinkMessageRegistry registry,
      MavlinkCompiledMessage msg
  ) throws Exception {

    int baseSize = PayloadSizing.computeBaseSize(msg);

    Map<String, Object> baseOnly = RandomValueFactory.buildValues(registry, msg, ExtensionMode.OMIT_ALL, BASE_SEED);
    byte[] basePayload = codec.encodePayload(msg.getMessageId(), baseOnly);
    assertEquals(baseSize, basePayload.length,
        "Base payload size mismatch for message " + msg.getMessageId() + " (" + msg.getName() + ")");

    Map<String, Object> withExt = RandomValueFactory.buildValues(registry, msg, ExtensionMode.SOME_PRESENT, BASE_SEED);
    byte[] extPayload = codec.encodePayload(msg.getMessageId(), withExt);
    assertTrue(extPayload.length >= baseSize,
        "Extension payload should be >= base size for message " + msg.getMessageId() + " (" + msg.getName() + ")");

    // Decode should succeed and base fields should remain stable.
    Map<String, Object> decoded = codec.parsePayload(msg.getMessageId(), extPayload);
    for (Map.Entry<String, Object> entry : baseOnly.entrySet()) {
      String name = entry.getKey();
      Object expected = entry.getValue();
      Object actual = decoded.get(name);
      assertNotNull(actual, "Missing base field after extension decode: " + name);

      MavlinkFieldDefinition fd = RandomValueFactory.fieldByName(msg, name);
      ValueAssertions.assertFieldEquals(fd, expected, actual, msg);
    }
  }

  enum ExtensionMode {
    OMIT_ALL,
    SOME_PRESENT
  }

  static final class PayloadSizing {

    private PayloadSizing() {}

    static int computeBaseSize(MavlinkCompiledMessage msg) {
      int size = 0;
      for (MavlinkCompiledField cf : msg.getCompiledFields()) {
        MavlinkFieldDefinition fd = MavlinkTestSupport.fieldDefinition(cf);
        if (fd.isExtension()) {
          break;
        }
        size += MavlinkTestSupport.size(cf);
      }
      return size;
    }
  }

  static final class RandomValueFactory {

    private RandomValueFactory() {}

    static Map<String, Object> buildValues(
        MavlinkMessageRegistry registry,
        MavlinkCompiledMessage msg,
        ExtensionMode extensionMode,
        long baseSeed
    ) throws IOException {

      Map<String, Object> values = new LinkedHashMap<>();
      List<MavlinkCompiledField> fields = msg.getCompiledFields();

      for (int i = 0; i < fields.size(); i++) {
        MavlinkCompiledField cf = fields.get(i);
        MavlinkFieldDefinition fd = MavlinkTestSupport.fieldDefinition(cf);

        if (fd.isExtension() && extensionMode == ExtensionMode.OMIT_ALL) {
          continue;
        }

        if (fd.isExtension() && extensionMode == ExtensionMode.SOME_PRESENT) {
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

    static MavlinkFieldDefinition fieldByName(MavlinkCompiledMessage msg, String fieldName) {
      for (MavlinkCompiledField cf : msg.getCompiledFields()) {
        MavlinkFieldDefinition fd = MavlinkTestSupport.fieldDefinition(cf);
        if (fieldName.equals(fd.getName())) {
          return fd;
        }
      }
      throw new IllegalStateException("Unknown field '" + fieldName + "' in message " + msg.getMessageId());
    }

    private static Object generateValueForField(
        MavlinkMessageRegistry registry,
        int messageId,
        MavlinkFieldDefinition fd,
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

    private static Object generateScalar(MavlinkMessageRegistry registry, MavlinkFieldDefinition fd, Random random) throws IOException {
      String enumName = fd.getEnumName();
      if (enumName != null && !enumName.isEmpty()) {
        MavlinkEnumDefinition def = registry.getEnumsByName().get(enumName);
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

    private static long pickEnumValue(MavlinkEnumDefinition def, Random random) {
      List<MavlinkEnumEntry> entries = def.getEntries();
      MavlinkEnumEntry entry = entries.get(random.nextInt(entries.size()));
      return entry.getValue();
    }

    private static long pickBitmask(MavlinkEnumDefinition def, Random random) {
      List<MavlinkEnumEntry> entries = def.getEntries();
      int count = Math.min(entries.size(), 1 + random.nextInt(3));
      long mask = 0L;

      for (int i = 0; i < count; i++) {
        MavlinkEnumEntry entry = entries.get(random.nextInt(entries.size()));
        mask |= entry.getValue();
      }
      return mask;
    }

    private static boolean isCharType(MavlinkFieldDefinition fd) {
      String type = normalizeType(fd.getType());
      return "char".equals(type);
    }

    private static String randomAsciiString(int maxLen, int messageId, String fieldName, Random random) {
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

    private static String normalizeType(String type) {
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

    private static long perFieldSeed(long baseSeed, int messageId, int fieldIndex) {
      long s = baseSeed;
      s ^= (long) messageId * 0x9E37_79B9_7F4A_7C15L;
      s ^= (long) fieldIndex * 0xC2B2_AE3D_27D4_EB4FL;
      return s;
    }

    private static long nextLongBounded(Random random, long minInclusive, long maxInclusive) {
      if (minInclusive > maxInclusive) {
        throw new IllegalArgumentException("min > max");
      }
      long bound = maxInclusive - minInclusive + 1;
      long r = random.nextLong();
      long v = Math.floorMod(r, bound);
      return minInclusive + v;
    }


    private static float nextFloatBounded(Random random, float min, float max) {
      return min + random.nextFloat() * (max - min);
    }

    private static double nextDoubleBounded(Random random, double min, double max) {
      return min + random.nextDouble() * (max - min);
    }
  }

  static final class ValueAssertions {

    private ValueAssertions() {}

    static void assertFieldEquals(
        MavlinkFieldDefinition fd,
        Object expected,
        Object actual,
        MavlinkCompiledMessage msg
    ) {

      String type = RandomValueFactory.normalizeType(fd.getType());

      if (fd.isArray()) {
        if ("char".equals(type)) {
          String expectedStr = normalizeCharExpected(expected);
          String actualStr = normalizeCharActual(actual);
          assertEquals(expectedStr, actualStr,
              "Char array mismatch for message " + msg.getMessageId() + " (" + msg.getName() + ") field=" + fd.getName());
          return;
        }

        List<Object> exp = toObjectList(expected);
        List<Object> act = toObjectList(actual);

        assertEquals(exp.size(), act.size(),
            "Array length mismatch for message " + msg.getMessageId() + " (" + msg.getName() + ") field=" + fd.getName());

        for (int i = 0; i < exp.size(); i++) {
          assertNumericEquals(type, exp.get(i), act.get(i),
              "Array element mismatch idx=" + i + " for message " + msg.getMessageId() + " field=" + fd.getName());
        }
        return;
      }

      if ("float".equals(type)) {
        float e = ((Number) expected).floatValue();
        float a = ((Number) actual).floatValue();
        assertEquals(e, a, 1e-4f, "Float mismatch for message " + msg.getMessageId() + " field=" + fd.getName());
        return;
      }

      if ("double".equals(type)) {
        double e = ((Number) expected).doubleValue();
        double a = ((Number) actual).doubleValue();
        assertEquals(e, a, 1e-9d, "Double mismatch for message " + msg.getMessageId() + " field="+ fd.getName());
        return;
      }

      assertNumericEquals(type, expected, actual,
          "Scalar mismatch for message " + msg.getMessageId() + " (" + msg.getName() + ") field=" + fd.getName());
    }

    private static void assertNumericEquals(String type, Object expected, Object actual, String message) {
      // Compare numerics by widened type. Unsigned are generated within range already.
      if (!(expected instanceof Number) || !(actual instanceof Number)) {
        fail(message + " expected/actual not numeric: expected=" + expected + " actual=" + actual);
      }

      long e = ((Number) expected).longValue();
      long a = ((Number) actual).longValue();

      assertEquals(e, a, message + " type=" + type);
    }

    private static List<Object> toObjectList(Object value) {
      if (value == null) {
        return List.of();
      }
      if (value instanceof List<?> list) {
        return new ArrayList<>(list);
      }
      if (value instanceof Object[] arr) {
        return Arrays.asList(arr);
      }
      if (value instanceof byte[] bytes) {
        List<Object> out = new ArrayList<>(bytes.length);
        for (byte b : bytes) {
          out.add((int) (b & 0xFF));
        }
        return out;
      }
      Class<?> c = value.getClass();
      if (c.isArray()) {
        int len = Array.getLength(value);
        List<Object> out = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
          out.add(Array.get(value, i));
        }
        return out;
      }
      return List.of(value);
    }

    private static String normalizeCharExpected(Object expected) {
      if (expected == null) {
        return "";
      }
      if (expected instanceof String s) {
        return trimAtNull(s);
      }
      if (expected instanceof byte[] b) {
        return trimAtNull(new String(b, StandardCharsets.US_ASCII));
      }
      return trimAtNull(String.valueOf(expected));
    }

    private static String normalizeCharActual(Object actual) {
      if (actual == null) {
        return "";
      }
      if (actual instanceof String s) {
        return trimAtNull(s);
      }
      if (actual instanceof byte[] b) {
        return trimAtNull(new String(b, StandardCharsets.US_ASCII));
      }
      if (actual instanceof Object[] arr) {
        byte[] bytes = new byte[arr.length];
        for (int i = 0; i < arr.length; i++) {
          Object o = arr[i];
          bytes[i] = (byte) (((Number) o).intValue() & 0xFF);
        }
        return trimAtNull(new String(bytes, StandardCharsets.US_ASCII));
      }
      return trimAtNull(String.valueOf(actual));
    }

    private static String trimAtNull(String s) {
      int idx = s.indexOf('\0');
      if (idx >= 0) {
        return s.substring(0, idx);
      }
      return s;
    }
  }
}
