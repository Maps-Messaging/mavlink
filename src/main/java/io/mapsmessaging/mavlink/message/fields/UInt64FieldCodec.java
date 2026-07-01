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

package io.mapsmessaging.mavlink.message.fields;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UInt64FieldCodec extends AbstractMavlinkFieldCodec {

  private static final BigInteger MAX_UINT64 = new BigInteger("18446744073709551615");

  public UInt64FieldCodec() {
    super(WireType.UINT64);
  }

  @Override
  public Object decode(ByteBuffer buffer) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    return new BigInteger(Long.toUnsignedString(buffer.getLong()));
  }

  @Override
  public void encode(ByteBuffer buffer, Object value) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putLong(toUnsignedLong(value));
  }

  private long toUnsignedLong(Object value) {
    BigInteger bigIntegerValue;

    if (value instanceof BigInteger bigInteger) {
      bigIntegerValue = bigInteger;
    } else if (value instanceof Number number) {
      bigIntegerValue = BigInteger.valueOf(number.longValue());
    } else if (value instanceof String stringValue) {
      try {
        bigIntegerValue = new BigInteger(stringValue);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("Unable to encode UINT64 field from value: " + stringValue, exception);
      }
    } else {
      throw new IllegalArgumentException("Unable to encode UINT64 field from value type " + value.getClass().getName());
    }

    if (bigIntegerValue.signum() < 0 || bigIntegerValue.compareTo(MAX_UINT64) > 0) {
      throw new IllegalArgumentException("UINT64 value out of range: " + bigIntegerValue);
    }

    return bigIntegerValue.longValue();
  }
}