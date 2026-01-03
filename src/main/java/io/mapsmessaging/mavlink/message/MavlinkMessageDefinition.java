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

import io.mapsmessaging.mavlink.message.fields.MavlinkFieldDefinition;
import io.mapsmessaging.mavlink.message.fields.MavlinkWireType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
public class MavlinkMessageDefinition {

  private int messageId;
  private String name;
  private String description;

  /**
   * Wire-order, immutable after setXmlOrderedFields().
   */
  private List<MavlinkFieldDefinition> fields = List.of();

  private int extraCrc;

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("MavlinkMessageDefinition [messageId=")
        .append(messageId)
        .append(", name=")
        .append(name)
        .append(", description=")
        .append(description)
        .append("\n");
    for (MavlinkFieldDefinition field : fields) {
      builder.append(field.toString()).append("\n");
    }
    return builder.toString();
  }

  public void setXmlOrderedFields(List<MavlinkFieldDefinition> xmlOrdered) {
    if (xmlOrdered == null || xmlOrdered.isEmpty()) {
      fields = List.of();
      computeExtraCrc();
      return;
    }

    List<MavlinkFieldDefinition> ordered = orderForWire(xmlOrdered);
    fields = List.copyOf(ordered); // locked
    computeExtraCrc();
  }

  private List<MavlinkFieldDefinition> orderForWire(List<MavlinkFieldDefinition> xmlOrdered) {
    List<MavlinkFieldDefinition> base = new ArrayList<>();
    List<MavlinkFieldDefinition> extensions = new ArrayList<>();

    for (MavlinkFieldDefinition field : xmlOrdered) {
      if (field.isExtension()) {
        extensions.add(field);
      } else {
        base.add(field);
      }
    }

    Comparator<MavlinkFieldDefinition> comparator = Comparator
        .comparingInt((MavlinkFieldDefinition f) -> f.getWireType().getSizeInBytes()).reversed()
        .thenComparingInt(MavlinkFieldDefinition::getIndex); // xml order tie-breaker

    base.sort(comparator);
    extensions.sort(comparator);

    List<MavlinkFieldDefinition> ordered = new ArrayList<>(base.size() + extensions.size());
    ordered.addAll(base);
    ordered.addAll(extensions);

    return ordered;
  }

  private void computeExtraCrc() {
    X25Crc crc = new X25Crc();
    crc.reset();

    crcCharArray(crc, getName().toCharArray());

    for (MavlinkFieldDefinition field : fields) {
      if (field.isExtension()) {
        continue;
      }

      MavlinkWireType fieldType = MavlinkWireType.fromXmlType(field.getType());
      crcCharArray(crc, fieldType.getWireName().toCharArray());
      crcCharArray(crc, field.getName().toCharArray());
      if (field.isArray()) {
        crc.update(field.getArrayLength() & 0xFF);
      }
    }

    int val = crc.getRawCrc();
    extraCrc = ((val & 0xFF) ^ ((val >> 8) & 0xFF)) & 0xFF;
  }

  private void crcCharArray(X25Crc crc, char[] charArray) {
    for (char c : charArray) {
      crc.update((byte) c);
    }
    crc.update(' ');
  }
}
