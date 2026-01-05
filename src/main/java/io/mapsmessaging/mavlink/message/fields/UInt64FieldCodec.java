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

package io.mapsmessaging.mavlink.message.fields;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UInt64FieldCodec extends AbstractMavlinkFieldCodec {

  public UInt64FieldCodec() {
    super(MavlinkWireType.UINT64);
  }

  @Override
  public Object decode(ByteBuffer buffer) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    return buffer.getLong(); // assumes fits in signed long
  }

  @Override
  public void encode(ByteBuffer buffer, Object value) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putLong(((Number) value).longValue());
  }
}
