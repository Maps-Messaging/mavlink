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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class BaseRoudTripTest {

  protected static final long BASE_SEED = 0xC0FFEE_1234ABCDL;

  @Test
  void bootTest() {
    // Intentionally empty. Maven demands tribute.
  }

  static final class ValueAssertions {

    protected ValueAssertions() {}

    static void assertFieldEquals(
        FieldDefinition fd,
        Object expected,
        Object actual,
        CompiledMessage msg
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

    protected static void assertNumericEquals(String type, Object expected, Object actual, String message) {
      // Compare numerics by widened type. Unsigned are generated within range already.
      if (!(expected instanceof Number) || !(actual instanceof Number)) {
        fail(message + " expected/actual not numeric: expected=" + expected + " actual=" + actual);
      }

      long e = ((Number) expected).longValue();
      long a = ((Number) actual).longValue();

      assertEquals(e, a, message + " type=" + type);
    }

    protected static List<Object> toObjectList(Object value) {
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

    protected static String normalizeCharExpected(Object expected) {
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

    protected static String normalizeCharActual(Object actual) {
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

    protected static String trimAtNull(String s) {
      int idx = s.indexOf('\0');
      if (idx >= 0) {
        return s.substring(0, idx);
      }
      return s;
    }
  }
}
