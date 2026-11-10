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
package io.mapsmessaging.mavlink.message.fields;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UInt32LongValidationTest {

  private final UInt32FieldCodec codec = new UInt32FieldCodec();

  @Test
  void uint32RoundTripsUsingLongStorage() {
    for (long number : List.of(0L, 1L, 2_147_483_647L, 2_147_483_648L, 4_294_967_295L)) {
      ByteBuffer output = ByteBuffer.allocate(4);
      codec.encode(output, number);
      output.flip();
      Object decoded = codec.decode(output);
      assertInstanceOf(Long.class, decoded);
      assertEquals(number, decoded);
    }
  }

  @Test
  void existingInputsRemainAccepted() {
    for (Object input : List.of(42, (short) 42, (byte) 42, "4294967295", BigInteger.valueOf(4_294_967_295L))) {
      ByteBuffer output = ByteBuffer.allocate(4);
      codec.encode(output, input);
      output.flip();
      long expected = input instanceof Number number ? number.longValue() : Long.parseLong((String) input);
      assertEquals(expected, codec.decode(output));
    }
    ByteBuffer signedBits = ByteBuffer.allocate(4);
    codec.encode(signedBits, -1);
    signedBits.flip();
    assertEquals(4_294_967_295L, codec.decode(signedBits));
  }

  @Test
  void rejectsOutOfRangeAndInvalidInputs() {
    for (Object bad : List.of(-1L, 4_294_967_296L, BigInteger.valueOf(-1),
        BigInteger.valueOf(4_294_967_296L), "4294967296", "not an integer")) {
      assertThrows(IllegalArgumentException.class, () -> codec.encode(ByteBuffer.allocate(4), bad), bad.toString());
    }
  }
}
