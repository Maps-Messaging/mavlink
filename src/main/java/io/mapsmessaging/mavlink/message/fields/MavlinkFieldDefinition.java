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


import lombok.Data;

@Data
public class MavlinkFieldDefinition {
  private int index;
  private String type;          // XML base type ("uint8_t", "char")
  private String name;
  private String units;
  private String description;
  private String enumName;

  private MavlinkWireType wireType;

  private boolean extension;
  private boolean array;
  private int arrayLength;      // 0 for scalars; >0 for arrays

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("\tField[")
        .append(index)
        .append("] ")
        .append(type);

    if (array) {
      builder.append("[").append(arrayLength).append("]");
    }

    builder.append(" ").append(name);

    if (units != null && !units.isEmpty()) {
      builder.append(" (units=").append(units).append(")");
    }
    if (enumName != null && !enumName.isEmpty()) {
      builder.append(" <enum=").append(enumName).append(">");
    }
    if (description != null && !description.isEmpty()) {
      builder.append(" : ").append(description);
    }

    return builder.toString();
  }
}
