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

package io.mapsmessaging.mavlink.message;


import lombok.Data;

import java.util.List;

@Data
public class MavlinkCompiledMessage {

  private int messageId;
  private String name;
  private MavlinkMessageDefinition messageDefinition;
  private List<MavlinkCompiledField> compiledFields;
  private int payloadSizeBytes;
  private int minimumPayloadSizeBytes;

  public int getCrcExtra(){
    return messageDefinition.getExtraCrc();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("CompiledMessage[id=")
        .append(messageId)
        .append(", name=")
        .append(name)
        .append(", payloadSize=")
        .append(payloadSizeBytes)
        .append(", minPayloadSize=")
        .append(minimumPayloadSizeBytes)
        .append(", crcExtra=")
        .append(messageDefinition.getExtraCrc())
        .append("]\n");

    for (MavlinkCompiledField compiledField : compiledFields) {
      builder.append("  ").append(compiledField.toString()).append("\n");
    }
    return builder.toString();
  }
}
