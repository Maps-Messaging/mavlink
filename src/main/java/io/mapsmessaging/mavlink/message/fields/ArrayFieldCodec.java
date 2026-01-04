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
import java.util.List;

public class ArrayFieldCodec extends AbstractMavlinkFieldCodec {

  private final AbstractMavlinkFieldCodec elementCodec;
  private final int arrayLength;
  private final boolean treatAsString;

  public ArrayFieldCodec(AbstractMavlinkFieldCodec elementCodec, int arrayLength, boolean treatAsString) {
    super(elementCodec.getWireType());
    this.elementCodec = elementCodec;
    this.arrayLength = arrayLength;
    this.treatAsString = treatAsString;
  }

  @Override
  public int getSizeInBytes() {
    return elementCodec.getSizeInBytes() * arrayLength;
  }

  @Override
  public Object decode(ByteBuffer buffer) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    if (treatAsString && getWireType() == MavlinkWireType.CHAR) {
      byte[] temp = new byte[arrayLength];
      buffer.get(temp);
      int end = 0;
      while (end < temp.length && temp[end] != 0) {
        end++;
      }
      return new String(temp, 0, end);
    }

    return elementCodec.decode(buffer);
  }

  @Override
  public void encode(ByteBuffer buffer, Object value) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    if (treatAsString && getWireType() == MavlinkWireType.CHAR) {
      String text = value == null ? "" : value.toString();
      byte[] bytes = text.getBytes();
      int length = Math.min(bytes.length, arrayLength);
      buffer.put(bytes, 0, length);
      for (int index = length; index < arrayLength; index++) {
        buffer.put((byte) 0);
      }
      return;
    }

    if (!(value instanceof List<?>)) {
      throw new IllegalArgumentException("Expected List for array field, got " + value);
    }

    @SuppressWarnings("unchecked")
    List<Object> values = (List<Object>) value;
    int limit = Math.min(values.size(), arrayLength);

    for (int index = 0; index < limit; index++) {
      elementCodec.encode(buffer, values.get(index));
    }
    for (int index = limit; index < arrayLength; index++) {
      elementCodec.encode(buffer, 0);
    }
  }
}
