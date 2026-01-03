package io.mapsmessaging.mavlink.framing;

import io.mapsmessaging.mavlink.message.X25Crc;

import java.nio.ByteBuffer;

public class CrcHelper {

  public static int computeChecksumFromWritten(ByteBuffer out, int index, int length, int crcExtra) {
    X25Crc crc = new X25Crc();
    int endIndex = index + length;

    for (int currentIndex = index; currentIndex < endIndex; currentIndex++) {
      crc.update(out.get(currentIndex));
    }
    crc.update(crcExtra);
    return crc.getCrc() & 0xFFFF;
  }

  private CrcHelper(){}

}
