package io.mapsmessaging.mavlink.message.fields;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldCodecBoundaryTest {

  @Test
  void signedAndUnsignedScalarCodecsAcceptNumbersAndStrings() {
    List<AbstractMavlinkFieldCodec> signed = List.of(
        new Int8FieldCodec(), new Int16FieldCodec(), new Int32FieldCodec(), new Int64FieldCodec());
    List<AbstractMavlinkFieldCodec> unsigned = List.of(
        new UInt8FieldCodec(), new UInt16FieldCodec(), new UInt32FieldCodec(), new UInt64FieldCodec());

    for (AbstractMavlinkFieldCodec codec : signed) {
      assertEquals(-7, ((Number) roundTrip(codec, "-7")).intValue());
      assertEquals(7, ((Number) roundTrip(codec, 7)).intValue());
      assertThrows(IllegalArgumentException.class, () -> roundTrip(codec, "invalid"));
      assertThrows(IllegalArgumentException.class, () -> roundTrip(codec, new Object()));
    }
    for (AbstractMavlinkFieldCodec codec : unsigned) {
      assertEquals(7, ((Number) roundTrip(codec, "7")).intValue());
      assertEquals(9, ((Number) roundTrip(codec, 9)).intValue());
      assertThrows(IllegalArgumentException.class, () -> roundTrip(codec, "invalid"));
      assertThrows(IllegalArgumentException.class, () -> roundTrip(codec, "-1"));
      assertThrows(IllegalArgumentException.class, () -> roundTrip(codec, new Object()));
    }
    assertEquals(new BigInteger("18446744073709551615"),
        roundTrip(new UInt64FieldCodec(), "18446744073709551615"));
    assertEquals(4294967295L, ((Number) roundTrip(new UInt32FieldCodec(), "4294967295")).longValue());
  }

  @Test
  void floatingPointCodecsSupportSpecialValuesAndInvalidTypes() {
    for (AbstractMavlinkFieldCodec codec : List.of(new FloatFieldCodec(), new DoubleFieldCodec())) {
      assertTrue(Double.isNaN(((Number) roundTrip(codec, "NaN")).doubleValue()));
      assertEquals(Double.POSITIVE_INFINITY, ((Number) roundTrip(codec, "Infinity")).doubleValue());
      assertEquals(Double.NEGATIVE_INFINITY, ((Number) roundTrip(codec, "-Infinity")).doubleValue());
      assertEquals(1.5, ((Number) roundTrip(codec, "1.5")).doubleValue());
      assertEquals(2.0, ((Number) roundTrip(codec, 2)).doubleValue());
      assertThrows(IllegalArgumentException.class, () -> roundTrip(codec, new Object()));
    }
    assertThrows(IllegalArgumentException.class, () -> roundTrip(new DoubleFieldCodec(), "invalid"));
  }

  @Test
  void charAndArrayCodecsRoundTripStringAndPadNumericArrays() {
    assertEquals((int) 'A', roundTrip(new CharFieldCodec(), 'A'));
    assertEquals((int) 'B', roundTrip(new CharFieldCodec(), "BC"));
    assertEquals(0, roundTrip(new CharFieldCodec(), null));
    ArrayFieldCodec text = new ArrayFieldCodec(new CharFieldCodec(), 5, true);
    assertEquals("abc", roundTrip(text, "abc"));
    assertEquals("abcde", roundTrip(text, "abcdef"));
    assertEquals("", roundTrip(text, null));

    ArrayFieldCodec numbers = new ArrayFieldCodec(new UInt8FieldCodec(), 3, false);
    ByteBuffer output = ByteBuffer.allocate(numbers.getSizeInBytes());
    numbers.encode(output, List.of(1, 2));
    assertArrayEquals(new byte[]{1, 2, 0}, output.array());
    assertThrows(IllegalArgumentException.class, () -> numbers.encode(ByteBuffer.allocate(3), "wrong"));
  }

  private static Object roundTrip(AbstractMavlinkFieldCodec codec, Object value) {
    ByteBuffer buffer = ByteBuffer.allocate(codec.getSizeInBytes());
    codec.encode(buffer, value);
    buffer.flip();
    return codec.decode(buffer);
  }
}
