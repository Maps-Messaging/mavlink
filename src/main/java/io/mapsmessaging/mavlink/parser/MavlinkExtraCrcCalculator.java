package io.mapsmessaging.mavlink.parser;

import io.mapsmessaging.mavlink.message.MavlinkMessageDefinition;
import io.mapsmessaging.mavlink.message.X25Crc;
import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MavlinkExtraCrcCalculator {

  private MavlinkExtraCrcCalculator() {
  }

  public static int computeExtraCrc(MavlinkMessageDefinition messageDefinition) {
    X25Crc crc = new X25Crc();
    String messageName = messageDefinition.getName();
    List<MavlinkFieldDefinition> wireOrderedFields = messageDefinition.getFields();
    accumulateTokenWithSpace(crc, messageName);

    for (MavlinkFieldDefinition field : wireOrderedFields) {
      if (field.isExtension()) {
        continue;
      }

      accumulateTokenWithSpace(crc, field.getType());
      accumulateTokenWithSpace(crc, field.getName());

      if (field.isArray()) {
        crc.update(field.getArrayLength() & 0xFF);
      }
    }

    int crc16 = crc.getCrc() & 0xFFFF;
    return ((crc16 & 0xFF) ^ (crc16 >>> 8)) & 0xFF;
  }

  private static void accumulateTokenWithSpace(X25Crc crc, String token) {
    if (token == null) {
      return;
    }

    byte[] bytes = token.getBytes(StandardCharsets.US_ASCII);
    for (byte value : bytes) {
      crc.update(value);
    }
    crc.update((byte) ' ');
  }
}
