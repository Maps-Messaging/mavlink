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

package io.mapsmessaging.mavlink.parser;

import io.mapsmessaging.mavlink.message.MessageDefinition;
import io.mapsmessaging.mavlink.message.X25Crc;
import io.mapsmessaging.mavlink.message.fields.FieldDefinition;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ExtraCrcCalculator {

  private ExtraCrcCalculator() {
  }

  public static int computeExtraCrc(MessageDefinition messageDefinition) {
    X25Crc crc = new X25Crc();
    String messageName = messageDefinition.getName();
    List<FieldDefinition> wireOrderedFields = messageDefinition.getFields();
    accumulateTokenWithSpace(crc, messageName);

    for (FieldDefinition field : wireOrderedFields) {
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
