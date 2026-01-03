/*
 *
 *     Copyright [ 2020 - 2026 ] [Matthew Buckton]
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package io.mapsmessaging.mavlink.message.fields;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Int16FieldCodec extends AbstractMavlinkFieldCodec {

  public Int16FieldCodec() {
    super(MavlinkWireType.INT16);
  }

  @Override
  public Object decode(ByteBuffer buffer) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    return buffer.getShort();
  }

  @Override
  public void encode(ByteBuffer buffer, Object value) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort(((Number) value).shortValue());
  }
}
