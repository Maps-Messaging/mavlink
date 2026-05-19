package io.mapsmessaging.mavlink.message.fields;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CharFieldCodec extends AbstractMavlinkFieldCodec {

  public CharFieldCodec() {
    super(WireType.CHAR);
  }

  @Override
  public Object decode(ByteBuffer buffer) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    return buffer.get() & 0xFF;
  }

  @Override
  public void encode(ByteBuffer buffer, Object value) {
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    int characterValue;

    if (value instanceof Number number) {
      characterValue = number.intValue();
    } else if (value instanceof Character character) {
      characterValue = character;
    } else {
      String text = value == null ? "" : value.toString();
      characterValue = text.isEmpty() ? 0 : text.charAt(0);
    }

    buffer.put((byte) (characterValue & 0xFF));
  }
}